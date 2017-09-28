/* Copyright (c) 2013-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remotes.internal;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.remotes.SynchronizationException;
import org.locationtech.geogig.remotes.SynchronizationException.StatusCode;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Provides a base implementation for different representations of the {@link IRemoteRepo}.
 * 
 * @see IRemoteRepo
 */
public abstract class AbstractRemoteRepo implements IRemoteRepo {

    private final Remote remote;

    protected AbstractRemoteRepo(Remote remote) {
        Preconditions.checkNotNull(remote);
        this.remote = remote;
    }

    public @Override Remote getInfo() {
        return remote;
    }

    /**
     * CommitTraverser for transfers from a shallow clone to a full repository. This works just like
     * a normal commit traverser, but will throw an appropriate synchronization exception when the
     * history is not deep enough to perform the traversal.
     */
    protected class ShallowFullCommitTraverser extends CommitTraverser {

        private RepositoryWrapper source;

        private RepositoryWrapper destination;

        public ShallowFullCommitTraverser(RepositoryWrapper source, RepositoryWrapper destination) {
            this.source = source;
            this.destination = destination;
        }

        @Override
        protected Evaluation evaluate(CommitNode commitNode) {

            if (destination.objectExists(commitNode.getObjectId())) {
                return Evaluation.EXCLUDE_AND_PRUNE;
            }

            if (!commitNode.getObjectId().equals(ObjectId.NULL)
                    && !source.objectExists(commitNode.getObjectId())) {
                // Source is too shallow
                throw new SynchronizationException(StatusCode.HISTORY_TOO_SHALLOW);
            }

            return Evaluation.INCLUDE_AND_CONTINUE;
        }

        @Override
        protected ImmutableList<ObjectId> getParentsInternal(ObjectId commitId) {
            return source.getParents(commitId);
        }

        @Override
        protected boolean existsInDestination(ObjectId commitId) {
            return destination.objectExists(commitId);
        }
    }

    /**
     * CommitTraverser for transfering data to a shallow clone. This traverser will fetch data up to
     * the fetch limit. If no fetch limit is defined, one will be calculated when a commit is
     * fetched that I already have. The new fetch depth will be the depth from the starting commit
     * to beginning of the orphaned branch.
     */
    protected class ShallowCommitTraverser extends CommitTraverser {

        Optional<Integer> limit;

        private RepositoryWrapper source;

        private RepositoryWrapper destination;

        public ShallowCommitTraverser(RepositoryWrapper source, RepositoryWrapper destination,
                Optional<Integer> limit) {
            this.source = source;
            this.destination = destination;
            this.limit = limit;
        }

        @Override
        protected Evaluation evaluate(CommitNode commitNode) {
            if (limit.isPresent() && commitNode.getDepth() > limit.get()) {
                return Evaluation.EXCLUDE_AND_PRUNE;
            } else if (!source.objectExists(commitNode.getObjectId())) {
                // remote history is shallow
                return Evaluation.EXCLUDE_AND_PRUNE;
            }

            boolean exists = destination.objectExists(commitNode.getObjectId());
            if (!limit.isPresent() && exists) {
                // calculate the new fetch limit
                limit = Optional.of(
                        destination.getDepth(commitNode.getObjectId()) + commitNode.getDepth() - 1);
            }
            if (exists) {
                return Evaluation.EXCLUDE_AND_CONTINUE;
            }
            return Evaluation.INCLUDE_AND_CONTINUE;
        }

        @Override
        protected ImmutableList<ObjectId> getParentsInternal(ObjectId commitId) {
            return source.getParents(commitId);
        }

        @Override
        protected boolean existsInDestination(ObjectId commitId) {
            return destination.objectExists(commitId);
        }
    };

    /**
     * CommitTraverser for synchronizing data between two full (non-shallow) repositories. The
     * traverser will copy data from the source to the destination until there is nothing left to
     * copy.
     */
    protected class FullCommitTraverser extends CommitTraverser {

        private RepositoryWrapper source;

        private RepositoryWrapper destination;

        public FullCommitTraverser(RepositoryWrapper source, RepositoryWrapper destination) {
            this.source = source;
            this.destination = destination;
        }

        @Override
        protected Evaluation evaluate(CommitNode commitNode) {
            if (destination.objectExists(commitNode.getObjectId())) {
                return Evaluation.EXCLUDE_AND_PRUNE;
            } else {
                return Evaluation.INCLUDE_AND_CONTINUE;
            }
        }

        @Override
        protected ImmutableList<ObjectId> getParentsInternal(ObjectId commitId) {
            return source.getParents(commitId);
        }

