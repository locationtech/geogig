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

import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.WriteTree;
import org.locationtech.geogig.remotes.SynchronizationException;
import org.locationtech.geogig.remotes.SynchronizationException.StatusCode;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;

/**
 * Abstract base implementation for mapped (sparse) clone.
 */
public abstract class AbstractMappedRemoteRepo implements IRemoteRepo {

    public static String PLACEHOLDER_COMMIT_MESSAGE = "Placeholder Sparse Commit";


    private final Remote remote;

    protected AbstractMappedRemoteRepo(Remote remote) {
        Preconditions.checkNotNull(remote);
        this.remote = remote;
    }

    public @Override Remote getInfo() {
        return remote;
    }

    /**
     * CommitTraverser for gathering all of the commits that I need to fetch.
     */
    protected class FetchCommitGatherer extends CommitTraverser {

        RepositoryWrapper source;

        Repository destination;

        public FetchCommitGatherer(RepositoryWrapper source, Repository destination) {
            this.source = source;
            this.destination = destination;
        }

        @Override
        protected Evaluation evaluate(CommitNode commitNode) {
            if (destination.graphDatabase().exists(commitNode.getObjectId())) {
                return Evaluation.EXCLUDE_AND_PRUNE;
            }
            return Evaluation.INCLUDE_AND_CONTINUE;
        }

        @Override
        protected ImmutableList<ObjectId> getParentsInternal(ObjectId commitId) {
            return source.getParents(commitId);
        }

        @Override
        protected boolean existsInDestination(ObjectId commitId) {
            return destination.graphDatabase().exists(commitId);
        }

    };

    /**
     * CommitTraverser for gathering all of the commits that I need to push.
     */
    protected class PushCommitGatherer extends CommitTraverser {

        Repository source;

        public PushCommitGatherer(Repository source) {
            this.source = source;
        }

        @Override
        protected Evaluation evaluate(CommitNode commitNode) {
            if (!source.graphDatabase().getMapping(commitNode.getObjectId())
                    .equals(ObjectId.NULL)) {
                return Evaluation.EXCLUDE_AND_PRUNE;
            }
            return Evaluation.INCLUDE_AND_CONTINUE;
        }

        @Override
        protected ImmutableList<ObjectId> getParentsInternal(ObjectId commitId) {
            return source.graphDatabase().getParents(commitId);
        }

        @Override
        protected boolean existsInDestination(ObjectId commitId) {
            // If the commit has not been mapped, it hasn't been pushed to the remote yet
            return !source.graphDatabase().getMapping(commitId).equals(ObjectId.NULL);
        }

    };

    /**
     * @return the {@link RepositoryWrapper} for this remote
     */
    protected abstract RepositoryWrapper getRemoteWrapper();

    @Override
    public final void fetchNewData(Repository local, Ref ref, Optional<Integer> fetchLimit,
            ProgressListener progress) {
        Preconditions.checkState(!fetchLimit.isPresent(), "A sparse clone cannot be shallow.");
        FetchCommitGatherer gatherer = new FetchCommitGatherer(getRemoteWrapper(), local);

        gatherer.traverse(ref.getObjectId());
        Stack<ObjectId> needed = gatherer.commits;
        while (!needed.empty()) {
            ObjectId commitId = needed.pop();
            // If the last commit is empty, add it anyways to preserve parentage of new commits.
            boolean allowEmpty = needed.isEmpty();
            fetchSparseCommit(local, commitId, allowEmpty);
        }

    }

