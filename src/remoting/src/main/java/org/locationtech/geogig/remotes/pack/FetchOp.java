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
import static com.google.common.collect.Iterables.filter;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject;
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
 * For each {@link #addRemote remote} specified, the fetch process consists of three basic steps:
 * <ul>
 * <li>Resolve the {@link RefDiff} differences between the local copies of the remote refs and the
 * current state of the remote refs in the remote repository
 * <li>Having resolved the outdated refs in the local copy of the remote refs, prepare a
 * {@link PackRequest} and call {@link SendPackOp} on the remote repository with that request, and
 * the local repository as the target. This transfers all the missing {@link RevObject} instances
 * from the remote to the local repository, as {@link SendPackOp} will call {@link ReceivePackOp} on
 * the target repo.
 * <li>Finally, update the local copies of the remote references so they point to the
 * {@link RevObject}s identified in the first step.
 * </ul>
 * <p>
 * This process is essentially the same than {@link PushOp} with inverted source and target
 * repositories. That is, {@code FetchOp} calls {@link SendPackOp} on the remote repository with the
 * local as target, and {@link PushOp} calls {@link SendPackOp} on the local repository with the
 * remote as target. Then both update the needed {@link Ref refs} at either side.
 * <p>
 * The result is a {@link TransferSummary} whose {@link TransferSummary#getRefDiffs() refDiffs} map
 * has a single entry keyed by the remote's {@link Remote#getPushURL() pushURL}, with one
 * {@link RefDiff} entry for each <b>remote</b> reference updated (i.e. the refs in the remote
 * repository under its {@code refs/heads/ or {@code refs/tags} namespaces that were created,
 * deleted, or updated.
 */
public class FetchOp extends AbstractGeoGigOp<TransferSummary> {

    private FetchArgs.Builder argsBuilder = new FetchArgs.Builder();

    protected @Override TransferSummary _call() {
        final Repository localRepo = repository();
        final FetchArgs args = argsBuilder.build(localRepo);

        ProgressListener progress = getProgressListener();
        progress.started();

        TransferSummary result = new TransferSummary();

        for (Remote remote : args.remotes) {
            try (IRemoteRepo remoteRepo = openRemote(remote)) {
                // get ref diffs in the remote's local namespace (i.e. refs/heads/<branch>)
                // this represents which remote refs we need to update
                final List<RefDiff> refDiffs = diffRemoteRefs(remoteRepo, args.fetchTags);
                // This is what needs to be transferred (what I want and what I already have for
                // each ref)
                final PackRequest request = prepareRequest(localRepo, refDiffs);

                // tell the remote to send us the missing objects
                Iterable<RefDiff> localRemoteResults;
                localRemoteResults = remoteRepo.command(SendPackOp.class)//
                        .setRequest(request)//
                        .setTarget(localRepo)//
                        .setProgressListener(progress)//
                        .call();

                Iterable<RefDiff> remoteRemoteRefs;

                // apply the ref diffs to our local remotes namespace and obtain the diffs in the
                // refs/remotes/... namespace
                remoteRemoteRefs = updateLocalRemoteRefs(remote, refDiffs, args.prune);
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

    private List<RefDiff> updateLocalRemoteRefs(Remote remote, Iterable<RefDiff> localRemoteResults,
            boolean prune) {

        if (!prune) {
            localRemoteResults = Iterables.filter(localRemoteResults, (d) -> !d.isDelete());
        }
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
     * @param refdiffs
     */
    private PackRequest prepareRequest(Repository local, List<RefDiff> refdiffs) {

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
            Ref want = cr.getNewRef().peel();
            // may the want commit exist in the local repository's object database nonetheless?
            ObjectId wantId = want.getObjectId();
            if (!wantId.isNull() && local.objectDatabase().exists(wantId)) {
                haveTip = wantId;
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
