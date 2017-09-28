/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remotes.pack;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.remotes.OpenRemote;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.RemoteListOp;
import org.locationtech.geogig.remotes.RemoteResolve;
import org.locationtech.geogig.remotes.TransferSummary;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Fetches named heads or tags from one or more other repositories, along with the objects necessary
 * to complete them.
 * <p>
 * remote.send-pack(pack-request) -> local.receive-pack(pack)
 */
public class FetchOp extends AbstractGeoGigOp<TransferSummary> {

    private FetchArgs.Builder argsBuilder = new FetchArgs.Builder();

    /**
     * Executes the fetch operation.
     * 
     * @return {@code null}
     * @see org.locationtech.geogig.repository.AbstractGeoGigOp#call()
     */
    @Override
    protected TransferSummary _call() {
        final Repository localRepo = repository();
        final FetchArgs args = argsBuilder.build(localRepo);

        ProgressListener progress = getProgressListener();
        progress.started();

        TransferSummary result = new TransferSummary();

        for (Remote remote : args.remotes) {
            try (IRemoteRepo remoteRepo = openRemote(remote)) {
                // get ref diffs in the remote's local namespace (i.e. refs/heads/<branch>)
                final List<RefDiff> refDiffs = diffRemoteRefs(remoteRepo, args.fetchTags);
                // prepare the request to fetch the missing RevObjects
                final List<RefDiff> symRefs = new ArrayList<>();
                final PackRequest request = prepareRequest(localRepo, remoteRepo, refDiffs,
                        symRefs);

                // tell the remote to send us the missing objects
                Iterable<RefDiff> localRemoteResults;
                localRemoteResults = remoteRepo.command(SendPackOp.class)//
                        .setRequest(request)//
                        .setTarget(localRepo)//
                        .setProgressListener(progress)//
                        .call();

                checkNotNull(localRemoteResults);

                Iterable<RefDiff> remoteRemoteRefs;

                // apply the ref diffs to our local remotes namespace and obtain the diffs in the
                // refs/remotes/... namespace
                localRemoteResults = concat(localRemoteResults, symRefs);
                remoteRemoteRefs = updateLocalRemoteRefs(remote, localRemoteResults);

                if (args.prune) {
                    // prune any local remote ref that's been deleted
                    Iterable<RefDiff> prunable = filter(refDiffs, (rd) -> rd.isDelete());
                    List<RefDiff> pruned = updateLocalRemoteRefs(remote, prunable);
                    remoteRemoteRefs = Iterables.concat(remoteRemoteRefs, pruned);
                }
                result.addAll(remote.getFetchURL(), Lists.newArrayList(remoteRemoteRefs));
            }
        }

        if (args.fullDepth) {
            // The full history was fetched, this is no longer a shallow clone
            command(ConfigOp.class)//
                    .setAction(ConfigAction.CONFIG_UNSET)//
                    .setScope(ConfigScope.LOCAL)//
                    .setName(Repository.DEPTH_CONFIG_KEY)//
                    .call();
        }

        progress.complete();

        return result;
    }

    private List<RefDiff> updateLocalRemoteRefs(Remote remote,
            Iterable<RefDiff> localRemoteResults) {
        List<RefDiff> remoteRemoteRefs;
        remoteRemoteRefs = command(UpdateRemoteRefOp.class)//
                .addAll(localRemoteResults)//
                .setRemote(remote)//
                .call();
        return remoteRemoteRefs;
    }

    /**
     * Runs {@link DiffRemoteRefsOp} returning a list of {@link RefDiff} where each
     * {@link RefDiff#getOldRef()} is the current value of the remote ref in the local repository
     * (as in the repository's {@code refs/remotes/<remote>} namespace), and each
     * {@link RefDiff#getNewRef())} is the current value of the ref returned by the remote.
     */
    private List<RefDiff> diffRemoteRefs(IRemoteRepo remote, boolean includeTags) {
        List<RefDiff> refdiffs;
        refdiffs = command(DiffRemoteRefsOp.class)//
                .setRemote(remote)//
                .setGetRemoteTags(includeTags)//
                .normalizeToLocalRefs()// use local namespaces to talk to the server
                .call();
        return refdiffs;
    }

    /**
     * Prepares a request to obtain all the missing {@link RevObject}s from the remote in order to
     * complement the local repository's object graph to contain the whole history of the refs in
     * the {@link RefDiff}s.
     * <p>
     * Since the {@link PackRequest} is only useful to retrieve missing contents, any
     * {@link RefDiff} that represents a {@link RefDiff#isDelete() deleted} ref on the remote is
     * filtered out.
     * 
     * @param local
     * @param remote
     * @param refdiffs
     * @param symRefsOut output parameter where to add {@link SymRef} for later update since they're
     *        {@link SymRef#peel() peeled} before being added to the {@link PackRequest}
     */
    private PackRequest prepareRequest(Repository local, IRemoteRepo remote, List<RefDiff> refdiffs,
            List<RefDiff> symRefsOut) {

        PackRequest request = new PackRequest();

        // filter out pruned remote refs
        Iterable<RefDiff> updatable = filter(refdiffs,
                (r) -> !r.isDelete() && !r.getNewRef().getObjectId().isNull());

        for (RefDiff cr : updatable) {
            RefRequest req;
            ObjectId haveTip = null;
            switch (cr.getType()) {
            case ADDED_REF:
                break;
            case CHANGED_REF:
                haveTip = cr.getOldRef().getObjectId();
                break;
            default:
                throw new IllegalStateException();
            }
            Ref want = cr.getNewRef();
            if (want instanceof SymRef) {
                symRefsOut.add(cr);
                want = ((SymRef) want).peel();
            }
            req = RefRequest.want(want, haveTip);
            request.addRef(req);
        }

        return request;
    }

