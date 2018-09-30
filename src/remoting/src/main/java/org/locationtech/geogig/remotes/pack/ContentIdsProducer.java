package org.locationtech.geogig.remotes.pack;

import static org.locationtech.geogig.model.RevTree.EMPTY;
import static org.locationtech.geogig.model.RevTree.EMPTY_TREE_ID;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.NodeOrdering;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.QuadTreeBuilder;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.remotes.internal.Deduplicator;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Preconditions;

class ContentIdsProducer
        implements java.util.function.Consumer<ObjectId>, Runnable {

    public static ContentIdsProducer forCommits(ObjectStore source,
            List<ObjectId[]> diffTreeIds, Deduplicator deduplicator,
            ObjectReporter objectReport) {

        boolean reportFeatures = true;
        return new ContentIdsProducer(source, diffTreeIds, deduplicator, objectReport,
                reportFeatures);
    }

    public static ContentIdsProducer forIndex(IndexInfo indexInfo, IndexDatabase sourceStore,
            List<ObjectId[]> treeIds, Deduplicator deduplicator, ObjectReporter objectReport) {

        Envelope maxBounds = IndexInfo.getMaxBounds(indexInfo);
        Preconditions.checkNotNull(maxBounds);
        NodeOrdering diffNodeOrdering = QuadTreeBuilder.nodeOrdering(maxBounds);

        boolean reportFeatures = false;
        ContentIdsProducer producer = new ContentIdsProducer(sourceStore, treeIds, deduplicator,
                objectReport, reportFeatures);
        producer.diffOrder = diffNodeOrdering;
        return producer;
    }

    private final ObjectStore source;

    private final Deduplicator deduplicator;

    private LinkedBlockingQueue<ObjectId> queue = new LinkedBlockingQueue<>(1_000_000);

    private final List<ObjectId[]> roots;

    private final ObjectReporter objectReport;

    private final boolean reportFeatures;

    private NodeOrdering diffOrder = CanonicalNodeOrder.INSTANCE;

    private ContentIdsProducer(ObjectStore source, List<ObjectId[]> diffTreeIds,
            Deduplicator deduplicator, ObjectReporter objectReport, boolean reportFeatures) {
        this.source = source;
        this.roots = diffTreeIds;
        this.deduplicator = deduplicator;
        this.objectReport = objectReport;
        this.reportFeatures = reportFeatures;
    }

    public Iterator<ObjectId> iterator() {
        Iterator<ObjectId> objectIds = new BlockingIterator<ObjectId>(queue, ObjectId.NULL);
        return objectIds;
    }

    public @Override void run() {
        for (ObjectId[] oldNewTreeId : this.roots) {
            ObjectId leftRootId = oldNewTreeId[0];
            ObjectId rightRootId = oldNewTreeId[1];
            visitPreorder(leftRootId, rightRootId, deduplicator, objectReport, this);
        }
        accept(ObjectId.NULL);// terminal token
    }

    public @Override void accept(ObjectId id) {
        try {
            queue.put(id);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void visitPreorder(final ObjectId leftTreeId, final ObjectId rightTreeId,
            Deduplicator deduplicator, ObjectReporter progress, Consumer<ObjectId> consumer) {
        if (deduplicator.isDuplicate(rightTreeId)) {
            return;
        }

        final ObjectStore source = this.source;
        final RevTree left = EMPTY_TREE_ID.equals(leftTreeId) ? EMPTY
                : source.getTree(leftTreeId);
        final RevTree right = EMPTY_TREE_ID.equals(rightTreeId) ? EMPTY
                : source.getTree(rightTreeId);

        PreOrderDiffWalk walk = new PreOrderDiffWalk(left, right, source, source);
        walk.nodeOrder(this.diffOrder);

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
                if (reportFeatures) {
                    if (right != null && consume(right.getObjectId())) {
                        progress.addFeature();
                        addMetadataId(progress, right);
                    }
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