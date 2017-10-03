/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.pack;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.locationtech.geogig.model.RevTree.EMPTY;
import static org.locationtech.geogig.model.RevTree.EMPTY_TREE_ID;
import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.internal.DeduplicationService;
import org.locationtech.geogig.remotes.internal.Deduplicator;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

class PackImpl implements Pack {

    private final Repository source;

    /**
     * All missing commits, by {@link RefRequest}, with no duplication (i.e. if a commit is part of
     * two missing ref histories, it'll be present only on the first one, according to the map's
     * iteration order)
     */
    private final LinkedHashMap<RefRequest, List<RevCommit>> missingCommits;

    private final List<RevTag> missingTags;

    protected PackImpl(Repository source, List<RevTag> missingTags,
            LinkedHashMap<RefRequest, List<RevCommit>> missingCommits) {
        checkNotNull(source);
        checkNotNull(missingTags);
        checkNotNull(missingCommits);

        this.source = source;
        this.missingTags = missingTags;
        this.missingCommits = missingCommits;
    }

    public @Override List<RefDiff> applyTo(PackProcessor target, ProgressListener progress) {
        checkNotNull(target);
        checkNotNull(progress);

        List<RefDiff> result = new ArrayList<>();
        List<RefRequest> reqs = Lists.newArrayList(missingCommits.keySet());

        Deduplicator deduplicator = DeduplicationService.create();
        try {
            for (RefRequest req : reqs) {
                RefDiff changedRef = applyToPreOrder(target, req, deduplicator, progress);
                checkNotNull(changedRef);
                result.add(changedRef);
            }
        } finally {
            deduplicator.release();
        }

        List<RevTag> tags = this.missingTags;
        target.putAll(tags.iterator(), NOOP_LISTENER);
        return result;
    }

    private static class AppliedProgressIndicator implements Function<ProgressListener, String> {

        private final ObjectReporter processed;

        AppliedProgressIndicator(ObjectReporter processed) {
            this.processed = processed;
        }

        public @Override String apply(final ProgressListener unused) {
            return processed.toString();
        }
    }

    private RefDiff applyToPreOrder(PackProcessor target, RefRequest req, Deduplicator deduplicator,
            ProgressListener progress) {

        progress.started();

        progress.setDescription("Applying changes of " + req.name);
        ObjectReporter objectReport = new ObjectReporter(progress);

        // back up current progress indicator
        final Function<ProgressListener, String> defaultProgressIndicator;
        defaultProgressIndicator = progress.progressIndicator();
        // set our custom progress indicator
        progress.setProgressIndicator(new AppliedProgressIndicator(objectReport));

        final List<RevCommit> commits = missingCommits.get(req);
        checkNotNull(commits);

        final ObjectDatabase sourceStore = source.objectDatabase();
        final Producer producer = new Producer(sourceStore, target, commits, deduplicator,
                objectReport);

        final ExecutorService producerThread = Executors.newSingleThreadExecutor();
        try {
            producerThread.submit(producer);
            Iterator<ObjectId> missingContentIds = producer.iterator();

            Iterator<RevObject> allObjects;
            {
                Iterator<RevObject> missingContents;
                Iterator<RevCommit> commitsIterator;
                missingContents = source.objectDatabase().getAll(() -> missingContentIds);
                commitsIterator = Iterators.filter(commits.iterator(), (c) -> {
                    objectReport.addCommit();
                    return true;
                });

                allObjects = Iterators.concat(missingContents, commitsIterator);
            }
            final Stopwatch sw = Stopwatch.createStarted();

            target.putAll(allObjects, objectReport);
            progress.complete();
            if (objectReport.total.get() > 0) {
                progress.started();
                String description = String.format("Objects inserted: %,d, repeated: %,d, time: %s",
                        objectReport.inserted(), objectReport.found(), sw.stop());
                progress.setDescription(description);

                progress.complete();
            }
        } finally {
            producerThread.shutdownNow();
            // restore previous progress indicator
            progress.setProgressIndicator(defaultProgressIndicator);
        }

        Ref oldRef = req.have.isPresent() ? new Ref(req.name, req.have.get()) : null;
        Ref newRef = new Ref(req.name, req.want);
        RefDiff changedRef = new RefDiff(oldRef, newRef);

        return changedRef;
    }

    private class Producer implements Consumer<ObjectId>, Runnable {

        private final ObjectStore source;

        private final PackProcessor target;

        private final Deduplicator deduplicator;

        private LinkedBlockingQueue<ObjectId> queue = new LinkedBlockingQueue<>(1_000_000);

        private final List<RevCommit> commits;

        private final ObjectReporter objectReport;

        Producer(ObjectStore source, PackProcessor target, List<RevCommit> commits,
                Deduplicator deduplicator, ObjectReporter objectReport) {
            this.source = source;
            this.target = target;
            this.commits = commits;
            this.deduplicator = deduplicator;
            this.objectReport = objectReport;
        }