    /**
     * This function takes all of the changes introduced by the specified commit and filters them
     * based on the repository filter. It then uses the filtered results to construct a new commit
     * that is the descendant of commits that the original's parents are mapped to.
     * 
     * @param commitId the commit id of the original, non-sparse commit
     * @param allowEmpty allow the function to create an empty sparse commit
     */
    private void fetchSparseCommit(final Repository local, final ObjectId commitId,
            final boolean allowEmpty) {

        Optional<RevObject> object = getObject(commitId);
        if (object.isPresent() && object.get().getType().equals(TYPE.COMMIT)) {
            RevCommit commit = (RevCommit) object.get();

            try (FilteredDiffIterator changes = getFilteredChanges(local, commit)) {
                GraphDatabase graphDatabase = local.graphDatabase();
                ObjectStore objectDatabase = local.objectDatabase();
                graphDatabase.put(commit.getId(), commit.getParentIds());

                RevTree rootTree = RevTree.EMPTY;

                if (commit.getParentIds().size() > 0) {
                    // Map this commit to the last "sparse" commit in my ancestry
                    ObjectId mappedCommit = graphDatabase.getMapping(commit.getParentIds().get(0));
                    graphDatabase.map(commit.getId(), mappedCommit);
                    Optional<ObjectId> treeId = local.command(ResolveTreeish.class)
                            .setTreeish(mappedCommit).call();
                    if (treeId.isPresent()) {
                        rootTree = local.getTree(treeId.get());
                    }

                } else {
                    graphDatabase.map(commit.getId(), ObjectId.NULL);
                }

                AutoCloseableIterator<DiffEntry> it = AutoCloseableIterator.filter(changes,
                        new Predicate<DiffEntry>() {
                            @Override
                            public boolean apply(DiffEntry e) {
                                return true;
                            }
                        });

                if (it.hasNext()) {
                    // Create new commit
                    WriteTree writeTree = local.command(WriteTree.class)
                            .setOldRoot(Suppliers.ofInstance(rootTree)).setDiffSupplier(
                                    Suppliers.ofInstance((AutoCloseableIterator<DiffEntry>) it));

                    ObjectId newTreeId = writeTree.call();

                    RevCommitBuilder builder = RevCommit.builder().init(commit);
                    List<ObjectId> newParents = new LinkedList<ObjectId>();
                    for (ObjectId parentCommitId : commit.getParentIds()) {
                        newParents.add(graphDatabase.getMapping(parentCommitId));
                    }
                    builder.parentIds(newParents);
                    builder.treeId(newTreeId);

                    RevCommit mapped = builder.build();
                    objectDatabase.put(mapped);

                    if (changes.wasFiltered()) {
                        graphDatabase.setProperty(mapped.getId(), GraphDatabase.SPARSE_FLAG,
                                "true");
                    }

                    graphDatabase.map(mapped.getId(), commit.getId());
                    // Replace the old mapping with the new commit Id.
                    graphDatabase.map(commit.getId(), mapped.getId());
                } else if (allowEmpty) {
                    RevCommitBuilder builder = RevCommit.builder().init(commit);
                    List<ObjectId> newParents = new LinkedList<>();
                    for (ObjectId parentCommitId : commit.getParentIds()) {
                        newParents.add(graphDatabase.getMapping(parentCommitId));
                    }
                    builder.parentIds(newParents);
                    builder.treeId(rootTree.getId());
                    builder.message(PLACEHOLDER_COMMIT_MESSAGE);

                    RevCommit mapped = builder.build();
                    objectDatabase.put(mapped);

                    graphDatabase.setProperty(mapped.getId(), GraphDatabase.SPARSE_FLAG, "true");

                    graphDatabase.map(mapped.getId(), commit.getId());
                    // Replace the old mapping with the new commit Id.
                    graphDatabase.map(commit.getId(), mapped.getId());
                } else {
                    // Mark the mapped commit as sparse, since it wont have these changes
                    graphDatabase.setProperty(graphDatabase.getMapping(commit.getId()),
                            GraphDatabase.SPARSE_FLAG, "true");
                }
            }
        }
    }

    /**
     * Retrieves an object with the specified id from the remote.
     * 
     * @param objectId the object to get
     * @return the fetched object
     */
    protected abstract Optional<RevObject> getObject(ObjectId objectId);

    /**
     * Gets all of the changes from the target commit that should be applied to the sparse clone.
     * 
     * @param commit the commit to get changes from
     * @return an iterator for changes that match the repository filter
     */
    protected abstract FilteredDiffIterator getFilteredChanges(final Repository local,
            RevCommit commit);

    @Override
    public void pushNewData(Repository local, Ref ref, ProgressListener progress)
            throws SynchronizationException {
        pushNewData(local, ref, ref.getName(), progress);
    }

