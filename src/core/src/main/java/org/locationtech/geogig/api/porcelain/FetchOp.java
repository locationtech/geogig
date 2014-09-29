/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.Remote;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.LsRemote;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.api.porcelain.FetchResult.ChangedRef;
import org.locationtech.geogig.api.porcelain.FetchResult.ChangedRef.ChangeTypes;
import org.locationtech.geogig.remote.IRemoteRepo;
import org.locationtech.geogig.remote.RemoteUtils;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.DeduplicationService;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

/**
 * Fetches named heads or tags from one or more other repositories, along with the objects necessary
 * to complete them.
 */
public class FetchOp extends AbstractGeoGigOp<FetchResult> {

    private boolean all;

    private boolean prune;

    private boolean fullDepth = false;

    private List<Remote> remotes = new ArrayList<Remote>();

    private Optional<Integer> depth = Optional.absent();

    /**
     * @param all if {@code true}, fetch from all remotes.
     * @return {@code this}
     */
    public FetchOp setAll(final boolean all) {
        this.all = all;
        return this;
    }

    public boolean isAll() {
        return all;
    }

    /**
     * @param prune if {@code true}, remote tracking branches that no longer exist will be removed
     *        locally.
     * @return {@code this}
     */
    public FetchOp setPrune(final boolean prune) {
        this.prune = prune;
        return this;
    }

