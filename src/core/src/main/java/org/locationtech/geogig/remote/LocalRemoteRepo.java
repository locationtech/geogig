/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remote;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.locationtech.geogig.api.RevObject.TYPE.FEATURE;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.Bounded;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.ForEachRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;
import org.locationtech.geogig.api.plumbing.diff.PostOrderDiffWalk;
import org.locationtech.geogig.api.plumbing.diff.PostOrderDiffWalk.Consumer;
import org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.api.porcelain.SynchronizationException;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryInitializer;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * An implementation of a remote repository that exists on the local machine.
 * 
 * @see IRemoteRepo
 */
class LocalRemoteRepo extends AbstractRemoteRepo {

    private Repository remoteRepository;

    private URI remoteRepoURI;

    /**
     * Constructs a new {@code LocalRemoteRepo} with the given parameters.
     * 
     * @param injector the Guice injector for the new repository
     * @param remoteRepoURI the location of the remote repository
     */
    public LocalRemoteRepo(URI remoteRepoURI, Repository localRepository) {
        super(localRepository);
        this.remoteRepoURI = remoteRepoURI;
    }

    LocalRemoteRepo(Repository remoteRepo, Repository localRepository) {
        super(localRepository);
        this.remoteRepository = remoteRepo;
    }

    /**
     * @param geogig manually set a geogig for this remote repository
     */
    @VisibleForTesting
    void setRepository(Repository remoteRepo) {
        this.remoteRepository = remoteRepo;
    }

    @Override
    public void open() throws RepositoryConnectionException {
        if (remoteRepository == null) {
            remoteRepository = RepositoryInitializer.load(remoteRepoURI);
        }
    }

    @Override
    public void close() {
        if (remoteRepository != null) {
            try {
                remoteRepository.close();
            } finally {
                remoteRepository = null;
            }
        }
    }

    /**
     * @return the remote's HEAD {@link Ref}.
     */
    @Override
    public Ref headRef() {
        final Optional<Ref> currHead = remoteRepository.command(RefParse.class).setName(Ref.HEAD)
                .call();
        Preconditions.checkState(currHead.isPresent(), "Remote repository has no HEAD.");
        if (currHead.get().getObjectId().equals(ObjectId.NULL)) {
            return null;
        } else {
            return currHead.get();
        }
    }

    /**
     * List the remote's {@link Ref refs}.
     * 
     * @param getHeads whether to return refs in the {@code refs/heads} namespace
     * @param getTags whether to return refs in the {@code refs/tags} namespace
     * @return an immutable set of refs from the remote
     */
    @Override
    public ImmutableSet<Ref> listRefs(final boolean getHeads, final boolean getTags) {
        Predicate<Ref> filter = new Predicate<Ref>() {
            @Override
            public boolean apply(Ref input) {
                if (input.getObjectId().equals(ObjectId.NULL)) {
                    return false;
                }
                boolean keep = false;
                if (getHeads) {
                    keep = input.getName().startsWith(Ref.HEADS_PREFIX);
                }
                if (getTags) {
                    keep = keep || input.getName().startsWith(Ref.TAGS_PREFIX);
                }
                return keep;
            }
        };
        return remoteRepository.command(ForEachRef.class).setFilter(filter).call();
    }