    @Override
    public void pushNewData(Repository local, Ref ref, String refspec, ProgressListener progress)
            throws SynchronizationException {
        Optional<Ref> remoteRef = getRemoteRef(refspec);
        checkPush(local, ref, remoteRef);
        beginPush();

        PushCommitGatherer gatherer = new PushCommitGatherer(local);

        gatherer.traverse(ref.getObjectId());
        Stack<ObjectId> needed = gatherer.commits;

        while (!needed.isEmpty()) {
            ObjectId commitToPush = needed.pop();

            pushSparseCommit(local, commitToPush);
        }

        ObjectId newCommitId = local.graphDatabase().getMapping(ref.getObjectId());

        ObjectId originalRemoteRefValue = ObjectId.NULL;
        if (remoteRef.isPresent()) {
            originalRemoteRefValue = remoteRef.get().getObjectId();
        }

        endPush(refspec, newCommitId, originalRemoteRefValue.toString());
    }

    /**
     * Gets the remote ref that matches the provided ref spec.
     * 
     * @param refspec the refspec to parse
     * @return the matching {@link Ref} or {@link Optional#absent()} if the ref could not be found
     */
    protected abstract Optional<Ref> getRemoteRef(String refspec);

    /**
     * Perform pre-push actions.
     */
    protected void beginPush() {
        // do nothing
    }

    /**
     * Perform post-push actions, this includes verification that the remote wasn't changed while we
     * were pushing.
     * 
     * @param refspec the refspec that we are pushing to
     * @param newCommitId the new commit id
     * @param originalRefValue the original value of the ref before pushing
     */
    protected void endPush(String refspec, ObjectId newCommitId, String originalRefValue) {
        updateRemoteRef(refspec, newCommitId, false);
    }

    /**
     * Updates the remote ref that matches the given refspec.
     * 
     * @param refspec the ref to update
     * @param commitId the new value of the ref
     * @param delete if true, the remote ref will be deleted
     * @return the updated ref, or {@link Optional#absent() absent} if it didn't exist
     */
    protected abstract Optional<Ref> updateRemoteRef(String refspec, ObjectId commitId,
            boolean delete);

    /**
     * Pushes a sparse commit to a remote repository and updates all mappings.
     * 
     * @param commitId the commit to push
     */
    protected abstract void pushSparseCommit(final Repository local, ObjectId commitId);

    /**
     * Determine if it is safe to push to the remote repository.
     * 
     * @param ref the ref to push
     * @param remoteRef the ref to push to
     * @throws SynchronizationException
     */
    protected void checkPush(Repository local, Ref ref, Optional<Ref> remoteRef)
            throws SynchronizationException {
        if (remoteRef.isPresent()) {
            if (remoteRef.get() instanceof SymRef) {
                throw new SynchronizationException(StatusCode.CANNOT_PUSH_TO_SYMBOLIC_REF);
            }
            ObjectId mappedId = local.graphDatabase().getMapping(remoteRef.get().getObjectId());
            if (mappedId.equals(ref.getObjectId())) {
                // The branches are equal, no need to push.
                throw new SynchronizationException(StatusCode.NOTHING_TO_PUSH);
            } else if (local.blobExists(mappedId)) {
                Optional<ObjectId> ancestor = local.command(FindCommonAncestor.class)
                        .setLeftId(mappedId).setRightId(ref.getObjectId()).call();
                if (!ancestor.isPresent()) {
                    // There is no common ancestor, a push will overwrite history
                    throw new SynchronizationException(StatusCode.REMOTE_HAS_CHANGES);
                } else if (ancestor.get().equals(ref.getObjectId())) {
                    // My last commit is the common ancestor, the remote already has my data.
                    throw new SynchronizationException(StatusCode.NOTHING_TO_PUSH);
                } else if (!ancestor.get().equals(mappedId)) {
                    // The remote branch's latest commit is not my ancestor, a push will cause a
                    // loss of history.
                    throw new SynchronizationException(StatusCode.REMOTE_HAS_CHANGES);
                }
            } else {
                // The remote has data that I do not, a push will cause this data to be lost.
                throw new SynchronizationException(StatusCode.REMOTE_HAS_CHANGES);
            }
        }
    }
}