        public Iterator<ObjectId> iterator() {
            Iterator<ObjectId> objectIds = new BlockingIterator<ObjectId>(queue, ObjectId.NULL);
            return objectIds;
        }

        public @Override void run() {
            final Map<ObjectId, RevCommit> commitsById = Maps.uniqueIndex(commits,
                    (c) -> c.getId());

            for (RevCommit commit : commits) {
                List<ObjectId> parentIds = commit.getParentIds();
                if (parentIds.isEmpty()) {
                    parentIds = ImmutableList.of(ObjectId.NULL);
                }
                for (ObjectId parentId : parentIds) {
                    RevCommit parent = parentId.isNull() ? null
                            : Optional.fromNullable(commitsById.get(parentId))
                                    .or(() -> source.getCommit(parentId));

                    visitPreOrder(parent, commit, deduplicator, target, objectReport, this);
                }
            }

            accept(ObjectId.NULL);// terminal token
        }

        public @Override void accept(ObjectId id) {
            try {
                queue.put(id);
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw Throwables.propagate(e);
            }
        }

        private void visitPreOrder(@Nullable RevCommit parent, RevCommit commit,
                Deduplicator deduplicator, PackProcessor target, ObjectReporter progress,
                Consumer<ObjectId> consumer) {

            final ObjectId leftRootId = parent == null ? EMPTY_TREE_ID : parent.getTreeId();
            final ObjectId rightRootId = commit.getTreeId();

            if (deduplicator.isDuplicate(rightRootId)) {
                return;
            }

            final ObjectStore source = this.source;
            final RevTree left = EMPTY_TREE_ID.equals(leftRootId) ? EMPTY
                    : source.getTree(leftRootId);
            final RevTree right = EMPTY_TREE_ID.equals(rightRootId) ? EMPTY
                    : source.getTree(rightRootId);

            PreOrderDiffWalk walk = new PreOrderDiffWalk(left, right, source, source, true);

            /**
             * A diff consumer that reports only the new objects, with deduplication
             */
            walk.walk(new PreOrderDiffWalk.AbstractConsumer() {

                /**
                 * Checks whether the {@code left} has already been compared against {@code right}
                 * 
                 * @return {@code true} if the pair of ids weren't already visited and are marked
                 *         visited as result to this call, {@code false} if this pair was already
                 *         visited.
                 */
                private boolean visitPair(ObjectId left, ObjectId right) {
                    return deduplicator.visit(left, right);
                }

                /**
                 * Calls {@link Consumer#accept consumer.accept(ObjectId}} with this id if it wasn't
                 * already visited and returns {@code true}, or {@code false} if the id was already
                 * visited and hence consumed
                 */
                private boolean consume(ObjectId objectId) {
                    if (deduplicator.visit(objectId)) {
                        consumer.accept(objectId);
                        return true;
                    }
                    return false;
                }

                public @Override boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
                    if (right != null && consume(right.getObjectId())) {
                        progress.addFeature();
                        addMetadataId(progress, right);
                    }
                    return true;
                }

                public @Override boolean tree(@Nullable NodeRef left, @Nullable NodeRef right) {
                    if (right == null) {
                        // not interested in purely deleted content
                        return false;
                    }
                    // which "old" object the "new" bucket is being compared against.
                    ObjectId leftId = left == null ? RevTree.EMPTY_TREE_ID : left.getObjectId();
                    boolean r = addTree(progress, leftId, right);
                    return r;
                }

                public @Override boolean bucket(NodeRef leftParent, NodeRef rightParent,
                        BucketIndex bucketIndex, @Nullable Bucket left, @Nullable Bucket right) {
                    if (rightParent == null || right == null) {
                        // not interested in purely deleted content
                        return false;
                    }

                    // which "old" object the "new" bucket is being compared against.
                    final ObjectId leftId = bucketIndex.left().getId();
                    boolean r = addBucket(progress, leftId, right);
                    return r;
                }

                private boolean addTree(ObjectReporter progress, ObjectId left, NodeRef right) {
                    if (visitPair(left, right.getObjectId())) {
                        if (consume(right.getObjectId())) {
                            progress.addTree();
                        }
                        addMetadataId(progress, right);
                        return true;
                    }
                    return false;
                }

                private void addMetadataId(ObjectReporter progress, NodeRef right) {
                    if (right.getNode().getMetadataId().isPresent()) {
                        ObjectId md = right.getNode().getMetadataId().get();
                        if (consume(md)) {
                            progress.addFeatureType();
                        }
                    }
                }

                private boolean addBucket(ObjectReporter progress, ObjectId left, Bucket right) {
                    Preconditions.checkNotNull(progress);
                    Preconditions.checkNotNull(left);
                    Preconditions.checkNotNull(right);
                    if (visitPair(left, right.getObjectId())) {
                        if (consume(right.getObjectId())) {
                            progress.addBucket();
                        }
                        return true;
                    }
                    return false;
                }
            });
        }
    }

}
