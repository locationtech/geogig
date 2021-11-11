/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */

package org.locationtech.geogig.remotes;

import static org.locationtech.geogig.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.filter;
import static org.locationtech.geogig.remotes.RefDiff.Type.ADDED_REF;
import static org.locationtech.geogig.remotes.RefDiff.Type.REMOVED_REF;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.plumbing.remotes.RemoteResolve;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.repository.impl.RepositoryImpl;

import lombok.NonNull;

/**
 * Fetches named heads or tags from one or more other repositories, along with the objects necessary
 * to complete them.
 */
public class FetchOp extends AbstractGeoGigOp<TransferSummary> {

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

            private Optional<Integer> depth = Optional.empty();

            private boolean fetchTags = true;

            public boolean fetchIndexes;

            public FetchArgs build(Repository repo) {
                if (allRemotes) {
                    remotes.clear();
                    // Add all remotes to list.
                    List<Remote> localRemotes = repo.command(RemoteListOp.class).call();
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
                    depth = Optional.empty();
                    fullDepth = false;
                }

                return new FetchArgs(fetchTags, prune, fullDepth, new ArrayList<>(remotes), depth,
                        fetchIndexes);
            }

        }

        final boolean prune;

        final boolean fullDepth;

        final List<Remote> remotes;

        final Optional<Integer> depth;

        final boolean fetchTags;

        final boolean fetchIndexes;

        private FetchArgs(boolean fetchTags, boolean prune, boolean fullDepth, List<Remote> remotes,
                Optional<Integer> depth, boolean fetchIndexes) {
            this.fetchTags = fetchTags;
            this.prune = prune;
            this.fullDepth = fullDepth;
            this.remotes = remotes;
            this.depth = depth;
            this.fetchIndexes = fetchIndexes;
        }
    }

    private FetchArgs.Builder argsBuilder = new FetchArgs.Builder();

    /**
     * @param all if {@code true}, fetch from all remotes.
     * @return {@code this}
     */
    public FetchOp setAllRemotes(final boolean all) {
        argsBuilder.allRemotes = all;
        return this;
    }

    public FetchOp setAutofetchTags(final boolean tags) {
        argsBuilder.fetchTags = tags;
        return this;
    }

    public boolean isAllRemotes() {
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

    public FetchOp setFetchIndexes(boolean fetchIndexes) {
        argsBuilder.fetchIndexes = fetchIndexes;
        return this;
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
        return argsBuilder.depth.orElse(null);
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
    public FetchOp addRemote(final @NonNull String remoteName) {
        return addRemote(command(RemoteResolve.class).setName(remoteName));
    }

    public FetchOp addRemote(final @NonNull Remote remote) {
        return addRemote(() -> Optional.of(remote));
    }

    public List<String> getRemoteNames() {
        return argsBuilder.remotes.stream().map(Remote::getName).collect(Collectors.toList());
    }

    /**
     * @param remoteSupplier the remote repository to fetch from
     * @return {@code this}
     */
    public FetchOp addRemote(@NonNull Supplier<Optional<Remote>> remoteSupplier) {
        Optional<Remote> remote = remoteSupplier.get();
        checkArgument(remote.isPresent(), "Remote could not be resolved.");
        argsBuilder.remotes.add(remote.get());

        return this;
    }

    public List<Remote> getRemotes() {
        return new ArrayList<>(argsBuilder.remotes);
    }

    /**
     * Executes the fetch operation.
     * 
     * @return {@code null}
     * @see org.locationtech.geogig.repository.impl.AbstractGeoGigOp#call()
     */
    protected @Override TransferSummary _call() {
        final Repository repository = repository();
        final FetchArgs args = argsBuilder.build(repository);
        {
            // defer to the new FetchOp implementation as long as it's not a shallow or sparse clone
            // UNTIL its ready for shallow and sparse clones.
            boolean isHttp = isHttp(args);// don't call new fetch on http(s) remotes until it's
                                          // ready
            boolean isShallow = repository.getDepth().isPresent() || anyRemoteIsShallow(args);
            boolean isSparse = RepositoryImpl.getFilter(repository).isPresent();
            if (!(isHttp || isShallow || isSparse)) {
                return command(org.locationtech.geogig.remotes.pack.FetchOp.class)//
                        .setAllRemotes(argsBuilder.allRemotes)//
                        .setDepth(argsBuilder.depth.orElse(0))//
                        .setFullDepth(argsBuilder.fullDepth)//
                        .setPrune(argsBuilder.prune)//
                        .addRemotes(argsBuilder.remotes)//
                        .setAutofetchTags(argsBuilder.fetchTags)//
                        .setFetchIndexes(argsBuilder.fetchIndexes)//
                        .setProgressListener(getProgressListener())//
                        .call();
            }
        }

        getProgressListener().started();

        TransferSummary transferSummary = new TransferSummary();

        for (Remote remote : args.remotes) {
            List<RefDiff> needUpdate = fetch(remote, args);
            if (!needUpdate.isEmpty()) {
                String fetchURL = remote.getFetchURL();
                transferSummary.addAll(fetchURL, needUpdate);
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

        getProgressListener().complete();

        return transferSummary;
    }

    private boolean isHttp(FetchArgs args) {
        for (Remote r : args.remotes) {
            if (r.getFetchURL().startsWith("http")) {
                return true;
            }
        }
        return false;
    }

    private boolean anyRemoteIsShallow(FetchArgs args) {
        for (Remote remote : args.remotes) {
            try (IRemoteRepo repo = openRemote(remote)) {
                Integer depth = repo.getDepth().orElse(0);
                if (depth.intValue() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<RefDiff> fetch(Remote remote, FetchArgs args) {

        List<RefDiff> needUpdate;

        try (IRemoteRepo remoteRepo = openRemote(remote)) {
            final Repository repository = repository();
            final Set<Ref> remoteRemoteRefs = getRemoteRefs(remoteRepo, args, remote);
            final Set<Ref> localRemoteRefs = getRemoteLocalRefs(remote);

            // If we have specified a depth to pull, we may have more history to pull from
            // existing refs.
            needUpdate = findOutdatedRefs(remote, remoteRemoteRefs, localRemoteRefs, args.depth);
            if (args.prune) {
                prune(remoteRemoteRefs, localRemoteRefs, needUpdate);
            }

            for (RefDiff ref : filter(needUpdate, r -> r.getType() != REMOVED_REF)) {
                final Optional<Integer> repoDepth = repository.getDepth();
                final boolean isShallow = repoDepth.isPresent();

                // If we haven't specified a depth, but this is a shallow repository, set
                // the fetch limit to the current repository depth.
                final Optional<Integer> newFetchLimit = args.depth.isPresent() ? args.depth
                        : Optional.ofNullable(
                                isShallow && ref.getType() == ADDED_REF ? repoDepth.orElse(null)
                                        : null);

                // Fetch updated data from this ref
                final Ref newRef = ref.getNewRef();
                remoteRepo.fetchNewData(repository, newRef, newFetchLimit, getProgressListener());

                if (isShallow && !args.fullDepth) {
                    // Update the repository depth if it is deeper than before.
                    int newDepth = repository.context().graphDatabase()
                            .getDepth(newRef.getObjectId());

                    if (newDepth > repoDepth.get()) {
                        command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET)
                                .setScope(ConfigScope.LOCAL).setName(Repository.DEPTH_CONFIG_KEY)
                                .setValue(Integer.toString(newDepth)).call();
                    }
                }

                // Update the ref
                Ref updatedRef = updateLocalRef(newRef, remote, localRemoteRefs);
                ref.setNewRef(updatedRef);
            }

            // Update HEAD ref
            if (!remote.getMapped()) {
                Optional<Ref> remoteHead = remoteRepo.headRef();
                if (remoteHead.isPresent() && !remoteHead.get().getObjectId().isNull()) {
                    updateLocalRef(remoteHead.get(), remote, localRemoteRefs);
                }
            }
        }

        return needUpdate;
    }

    private void prune(final Set<Ref> remoteRemoteRefs, final Set<Ref> localRemoteRefs,
            List<RefDiff> needUpdate) {
        // Delete local refs that aren't in the remote
        List<Ref> locals = new ArrayList<Ref>();
        // only branches, not tags, appear in the remoteRemoteRefs list so we will not catch
        // any tags in this check. However, we do not track which remote originally
        // provided a tag so it makes sense not to prune them anyway.
        for (Ref remoteRef : remoteRemoteRefs) {
            Optional<Ref> localRef = findLocal(remoteRef, localRemoteRefs);
            if (localRef.isPresent()) {
                locals.add(localRef.get());
            }
        }
        for (Ref localRef : localRemoteRefs) {
            if (!(localRef instanceof SymRef) && !locals.contains(localRef)) {
                // Delete the ref
                RefDiff RefDiff = new RefDiff(localRef, null);
                needUpdate.add(RefDiff);
                command(UpdateRef.class).setDelete(true).setName(localRef.getName()).call();
            }
        }
    }

    private Set<Ref> getRemoteLocalRefs(Remote remote) {
        final Set<Ref> localRemoteRefs;
        localRemoteRefs = command(LsRemoteOp.class)//
                .retrieveLocalRefs(true)//
                .setRemote(() -> Optional.of(remote))//
                .call();
        return localRemoteRefs;
    }

    private Set<Ref> getRemoteRefs(final IRemoteRepo remoteRepo, final FetchArgs args,
            Remote remote) {

        final Optional<Integer> repoDepth = repository().getDepth();
        final boolean getTags = args.fetchTags && !remote.getMapped()
                && (!repoDepth.isPresent() || args.fullDepth);

        Set<Ref> remoteRemoteRefs;
        remoteRemoteRefs = command(LsRemoteOp.class)//
                .setRemote(remoteRepo)//
                .retrieveLocalRefs(false)//
                .retrieveTags(getTags)//
                .call();

        return remoteRemoteRefs;
    }

    private IRemoteRepo openRemote(Remote remote) {
        return command(OpenRemote.class).setRemote(remote).readOnly().call();
    }

    private Ref updateLocalRef(Ref remoteRef, Remote remote, Set<Ref> localRemoteRefs) {
        final String refName;
        if (remoteRef.getName().startsWith(Ref.TAGS_PREFIX)) {
            refName = remoteRef.getName();
        } else {
            refName = Ref.REMOTES_PREFIX + remote.getName() + "/" + remoteRef.localName();
        }
        Ref updatedRef = remoteRef;
        if (remoteRef instanceof SymRef) {
            String targetBranch = Ref.localName(((SymRef) remoteRef).getTarget());
            String newTarget = Ref.REMOTES_PREFIX + remote.getName() + "/" + targetBranch;
            updatedRef = command(UpdateSymRef.class).setName(refName).setNewValue(newTarget)
                    .setReason("fetch").call().get();
        } else {
            ObjectId effectiveId = remoteRef.getObjectId();

            if (remote.getMapped() && !geogig().objects().commitExists(remoteRef.getObjectId())) {
                effectiveId = graphDatabase().getMapping(effectiveId);
            }
            updatedRef = command(UpdateRef.class).setName(refName).setNewValue(effectiveId)
                    .setReason("fetch").call().get();
        }
        return updatedRef;
    }

    /**
     * Filters the remote references for the given remote that are not present or outdated in the
     * local repository
     */
    private List<RefDiff> findOutdatedRefs(Remote remote, Set<Ref> remoteRefs,
            Set<Ref> localRemoteRefs, Optional<Integer> depth) {

        List<RefDiff> changedRefs = new LinkedList<>();

        for (Ref remoteRef : remoteRefs) {// refs/heads/xxx or refs/tags/yyy, though we don't handle
                                          // tags yet
            if (remote.getMapped()
                    && !remoteRef.localName().equals(Ref.localName(remote.getMappedBranch()))) {
                // for a mapped remote, we are only interested in the branch we are mapped to
                continue;
            }
            Optional<Ref> local = findLocal(remoteRef, localRemoteRefs);
            if (local.isPresent()) {
                if (!local.get().getObjectId().equals(remoteRef.getObjectId())) {
                    RefDiff changedRef = new RefDiff(local.get(), remoteRef);
                    changedRefs.add(changedRef);
                } else if (depth.isPresent()) {
                    int commitDepth = graphDatabase().getDepth(local.get().getObjectId());
                    if (depth.get() > commitDepth) {
                        RefDiff RefDiff = new RefDiff(local.get(), remoteRef);
                        changedRefs.add(RefDiff);
                    }
                }
            } else {
                RefDiff RefDiff = new RefDiff(null, remoteRef);
                changedRefs.add(RefDiff);
            }
        }
        return changedRefs;
    }

    /**
     * Finds the corresponding local reference in {@code localRemoteRefs} for the given remote ref
     * 
     * @param remoteRef a ref in the {@code refs/heads} or {@code refs/tags} namespace as given by
     *        {@link LsRemoteOp} when querying a remote repository
     * @param localRemoteRefs the list of locally known references of the given remote in the
     *        {@code refs/remotes/<remote name>/} namespace
     */
    private Optional<Ref> findLocal(Ref remoteRef, Set<Ref> localRemoteRefs) {
        if (remoteRef.getName().startsWith(Ref.TAGS_PREFIX)) {
            return command(RefParse.class).setName(remoteRef.getName()).call();
        } else {
            for (Ref localRef : localRemoteRefs) {
                if (localRef.localName().equals(remoteRef.localName())) {
                    return Optional.of(localRef);
                }
            }
            return Optional.empty();
        }
    }
}