    /**
     * Fetch all new objects from the specified {@link Ref} from the remote.
     * 
     * @param ref the remote ref that points to new commit data
     * @param fetchLimit the maximum depth to fetch
     */
    @Override
    public void fetchNewData(Ref ref, Optional<Integer> fetchLimit, ProgressListener progress) {

        CommitTraverser traverser = getFetchTraverser(fetchLimit);

        try {
            progress.setDescription("Fetching objects from " + ref.getName());
            progress.setProgress(0);
            traverser.traverse(ref.getObjectId());
            List<ObjectId> toSend = new LinkedList<ObjectId>(traverser.commits);
            Collections.reverse(toSend);// send oldest commits first
            for (ObjectId newHeadId : toSend) {
                walkHead(newHeadId, true, progress);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Throwables.propagate(e);
        }
    }

    /**
     * Push all new objects from the specified {@link Ref} to the given refspec.
     * 
     * @param ref the local ref that points to new commit data
     * @param refspec the refspec to push to
     */
    @Override
    public void pushNewData(final Ref ref, final String refspec, final ProgressListener progress)
            throws SynchronizationException {

        Optional<Ref> remoteRef = remoteRepository.command(RefParse.class).setName(refspec).call();
        remoteRef = remoteRef.or(remoteRepository.command(RefParse.class)
                .setName(Ref.TAGS_PREFIX + refspec).call());
        checkPush(ref, remoteRef);

        CommitTraverser traverser = getPushTraverser(remoteRef);

        traverser.traverse(ref.getObjectId());
        progress.setDescription("Uploading objects to " + refspec);
        progress.setProgress(0);
        while (!traverser.commits.isEmpty()) {
            ObjectId commitId = traverser.commits.pop();
            walkHead(commitId, false, progress);
        }

        String nameToSet = remoteRef.isPresent() ? remoteRef.get().getName() : Ref.HEADS_PREFIX
                + refspec;

        Ref updatedRef = remoteRepository.command(UpdateRef.class).setName(nameToSet)
                .setNewValue(ref.getObjectId()).call().get();

        Ref remoteHead = headRef();
        if (remoteHead instanceof SymRef) {
            if (((SymRef) remoteHead).getTarget().equals(updatedRef.getName())) {
                remoteRepository.command(UpdateSymRef.class).setName(Ref.HEAD)
                        .setNewValue(ref.getName()).call();
                RevCommit commit = remoteRepository.getCommit(ref.getObjectId());
                remoteRepository.workingTree().updateWorkHead(commit.getTreeId());
                remoteRepository.index().updateStageHead(commit.getTreeId());
            }
        }
    }

    /**
     * Delete the given refspec from the remote repository.
     * 
     * @param refspec the refspec to delete
     */
    @Override
    public Optional<Ref> deleteRef(String refspec) {
        Optional<Ref> deletedRef = remoteRepository.command(UpdateRef.class).setName(refspec)
                .setDelete(true).call();
        return deletedRef;
    }

    protected void walkHead(final ObjectId newHeadId, final boolean fetch,
            final ProgressListener progress) {

        Repository from = localRepository;
        Repository to = remoteRepository;
        if (fetch) {
            Repository tmp = to;
            to = from;
            from = tmp;
        }
        final ObjectDatabase fromDb = from.objectDatabase();
        final ObjectDatabase toDb = to.objectDatabase();

        final RevObject object = fromDb.get(newHeadId);

        RevCommit commit = null;
        RevTag tag = null;

        if (object.getType().equals(TYPE.COMMIT)) {
            commit = (RevCommit) object;
        } else if (object.getType().equals(TYPE.TAG)) {
            tag = (RevTag) object;
            commit = fromDb.getCommit(tag.getCommitId());
        }
        if (commit != null) {
            final RevTree newTree = fromDb.getTree(commit.getTreeId());
            List<ObjectId> parentIds = new ArrayList<>(commit.getParentIds());
            if (parentIds.isEmpty()) {
                parentIds.add(ObjectId.NULL);
            }
            RevTree oldTree = RevTree.EMPTY;
            // the diff against each parent is not working. For some reason some buckets that are
            // equal between the two ends of the comparison never get transferred (at some point
            // they shouldn't be equal and so the Consumer notified of it/them). Yet with the target
            // database exists check for each tree the performance is good enough.
            // for (ObjectId parentId : parentIds) {
            // if (!parentId.isNull()) {
            // RevCommit parent = fromDb.getCommit(parentId);
            // oldTree = fromDb.getTree(parent.getTreeId());
            // }
            copyNewObjects(oldTree, newTree, fromDb, toDb, progress);
            // }
            Preconditions.checkState(toDb.exists(newTree.getId()),
                    "tree %s wasn't copied to the target database", newTree.getId());

            toDb.put(commit);
        }
        if (tag != null) {
            toDb.put(tag);
        }
    }

    private void copyNewObjects(RevTree oldTree, RevTree newTree, final ObjectDatabase fromDb,
            final ObjectDatabase toDb, final ProgressListener progress) {
        checkNotNull(oldTree);
        checkNotNull(newTree);
        checkNotNull(fromDb);
        checkNotNull(toDb);
        checkNotNull(progress);

        // the diff walk uses fromDb as both left and right data source since we're comparing what
        // we have in the "origin" database against trees on the same repository
        PostOrderDiffWalk diffWalk = new PostOrderDiffWalk(oldTree, newTree, fromDb, fromDb);

        // holds object ids that need to be copied to the target db. Pruned when it reaches a
        // threshold.
        final Set<ObjectId> ids = Sets.newConcurrentHashSet();

        // This filter further refines the post order diff walk by making it ignore trees/buckets
        // that are already present in the target db
        Predicate<Bounded> filter = new Predicate<Bounded>() {

            @Override
            public boolean apply(@Nullable Bounded b) {
                if (b == null) {
                    return false;
                }

                if (b instanceof NodeRef && FEATURE.equals(((NodeRef) b).getType())) {
                    // check of existence of trees only. For features the diff filtering is good
                    // enough and checking for existence on each feature would be killer
                    // performance wise
                    return true;
                }

                final ObjectId id = b.getObjectId();
                boolean exists = !progress.isCanceled() && (ids.contains(id) || toDb.exists(id));
                return !exists;
            }
        };

        // receives notifications of feature/bucket/tree diffs. Only interested in the "new"/right
        // side of the comparisons
        Consumer consumer = new Consumer() {
            final int bulkSize = 10_000;

            @Override
            public void feature(@Nullable NodeRef left, NodeRef right) {
                // add(left);
                add(right);
            }

            @Override
            public void tree(@Nullable NodeRef left, NodeRef right) {
                // add(left);
                add(right);
            }

            private void add(@Nullable NodeRef node) {
                if (node == null) {
                    return;
                }
                ObjectId metadataId = node.getMetadataId();
                synchronized (ids) {
                    ids.add(node.getObjectId());
                    if (!metadataId.isNull()) {
                        ids.add(metadataId);
                    }
                }
                checkLimitAndCopy();
            }

            @Override
            public void bucket(NodeRef lparent, NodeRef rparent, BucketIndex bucketIndex,
                    @Nullable Bucket left, Bucket right) {
                // if (left != null) {
                // ids.add(left.getObjectId());
                // }
                if (right != null) {
                    synchronized (ids) {
                        ids.add(right.getObjectId());
                    }
                }
                checkLimitAndCopy();
            }

            private void checkLimitAndCopy() {
                // double check lock on ids to reduce contention when pruning it as this method can
                // be called from several concurrent threads from inside PreOrderDiffWalk
                if (ids.size() >= bulkSize) {
                    synchronized (ids) {
                        if (ids.size() >= bulkSize) {
                            Set<ObjectId> copyIds = Sets.newHashSet(ids);
                            copy(copyIds, fromDb, toDb, progress);
                            ids.clear();
                        }
                    }
                }
            }
        };
        diffWalk.walk(filter, consumer);
        // copy remaining objects
        copy(ids, fromDb, toDb, progress);
    }

    private void copy(Set<ObjectId> ids, ObjectStore from, ObjectStore to,
            final ProgressListener progress) {
        if (ids.isEmpty()) {
            return;
        }
        BulkOpListener countingListener = new BulkOpListener() {
            @Override
            public void inserted(ObjectId object, @Nullable Integer storageSizeBytes) {
                progress.setProgress(progress.getProgress() + 1);
            }
        };
        to.putAll(from.getAll(ids), countingListener);
    }

    /**
     * @return the {@link RepositoryWrapper} for this remote
     */
    @Override
    public RepositoryWrapper getRemoteWrapper() {
        return new LocalRepositoryWrapper(remoteRepository);
    }

    /**
     * Gets the depth of the remote repository.
     * 
     * @return the depth of the repository, or {@link Optional#absent()} if the repository is not
     *         shallow
     */
    @Override
    public Optional<Integer> getDepth() {
        return remoteRepository.getDepth();
    }
}