        @Override
        protected boolean existsInDestination(ObjectId commitId) {
            return destination.objectExists(commitId);
        }

    };

    /**
     * @return the {@link RepositoryWrapper} for this remote
     */
    public abstract RepositoryWrapper getRemoteWrapper();

    /**
     * Returns the appropriate commit traverser to use for the fetch operation.
     * 
     * @param fetchLimit the fetch limit to use
     * @return the {@link CommitTraverser} to use.
     */
    protected CommitTraverser getFetchTraverser(Repository local, Optional<Integer> fetchLimit) {

        RepositoryWrapper localWrapper = new LocalRepositoryWrapper(local);
        RepositoryWrapper remoteWrapper = getRemoteWrapper();

        CommitTraverser traverser;
        if (localWrapper.getRepoDepth().isPresent()) {
            traverser = new ShallowCommitTraverser(remoteWrapper, localWrapper, fetchLimit);
        } else if (remoteWrapper.getRepoDepth().isPresent()) {
            traverser = new ShallowFullCommitTraverser(remoteWrapper, localWrapper);
        } else {
            traverser = new FullCommitTraverser(remoteWrapper, localWrapper);
        }

        return traverser;
    }

    /**
     * Returns the appropriate commit traverser to use for the push operation.
     * 
     * @param remoteRef the remote ref to push to
     * @return the {@link CommitTraverser} to use.
     */
    protected CommitTraverser getPushTraverser(Repository local, Optional<Ref> remoteRef)
            throws SynchronizationException {

        RepositoryWrapper localWrapper = new LocalRepositoryWrapper(local);
        RepositoryWrapper remoteWrapper = getRemoteWrapper();

        CommitTraverser traverser;
        if (remoteWrapper.getRepoDepth().isPresent()) {
            Optional<Integer> pushDepth = Optional.absent();
            if (!remoteRef.isPresent()) {
                pushDepth = remoteWrapper.getRepoDepth();
            }
            traverser = new ShallowCommitTraverser(localWrapper, remoteWrapper, pushDepth);
        } else if (local.getDepth().isPresent()) {
            traverser = new ShallowFullCommitTraverser(localWrapper, remoteWrapper);
        } else {
            traverser = new FullCommitTraverser(localWrapper, remoteWrapper);
        }

        return traverser;
    }

    @Override
    public void pushNewData(Repository local, Ref ref, ProgressListener progress)
            throws SynchronizationException {
        pushNewData(local, ref, ref.getName(), progress);
    }

    /**
     * Determine if it is safe to push to the remote repository.
     * 
     * @param ref the ref to push
     * @param remoteRefOpt the ref to push to
     * @throws SynchronizationException if its not safe or possible to push to the given remote ref
     *         (see {@link StatusCode} for the possible reasons)
     */
    protected void checkPush(Repository local, Ref ref, Optional<Ref> remoteRefOpt)
            throws SynchronizationException {
        if (!remoteRefOpt.isPresent()) {
            return;// safe to push
        }
        final Ref remoteRef = remoteRefOpt.get();
        if (remoteRef instanceof SymRef) {
            throw new SynchronizationException(StatusCode.CANNOT_PUSH_TO_SYMBOLIC_REF);
        }
        final ObjectId remoteObjectId = remoteRef.getObjectId();
        final ObjectId localObjectId = ref.getObjectId();
        if (remoteObjectId.equals(localObjectId)) {
            // The branches are equal, no need to push.
            throw new SynchronizationException(StatusCode.NOTHING_TO_PUSH);
        } else if (local.blobExists(remoteObjectId)) {
            Optional<ObjectId> ancestor = local.command(FindCommonAncestor.class)
                    .setLeftId(remoteObjectId).setRightId(localObjectId).call();
            if (!ancestor.isPresent()) {
                // There is no common ancestor, a push will overwrite history
                throw new SynchronizationException(StatusCode.REMOTE_HAS_CHANGES);
            } else if (ancestor.get().equals(localObjectId)) {
                // My last commit is the common ancestor, the remote already has my data.
                throw new SynchronizationException(StatusCode.NOTHING_TO_PUSH);
            } else if (!ancestor.get().equals(remoteObjectId)) {
                // The remote branch's latest commit is not my ancestor, a push will cause a
                // loss of history.
                throw new SynchronizationException(StatusCode.REMOTE_HAS_CHANGES);
            }
        } else if (!remoteObjectId.isNull()) {
            // The remote has data that I do not, a push will cause this data to be lost.
            throw new SynchronizationException(StatusCode.REMOTE_HAS_CHANGES);
        }
    }

}