    private IRemoteRepo openRemote(Remote remote) {
        return command(OpenRemote.class).setRemote(remote).readOnly().call();
    }

    public FetchOp omitTags() {
        argsBuilder.fetchTags = false;
        return this;
    }

    public FetchOp addRemotes(List<Remote> remotes) {
        remotes.forEach((r) -> addRemote(Suppliers.ofInstance(Optional.of(r))));
        return this;
    }

    /**
     * Immutable state of command arguments
     */
    private static class FetchArgs {

        /**
         * Builder for command arguments
         */
        private static class Builder {
            private boolean allRemotes;

            private boolean prune;

            private boolean fullDepth = false;

            private List<Remote> remotes = new ArrayList<Remote>();

            private Optional<Integer> depth = Optional.absent();

            private boolean fetchTags = true;

            public FetchArgs build(Repository repo) {
                if (allRemotes) {
                    remotes.clear();
                    // Add all remotes to list.
                    ImmutableList<Remote> localRemotes = repo.command(RemoteListOp.class).call();
                    remotes.addAll(localRemotes);
                } else if (remotes.isEmpty()) {
                    // If no remotes are specified, default to the origin remote
                    Optional<Remote> origin;
                    origin = repo.command(RemoteResolve.class)
                            .setName(NodeRef.nodeFromPath(Ref.ORIGIN)).call();
                    checkArgument(origin.isPresent(), "Remote could not be resolved.");
                    remotes.add(origin.get());
                }

                final Optional<Integer> repoDepth = repo.getDepth();
                if (repoDepth.isPresent()) {
                    if (fullDepth) {
                        depth = Optional.of(Integer.MAX_VALUE);
                    }
                    if (depth.isPresent()) {
                        if (depth.get() > repoDepth.get()) {
                            repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET)
                                    .setScope(ConfigScope.LOCAL)
                                    .setName(Repository.DEPTH_CONFIG_KEY)
                                    .setValue(depth.get().toString()).call();
                        }
                    }
                } else if (depth.isPresent() || fullDepth) {
                    // Ignore depth, this is a full repository
                    depth = Optional.absent();
                    fullDepth = false;
                }

                return new FetchArgs(fetchTags, prune, fullDepth, ImmutableList.copyOf(remotes),
                        depth);
            }

        }

        final boolean prune;

        final boolean fullDepth;

        final ImmutableList<Remote> remotes;

        final Optional<Integer> depth;

        final boolean fetchTags;

        private FetchArgs(boolean fetchTags, boolean prune, boolean fullDepth,
                ImmutableList<Remote> remotes, Optional<Integer> depth) {
            this.fetchTags = fetchTags;
            this.prune = prune;
            this.fullDepth = fullDepth;
            this.remotes = remotes;
            this.depth = depth;
        }
    }

    /**
     * @param all if {@code true}, fetch from all remotes.
     * @return {@code this}
     */
    public FetchOp setAll(final boolean all) {
        argsBuilder.allRemotes = all;
        return this;
    }

    public boolean isAll() {
        return argsBuilder.allRemotes;
    }

    /**
     * @param prune if {@code true}, remote tracking branches that no longer exist will be removed
     *        locally.
     * @return {@code this}
     */
    public FetchOp setPrune(final boolean prune) {
        argsBuilder.prune = prune;
        return this;
    }

    public boolean isPrune() {
        return argsBuilder.prune;
    }

    /**
     * If no depth is specified, fetch will pull all history from the specified ref(s). If the
     * repository is shallow, it will maintain the existing depth.
     * 
     * @param depth maximum commit depth to fetch
     * @return {@code this}
     */
    public FetchOp setDepth(final int depth) {
        if (depth > 0) {
            argsBuilder.depth = Optional.of(depth);
        }
        return this;
    }

    public Integer getDepth() {
        return argsBuilder.depth.orNull();
    }

    /**
     * If full depth is set on a shallow clone, then the full history will be fetched.
     * 
     * @param fulldepth whether or not to fetch the full history
     * @return {@code this}
     */
    public FetchOp setFullDepth(boolean fullDepth) {
        argsBuilder.fullDepth = fullDepth;
        return this;
    }

    public boolean isFullDepth() {
        return argsBuilder.fullDepth;
    }

    /**
     * @param remoteName the name or URL of a remote repository to fetch from
     * @return {@code this}
     */
    public FetchOp addRemote(final String remoteName) {
        checkNotNull(remoteName);
        return addRemote(command(RemoteResolve.class).setName(remoteName));
    }

    public List<String> getRemoteNames() {
        return Lists.transform(argsBuilder.remotes, (remote) -> remote.getName());
    }

    /**
     * @param remoteSupplier the remote repository to fetch from
     * @return {@code this}
     */
    public FetchOp addRemote(Supplier<Optional<Remote>> remoteSupplier) {
        checkNotNull(remoteSupplier);
        Optional<Remote> remote = remoteSupplier.get();
        checkArgument(remote.isPresent(), "Remote could not be resolved.");
        argsBuilder.remotes.add(remote.get());

        return this;
    }

    public List<Remote> getRemotes() {
        return ImmutableList.copyOf(argsBuilder.remotes);
    }
}
