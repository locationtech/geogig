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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.remotes.RemoteResolve;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.remotes.LsRemoteOp;
import org.locationtech.geogig.remotes.OpenRemote;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.RemoteListOp;
import org.locationtech.geogig.remotes.TransferSummary;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.LocalRemoteRefSpec;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class FetchOp extends AbstractGeoGigOp<TransferSummary> {

    private FetchArgs.Builder argsBuilder = new FetchArgs.Builder();

    protected @Override TransferSummary _call() {
        final Repository localRepo = repository();
        final FetchArgs args = argsBuilder.build(localRepo);

        ProgressListener progress = getProgressListener();
        progress.started();

        TransferSummary transferSummary = new TransferSummary();

        for (Remote remote : args.remotes) {
            if (args.fetchTags) {
                String fetchSpec = remote.getFetchSpec();
                fetchSpec += ";+refs/tags/*:refs/tags/*";
                remote = remote.fetch(fetchSpec);
            }
            try (IRemoteRepo remoteRepo = openRemote(remote)) {
                Preconditions.checkState(remote.equals(remoteRepo.getInfo()));
                progress.setDescription("Fetching " + remoteRepo.getInfo());
                final List<LocalRemoteRef> localToRemoteRemoteRefs = resolveRemoteRefs(remoteRepo);

                final PackRequest request = prepareRequest(localRepo, localToRemoteRemoteRefs);
                request.syncIndexes(args.fetchIndexes);

                if (progress.isCanceled())
                    return null;
                // tell the remote to send us the missing objects
                remoteRepo.command(SendPackOp.class)//
                        .setRequest(request)//
                        .setTarget(localRepo)//
                        .setProgressListener(progress)//
                        .call();

                if (progress.isCanceled())
                    return null;

                Iterable<RefDiff> remoteRemoteRefs;

                // apply the ref diffs to our local remotes namespace and obtain the diffs in the
                // refs/remotes/... namespace
                remoteRemoteRefs = updateLocalRemoteRefs(remote, localToRemoteRemoteRefs,
                        args.prune);
                transferSummary.addAll(remote.getFetchURL(), Lists.newArrayList(remoteRemoteRefs));
                progress.setDescription("Fetched " + remoteRepo.getInfo());
            }
        }

        if (progress.isCanceled())
            return null;

        if (args.fullDepth) {
            // The full history was fetched, this is no longer a shallow clone
            command(ConfigOp.class)//
                    .setAction(ConfigAction.CONFIG_UNSET)//
                    .setScope(ConfigScope.LOCAL)//
                    .setName(Repository.DEPTH_CONFIG_KEY)//
                    .call();
        }

        progress.complete();

        return transferSummary;
    }

    private Iterable<RefDiff> updateLocalRemoteRefs(Remote remote, List<LocalRemoteRef> fetchSpecs,
            final boolean prune) {

        List<RefDiff> updatedLocalRemoteRefs = new ArrayList<>();

        for (LocalRemoteRef expected : fetchSpecs) {
            final boolean isNew = expected.isNew;
            final boolean remoteDeleted = expected.remoteDeleted;
            final String localName = expected.localRemoteRef.getName();

            if (remoteDeleted) {
                if (prune) {
                    updatedLocalRemoteRefs.add(RefDiff.removed(expected.localRemoteRef));
                    command(UpdateRef.class).setName(localName)
                            .setOldValue(expected.localRemoteRef.getObjectId()).setDelete(true)
                            .call();
                }
                continue;
            }
            RefDiff localRefDiff;

            Ref oldRef = isNew ? null : expected.localRemoteRef;
            Ref newRef = new Ref(localName, expected.remoteRef.getObjectId());

            command(UpdateRef.class).setName(localName).setNewValue(newRef.getObjectId()).call();

            localRefDiff = new RefDiff(oldRef, newRef);
            updatedLocalRemoteRefs.add(localRefDiff);
        }
        return updatedLocalRemoteRefs;
    }

    private static class LocalRemoteRef {
        final boolean force, isNew, remoteDeleted;

        final Ref remoteRef, localRemoteRef;

        public LocalRemoteRef(Ref remoteRef, Ref localRemoteRef, boolean force, boolean isNew,
                boolean remoteDeleted) {
            checkNotNull(remoteRef);
            checkNotNull(localRemoteRef);
            this.remoteRef = remoteRef;
            this.localRemoteRef = localRemoteRef;
            this.force = force;
            this.isNew = isNew;
            this.remoteDeleted = remoteDeleted;
        }

        public @Override String toString() {
            return String.format("%s -> %s (%s -> %s)", remoteRef.getName(),
                    localRemoteRef.getName(), remoteRef.getObjectId(),
                    localRemoteRef.getObjectId());
        }
    }

    /**
     * Based on the remote's {@link Remote#getFetchSpecs() fetch specs}, resolves which remote
     * references need to be fetched and returns the mapping of each ref in the remote's namespate
     * to the local remotes namespace (e.g. {@code refs/heads/master -> refs/remotes/origin/master}
     */
    private List<LocalRemoteRef> resolveRemoteRefs(IRemoteRepo remoteRepo) {

        final Map<String, Ref> remoteRemoteRefs;
        final Map<String, Ref> localRemoteRefs;

        {
            LsRemoteOp lsRemote = command(LsRemoteOp.class).setRemote(remoteRepo);

            // Ref::getName, but friendly for Fortify
            Function<Ref, String> fn_ref_getName = new Function<Ref, String>() {
                @Override
                public String apply(Ref ref) {
                    return ref.getName();
                }
            };

            remoteRemoteRefs = new HashMap<>(Maps.uniqueIndex(lsRemote.call(), fn_ref_getName));
            localRemoteRefs = new HashMap<>(
                    Maps.uniqueIndex(lsRemote.retrieveLocalRefs(true).call(), fn_ref_getName));
        }

        List<LocalRemoteRef> refsToFectch = new ArrayList<>();
        final Remote remote = remoteRepo.getInfo();

        for (Ref remoteRef : remoteRemoteRefs.values()) {
            for (LocalRemoteRefSpec spec : remote.getFetchSpecs()) {
                java.util.Optional<String> localName = remote.mapToLocal(remoteRef.getName());
                boolean isNew = false, remoteDeleted = false;
                if (localName.isPresent()) {
                    Ref localRemoteRef = localRemoteRefs.remove(localName.get());
                    if (localRemoteRef == null) {
                        localRemoteRef = new Ref(localName.get(), ObjectId.NULL);
                        isNew = true;
                    }
                    if (!remoteRef.getObjectId().equals(localRemoteRef.getObjectId())) {
                        LocalRemoteRef localRemoteMapping;
                        localRemoteMapping = new LocalRemoteRef(remoteRef, localRemoteRef,
                                spec.isForce(), isNew, remoteDeleted);
                        refsToFectch.add(localRemoteMapping);
                    }
                    break;
                }
            }
        }
        // remaining refs found in the local repository
        for (Ref localRemote : localRemoteRefs.values()) {
            for (LocalRemoteRefSpec spec : remote.getFetchSpecs()) {
                java.util.Optional<String> remoteName;
                remoteName = remote.mapToRemote(localRemote.getName());
                boolean isNew = false, remoteDeleted = false;
                if (remoteName.isPresent()) {
                    Ref remoteRef = remoteRemoteRefs.remove(remoteName.get());
                    if (remoteRef == null) {
                        remoteRef = new Ref(remoteName.get(), ObjectId.NULL);
                        remoteDeleted = true;
                    }
                    if (remoteDeleted
                            || !localRemote.getObjectId().equals(remoteRef.getObjectId())) {
                        LocalRemoteRef localRemoteMapping;
                        localRemoteMapping = new LocalRemoteRef(remoteRef, localRemote,
                                spec.isForce(), isNew, remoteDeleted);
                        refsToFectch.add(localRemoteMapping);
                    }
                    break;
                }
            }
        }

        return refsToFectch;
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
     * @param localToRemoteRemoteRefs
     */
    private PackRequest prepareRequest(Repository local,
            List<LocalRemoteRef> localToRemoteRemoteRefs) {

        PackRequest request = new PackRequest();

        for (LocalRemoteRef refFetchSpec : localToRemoteRemoteRefs) {
            final Ref remoteRef = refFetchSpec.remoteRef;
            final Ref localRef = refFetchSpec.localRemoteRef;
            if (remoteRef.getObjectId().equals(localRef.getObjectId())) {
                continue;
            }
            if (remoteRef.getObjectId().isNull()) {
                continue;// filter out pruned remote refs
            }

            RefRequest req;
            ObjectId haveTip = localRef.getObjectId();
            // may the want commit exist in the local repository's object database nonetheless?
            ObjectId wantId = remoteRef.getObjectId();
            if (!wantId.isNull() && local.objectDatabase().exists(wantId)) {
                haveTip = wantId;
            }
            req = RefRequest.want(remoteRef, haveTip);
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
    private static @AllArgsConstructor @Value class FetchArgs {

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

            private boolean fetchIndexes = false;

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
                        depth, fetchIndexes);
            }

        }

        final boolean fetchTags;

        final boolean prune;

        final boolean fullDepth;

        final ImmutableList<Remote> remotes;

        final Optional<Integer> depth;

        private boolean fetchIndexes;
    }

    /**
     * @param all if {@code true}, fetch from all remotes.
     * @return {@code this}
     * @deprecated use {@link #setAllRemotes} instead
     */
    public FetchOp setAll(final boolean all) {
        argsBuilder.allRemotes = all;
        return this;
    }

    /**
     * @param all if {@code true}, fetch from all remotes.
     * @return {@code this}
     */
    public FetchOp setAllRemotes(final boolean all) {
        argsBuilder.allRemotes = all;
        return this;
    }

    /**
     * @deprecated use {@link #isAllRemotes} instead
     */
    public boolean isAll() {
        return argsBuilder.allRemotes;
    }

    public boolean isAllRemotes() {
        return argsBuilder.allRemotes;
    }

    public FetchOp setAutofetchTags(final boolean tags) {
        argsBuilder.fetchTags = tags;
        return this;
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

        // (remote) -> remote.getName()
        Function<Remote, String> fn = new Function<Remote, String>() {
            @Override
            public String apply(Remote remote) {
                return remote.getName();
            }
        };

        return Lists.transform(argsBuilder.remotes, fn);
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

    public FetchOp setFetchIndexes(boolean fetchIndexes) {
        argsBuilder.fetchIndexes = fetchIndexes;
        return this;
    }

}
