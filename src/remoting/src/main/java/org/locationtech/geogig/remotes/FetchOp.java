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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.filter;
import static org.locationtech.geogig.remotes.RefDiff.Type.ADDED_REF;
import static org.locationtech.geogig.remotes.RefDiff.Type.REMOVED_REF;

import java.util.ArrayList;
import java.util.List;

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
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.RepositoryImpl;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

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

            private Optional<Integer> depth = Optional.absent();

            private boolean fetchTags = true;

            public boolean fetchIndexes;

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

        final boolean prune;

        final boolean fullDepth;

        final ImmutableList<Remote> remotes;

        final Optional<Integer> depth;

        final boolean fetchTags;

        final boolean fetchIndexes;

        private FetchArgs(boolean fetchTags, boolean prune, boolean fullDepth,
                ImmutableList<Remote> remotes, Optional<Integer> depth, boolean fetchIndexes) {
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

    public FetchOp setAutofetchTags(final boolean tags) {
        argsBuilder.fetchTags = tags;
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

    public FetchOp addRemote(final Remote remote) {
        return addRemote(Suppliers.ofInstance(Optional.of(remote)));
    }

    public List<String> getRemoteNames() {

        //(remote) -> remote.getName()
        Function<Remote, String> fn =  new Function<Remote, String>() {
            @Override
            public String apply(Remote remote) {
                return remote.getName();
            }};

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

    /**
     * Executes the fetch operation.
     * 
     * @return {@code null}
     * @see org.locationtech.geogig.repository.AbstractGeoGigOp#call()
     */
    @Override
    protected TransferSummary _call() {
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
                        .setDepth(argsBuilder.depth.or(0))//
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
                Integer depth = repo.getDepth().or(0);
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
            final ImmutableSet<Ref> remoteRemoteRefs = getRemoteRefs(remoteRepo, args, remote);
            final ImmutableSet<Ref> localRemoteRefs = getRemoteLocalRefs(remote);

            // If we have specified a depth to pull, we may have more history to pull from
            // existing refs.
            needUpdate = findOutdatedRefs(remote, remoteRemoteRefs, localRemoteRefs, args.depth);
            if (args.prune) {
                prune(remoteRemoteRefs, localRemoteRefs, needUpdate);
            }

            //(r) -> r.getType() != REMOVED_REF
            Predicate<RefDiff> fn =  new Predicate<RefDiff>() {
                @Override
                public boolean apply(RefDiff r) {
                    return r.getType() != REMOVED_REF;
                }};

            for (RefDiff ref : filter(needUpdate, fn)) {
                final Optional<Integer> repoDepth = repository.getDepth();
                final boolean isShallow = repoDepth.isPresent();

                // If we haven't specified a depth, but this is a shallow repository, set
                // the fetch limit to the current repository depth.
                final Optional<Integer> newFetchLimit = args.depth.or(
                        isShallow && ref.getType() == ADDED_REF ? repoDepth : Optional.absent());

                // Fetch updated data from this ref
                final Ref newRef = ref.getNewRef();
                remoteRepo.fetchNewData(repository, newRef, newFetchLimit, getProgressListener());

                if (isShallow && !args.fullDepth) {
                    // Update the repository depth if it is deeper than before.
                    int newDepth = repository.graphDatabase().getDepth(newRef.getObjectId());

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

    private void prune(final ImmutableSet<Ref> remoteRemoteRefs,
            final ImmutableSet<Ref> localRemoteRefs, List<RefDiff> needUpdate) {
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

    private ImmutableSet<Ref> getRemoteLocalRefs(Remote remote) {
        final ImmutableSet<Ref> localRemoteRefs;
        localRemoteRefs = command(LsRemoteOp.class)//
                .retrieveLocalRefs(true)//
                .setRemote(Suppliers.ofInstance(Optional.of(remote)))//
                .call();
        return localRemoteRefs;
    }

    private ImmutableSet<Ref> getRemoteRefs(final IRemoteRepo remoteRepo, final FetchArgs args,
            Remote remote) {

        final Optional<Integer> repoDepth = repository().getDepth();
        final boolean getTags = args.fetchTags && !remote.getMapped()
                && (!repoDepth.isPresent() || args.fullDepth);

        ImmutableSet<Ref> remoteRemoteRefs;
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

    private Ref updateLocalRef(Ref remoteRef, Remote remote, ImmutableSet<Ref> localRemoteRefs) {
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
            updatedRef = command(UpdateSymRef.class).setName(refName).setNewValue(newTarget).call()
                    .get();
        } else {
            ObjectId effectiveId = remoteRef.getObjectId();

            if (remote.getMapped() && !repository().commitExists(remoteRef.getObjectId())) {
                effectiveId = graphDatabase().getMapping(effectiveId);
            }
            updatedRef = command(UpdateRef.class).setName(refName).setNewValue(effectiveId).call()
                    .get();
        }
        return updatedRef;
    }

    /**
     * Filters the remote references for the given remote that are not present or outdated in the
     * local repository
     */
    private List<RefDiff> findOutdatedRefs(Remote remote, ImmutableSet<Ref> remoteRefs,
            ImmutableSet<Ref> localRemoteRefs, Optional<Integer> depth) {

        List<RefDiff> changedRefs = Lists.newLinkedList();

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
    private Optional<Ref> findLocal(Ref remoteRef, ImmutableSet<Ref> localRemoteRefs) {
        if (remoteRef.getName().startsWith(Ref.TAGS_PREFIX)) {
            return command(RefParse.class).setName(remoteRef.getName()).call();
        } else {
            for (Ref localRef : localRemoteRefs) {
                if (localRef.localName().equals(remoteRef.localName())) {
                    return Optional.of(localRef);
                }
            }
            return Optional.absent();
        }
    }
}