    public boolean isPrune() {
        return prune;
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
            this.depth = Optional.of(depth);
        }
        return this;
    }

    public Integer getDepth() {
        return this.depth.orNull();
    }

    /**
     * If full depth is set on a shallow clone, then the full history will be fetched.
     * 
     * @param fulldepth whether or not to fetch the full history
     * @return {@code this}
     */
    public FetchOp setFullDepth(boolean fullDepth) {
        this.fullDepth = fullDepth;
        return this;
    }

    public boolean isFullDepth() {
        return fullDepth;
    }

    /**
     * @param remoteName the name or URL of a remote repository to fetch from
     * @return {@code this}
     */
    public FetchOp addRemote(final String remoteName) {
        Preconditions.checkNotNull(remoteName);
        return addRemote(command(RemoteResolve.class).setName(remoteName));
    }

    public List<String> getRemoteNames() {
        return Lists.transform(this.remotes, new Function<Remote, String>() {

            @Override
            public String apply(Remote remote) {
                return remote.getName();
            }
        });
    }

    /**
     * @param remoteSupplier the remote repository to fetch from
     * @return {@code this}
     */
    public FetchOp addRemote(Supplier<Optional<Remote>> remoteSupplier) {
        Preconditions.checkNotNull(remoteSupplier);
        Optional<Remote> remote = remoteSupplier.get();
        Preconditions.checkState(remote.isPresent(), "Remote could not be resolved.");
        remotes.add(remote.get());

        return this;
    }

    public List<Remote> getRemotes() {
        return ImmutableList.copyOf(remotes);
    }

    /**
     * Executes the fetch operation.
     * 
     * @return {@code null}
     * @see org.locationtech.geogig.api.AbstractGeoGigOp#call()
     */
    @Override
    protected FetchResult _call() {
        if (all) {
            // Add all remotes to list.
            ImmutableList<Remote> localRemotes = command(RemoteListOp.class).call();
            for (Remote remote : localRemotes) {
                if (!remotes.contains(remote)) {
                    remotes.add(remote);
                }
            }
        } else if (remotes.size() == 0) {
            // If no remotes are specified, default to the origin remote
            addRemote("origin");
        }

        getProgressListener().started();

        Optional<Integer> repoDepth = repository().getDepth();
        if (repoDepth.isPresent()) {
            if (fullDepth) {
                depth = Optional.of(Integer.MAX_VALUE);
            }
            if (depth.isPresent()) {
                if (depth.get() > repoDepth.get()) {
                    command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET)
                            .setScope(ConfigScope.LOCAL).setName(Repository.DEPTH_CONFIG_KEY)
                            .setValue(depth.get().toString()).call();
                    repoDepth = depth;
                }
            }
        } else if (depth.isPresent() || fullDepth) {
            // Ignore depth, this is a full repository
            depth = Optional.absent();
            fullDepth = false;
        }

        FetchResult result = new FetchResult();

        for (Remote remote : remotes) {
            ProgressListener subProgress = this.subProgress(100.f / remotes.size());
            subProgress.started();
            final ImmutableSet<Ref> remoteRemoteRefs = command(LsRemote.class)
                    .setRemote(Suppliers.ofInstance(Optional.of(remote)))
                    .retrieveTags(!remote.getMapped() && (!repoDepth.isPresent() || fullDepth))
                    .call();
            final ImmutableSet<Ref> localRemoteRefs = command(LsRemote.class)
                    .retrieveLocalRefs(true).setRemote(Suppliers.ofInstance(Optional.of(remote)))
                    .call();

            // If we have specified a depth to pull, we may have more history to pull from existing
            // refs.
            List<ChangedRef> needUpdate = findOutdatedRefs(remote, remoteRemoteRefs,
                    localRemoteRefs, depth);

            if (prune) {
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
                    if (!locals.contains(localRef)) {
                        // Delete the ref
                        ChangedRef changedRef = new ChangedRef(localRef, null,
                                ChangeTypes.REMOVED_REF);
                        needUpdate.add(changedRef);
                        command(UpdateRef.class).setDelete(true).setName(localRef.getName()).call();
                    }
                }
            }

            Optional<IRemoteRepo> remoteRepo = getRemoteRepo(remote, repository()
                    .deduplicationService());

            Preconditions.checkState(remoteRepo.isPresent(), "Failed to connect to the remote.");
            IRemoteRepo remoteRepoInstance = remoteRepo.get();
            try {
                remoteRepoInstance.open();
            } catch (IOException e) {
                Throwables.propagate(e);
            }
            try {
                int refCount = 0;
                for (ChangedRef ref : needUpdate) {
                    if (ref.getType() != ChangeTypes.REMOVED_REF) {
                        refCount++;
                        subProgress.setProgress((refCount * 100.f) / needUpdate.size());

                        Optional<Integer> newFetchLimit = depth;
                        // If we haven't specified a depth, but this is a shallow repository, set
                        // the
                        // fetch limit to the current repository depth.
                        if (!newFetchLimit.isPresent() && repoDepth.isPresent()
                                && ref.getType() == ChangeTypes.ADDED_REF) {
                            newFetchLimit = repoDepth;
                        }
                        // Fetch updated data from this ref
                        remoteRepoInstance.fetchNewData(ref.getNewRef(), newFetchLimit);

                        if (repoDepth.isPresent() && !fullDepth) {
                            // Update the repository depth if it is deeper than before.
                            int newDepth;
                            try {
                                newDepth = repository().graphDatabase().getDepth(
                                        ref.getNewRef().getObjectId());
                            } catch (IllegalStateException e) {
                                throw new RuntimeException(ref.toString(), e);
                            }

                            if (newDepth > repoDepth.get()) {
                                command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET)
                                        .setScope(ConfigScope.LOCAL)
                                        .setName(Repository.DEPTH_CONFIG_KEY)
                                        .setValue(Integer.toString(newDepth)).call();
                                repoDepth = Optional.of(newDepth);
                            }
                        }

                        // Update the ref
                        Ref updatedRef = updateLocalRef(ref.getNewRef(), remote, localRemoteRefs);
                        ref.setNewRef(updatedRef);
                    }
                }

                if (needUpdate.size() > 0) {
                    result.getChangedRefs().put(remote.getFetchURL(), needUpdate);
                }

                // Update HEAD ref
                if (!remote.getMapped()) {
                    Ref remoteHead = remoteRepoInstance.headRef();
                    if (remoteHead != null) {
                        updateLocalRef(remoteHead, remote, localRemoteRefs);
                    }
                }
            } finally {
                try {
                    remoteRepoInstance.close();
                } catch (IOException e) {
                    Throwables.propagate(e);
                }
            }
            subProgress.complete();
        }

        if (fullDepth) {
            // The full history was fetched, this is no longer a shallow clone
            command(ConfigOp.class).setAction(ConfigAction.CONFIG_UNSET)
                    .setScope(ConfigScope.LOCAL).setName(Repository.DEPTH_CONFIG_KEY).call();
        }

        getProgressListener().complete();

        return result;
    }

    /**
     * @param remote the remote to get
     * @return an interface for the remote repository
     */
    public Optional<IRemoteRepo> getRemoteRepo(Remote remote,
            DeduplicationService deduplicationService) {
        return RemoteUtils.newRemote(GlobalContextBuilder.builder.build(Hints.readOnly()), remote,
                repository(), deduplicationService);
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
            command(UpdateSymRef.class).setName(refName).setNewValue(newTarget).call();
        } else {
            ObjectId effectiveId = remoteRef.getObjectId();

            if (remote.getMapped() && !repository().commitExists(remoteRef.getObjectId())) {
                effectiveId = graphDatabase().getMapping(effectiveId);
                updatedRef = new Ref(remoteRef.getName(), effectiveId);
            }
            command(UpdateRef.class).setName(refName).setNewValue(effectiveId).call();
        }
        return updatedRef;
    }

    /**
     * Filters the remote references for the given remote that are not present or outdated in the
     * local repository
     */
    private List<ChangedRef> findOutdatedRefs(Remote remote, ImmutableSet<Ref> remoteRefs,
            ImmutableSet<Ref> localRemoteRefs, Optional<Integer> depth) {

        List<ChangedRef> changedRefs = Lists.newLinkedList();

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
                    ChangedRef changedRef = new ChangedRef(local.get(), remoteRef,
                            ChangeTypes.CHANGED_REF);
                    changedRefs.add(changedRef);
                } else if (depth.isPresent()) {
                    int commitDepth = graphDatabase().getDepth(local.get().getObjectId());
                    if (depth.get() > commitDepth) {
                        ChangedRef changedRef = new ChangedRef(local.get(), remoteRef,
                                ChangeTypes.DEEPENED_REF);
                        changedRefs.add(changedRef);
                    }
                }
            } else {
                ChangedRef changedRef = new ChangedRef(null, remoteRef, ChangeTypes.ADDED_REF);
                changedRefs.add(changedRef);
            }
        }
        return changedRefs;
    }

    /**
     * Finds the corresponding local reference in {@code localRemoteRefs} for the given remote ref
     * 
     * @param remoteRef a ref in the {@code refs/heads} or {@code refs/tags} namespace as given by
     *        {@link LsRemote} when querying a remote repository
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
