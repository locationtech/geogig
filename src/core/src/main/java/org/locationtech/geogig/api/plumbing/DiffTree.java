/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Bounded;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.diff.BoundsFilteringDiffConsumer;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry.ChangeType;
import org.locationtech.geogig.api.plumbing.diff.PathFilteringDiffConsumer;
import org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk.ForwardingConsumer;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Compares the content and metadata links of blobs found via two tree objects on the repository's
 * {@link ObjectDatabase}
 */
public class DiffTree extends AbstractGeoGigOp<Iterator<DiffEntry>>
        implements
        Supplier<Iterator<DiffEntry>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiffTree.class);

    private final List<String> pathFilters = Lists.newLinkedList();

    private ReferencedEnvelope boundsFilter;

    private ChangeType changeTypeFilter;

    private String oldRefSpec;

    private String newRefSpec;

    private boolean reportTrees;

    private boolean recursive;

    private Predicate<Bounded> customFilter;

    private Long limit;

    private ObjectId metadataId;

    private ObjectStore leftSource;

    private ObjectStore rightSource;

    private ObjectId newTreeId;

    private ObjectId oldTreeId;

    private boolean preserveIterationOrder = false;

    /**
     * Constructs a new instance of the {@code DiffTree} operation with the given parameters.
     */
    public DiffTree() {
        this.recursive = true;
    }

    /**
     * @param oldRefSpec the ref that points to the "old" version
     * @return {@code this}
     */
    public DiffTree setOldVersion(String oldRefSpec) {
        this.oldRefSpec = oldRefSpec;
        return this;
    }

    /**
     * @param newRefSpec the ref that points to the "new" version
     * @return {@code this}
     */
    public DiffTree setNewVersion(String newRefSpec) {
        this.newRefSpec = newRefSpec;
        return this;
    }

    /**
     * @param oldTreeId the {@link ObjectId} of the "old" tree
     * @return {@code this}
     */
    public DiffTree setOldTree(ObjectId oldTreeId) {
        this.oldTreeId = oldTreeId;
        return this;
    }

    /**
     * @param newTreeId the {@link ObjectId} of the "new" tree
     * @return {@code this}
     */
    public DiffTree setNewTree(ObjectId newTreeId) {
        this.newTreeId = newTreeId;
        return this;
    }

    /**
     * Note: Preserving the iteration order will disable parallel processing of the diff.
     * 
     * @param preserveIterationOorder whether or not to preserve the iteration order
     * @return {@code this}
     */
    public DiffTree setPreserveIterationOrder(boolean preserveIterationOrder) {
        this.preserveIterationOrder = preserveIterationOrder;
        return this;
    }

    /**
     * @param path the path filter to use during the diff operation, replaces any other filter
     *        previously set
     * @return {@code this}
     */
    public DiffTree setPathFilter(@Nullable String path) {
        if (path == null) {
            setPathFilter((List<String>) null);
        } else {
            setPathFilter(ImmutableList.of(path));
        }
        return this;
    }

    public DiffTree setPathFilter(@Nullable List<String> pathFitlers) {
        this.pathFilters.clear();
        if (pathFitlers != null) {
            this.pathFilters.addAll(pathFitlers);
        }
        return this;
    }

    public DiffTree setBoundsFilter(@Nullable ReferencedEnvelope bounds) {
        this.boundsFilter = bounds;
        return this;
    }

    public DiffTree setCustomFilter(@Nullable Predicate<Bounded> customFilter) {
        this.customFilter = customFilter;
        return this;
    }

    public DiffTree setChangeTypeFilter(@Nullable ChangeType changeType) {
        this.changeTypeFilter = changeType;
        return this;
    }

    /**
     * Implements {@link Supplier#get()} by delegating to {@link #call()}.
     */
    @Override
    public Iterator<DiffEntry> get() {
        return call();
    }

    /**
     * Finds differences between the two specified trees.
     * 
     * @return an iterator to a set of differences between the two trees
     * @see DiffEntry
     */
    @Override
    protected Iterator<DiffEntry> _call() throws IllegalArgumentException {
        checkArgument(oldRefSpec != null || oldTreeId != null, "old version not specified");
        checkArgument(newRefSpec != null || oldTreeId != null, "new version not specified");
        final ObjectStore leftSource;
        final ObjectStore rightSource;

        leftSource = this.leftSource == null ? objectDatabase() : this.leftSource;
        rightSource = this.rightSource == null ? objectDatabase() : this.rightSource;

        final RevTree oldTree = resolveTree(oldRefSpec, this.oldTreeId, leftSource);
        final RevTree newTree = resolveTree(newRefSpec, this.newTreeId, rightSource);

        if (oldTree.equals(newTree)) {
            return Collections.emptyIterator();
        }

        final PreOrderDiffWalk visitor = new PreOrderDiffWalk(oldTree, newTree, leftSource,
                rightSource, preserveIterationOrder);
        visitor.setDefaultMetadataId(this.metadataId);

        final BlockingQueue<DiffEntry> queue = new ArrayBlockingQueue<>(10_000);
        final DiffEntryProducer diffProducer = new DiffEntryProducer(queue);
        diffProducer.setReportTrees(this.reportTrees);
        diffProducer.setRecursive(this.recursive);

        final List<RuntimeException> producerErrors = new LinkedList<>();

        Thread producerThread = new Thread("DiffTree producer thread") {
            @Override
            public void run() {
                Consumer consumer = diffProducer;
                if (limit != null) {// evaluated the latest
                    consumer = new PreOrderDiffWalk.MaxFeatureDiffsLimiter(consumer, limit);
                }
                if (customFilter != null) {
                    consumer = new PreOrderDiffWalk.FilteringConsumer(consumer, customFilter);
                }
                if (changeTypeFilter != null) {
                    consumer = new ChangeTypeFilteringDiffConsumer(changeTypeFilter, consumer);
                }
                if (boundsFilter != null) {
                    consumer = new BoundsFilteringDiffConsumer(boundsFilter, consumer,
                            objectDatabase());
                }
                if (!pathFilters.isEmpty()) {// evaluated the former
                    consumer = new PathFilteringDiffConsumer(pathFilters, consumer);
                }
                try {
                    LOGGER.trace("waking diff {} / {}", oldRefSpec, newRefSpec);
                    visitor.walk(consumer);
                    LOGGER.trace("finished waking diff {} / {}", oldRefSpec, newRefSpec);
                } catch (RuntimeException e) {
                    LOGGER.error("Error traversing diffs", e);
                    producerErrors.add(e);
                } finally {
                    diffProducer.finished = true;
                }
            }
        };
        producerThread.setDaemon(true);
        producerThread.start();

        Iterator<DiffEntry> consumerIterator = new AbstractIterator<DiffEntry>() {
            @Override
            protected DiffEntry computeNext() {
                if (!producerErrors.isEmpty()) {
                    throw new RuntimeException("Error in producer thread", producerErrors.get(0));
                }
                BlockingQueue<DiffEntry> entries = queue;
                boolean finished = diffProducer.isFinished();
                boolean empty = entries.isEmpty();
                while (!finished || !empty) {
                    try {
                        DiffEntry entry = entries.poll(10, TimeUnit.MILLISECONDS);
                        if (entry != null) {
                            return entry;
                        }
                        finished = diffProducer.isFinished();
                        empty = entries.isEmpty();
                    } catch (InterruptedException e) {
                        throw Throwables.propagate(e);
                    }
                }
                return endOfData();
            }

            @Override
            protected void finalize() {
                diffProducer.finished = true;
            }
        };
        return consumerIterator;
    }

    private RevTree resolveTree(@Nullable final String treeIsh, @Nullable final ObjectId treeOid,
            final ObjectStore source) {

        RevTree tree = null;
        ResolveTreeish command = null;

        if (treeOid != null) {
            if (ObjectId.NULL.equals(treeOid) || RevTree.EMPTY_TREE_ID.equals(treeOid)) {
                tree = RevTree.EMPTY;
            } else {
                command = command(ResolveTreeish.class).setTreeish(treeOid);
            }
        } else if (treeIsh.equals(ObjectId.NULL.toString())
                || RevTree.EMPTY_TREE_ID.toString().equals(treeIsh)) {
            tree = RevTree.EMPTY;
        } else {
            command = command(ResolveTreeish.class).setTreeish(treeIsh);
        }

        if (tree == null) {
            final Optional<ObjectId> treeId = command.call();
            checkArgument(treeId.isPresent(), treeIsh + " did not resolve to a tree");
            tree = source.getTree(treeId.get());
        }

        return tree;
    }

    private static class ChangeTypeFilteringDiffConsumer extends ForwardingConsumer {

        private final ChangeType changeTypeFilter;

        public ChangeTypeFilteringDiffConsumer(ChangeType changeTypeFilter, Consumer consumer) {
            super(consumer);
            this.changeTypeFilter = changeTypeFilter;
        }

        @Override
        public boolean feature(final NodeRef left, final NodeRef right) {
            if (featureApplies(left, right)) {
                super.feature(left, right);
            }
            return true;
        }

        @Override
        public boolean tree(final NodeRef left, final NodeRef right) {
            if (isRoot(left, right) || treeApplies(left, right)) {
                return super.tree(left, right);
            }
            return false;
        }

        @Override
        public void endTree(final NodeRef left, final NodeRef right) {
            if (isRoot(left, right) || treeApplies(left, right)) {
                super.endTree(left, right);
            }
        }

        @Override
        public boolean bucket(NodeRef lparent, NodeRef rparent, final BucketIndex bucketIndex,
                final Bucket left, final Bucket right) {
            return treeApplies(left, right)
                    && super.bucket(lparent, rparent, bucketIndex, left, right);
        }

        @Override
        public void endBucket(NodeRef lparent, NodeRef rparent, BucketIndex bucketIndex,
                Bucket left, Bucket right) {
            if (treeApplies(left, right)) {
                super.endBucket(lparent, rparent, bucketIndex, left, right);
            }
        }

        private boolean isRoot(final NodeRef left, final NodeRef right) {
            return NodeRef.ROOT.equals((left == null ? right : left).name());
        }

        private boolean featureApplies(final NodeRef left, final NodeRef right) {
            switch (changeTypeFilter) {
            case ADDED:
                return left == null;
            case MODIFIED:
                return left != null && right != null;
            case REMOVED:
                return right == null;
            default:
                throw new IllegalArgumentException("Unknown change type: " + changeTypeFilter);
            }
        }

        private boolean treeApplies(final Bounded left, final Bounded right) {
            if (left != null && right != null) {
                // if neither is null traversal of the trees must continue to figure out the
                // differences
                return true;
            }
            switch (changeTypeFilter) {
            case ADDED:
                return left == null;
            case REMOVED:
                return right == null;
            case MODIFIED:
                return false;// safe to return false as its guaranteed that either left or right is
                             // null
            default:
                throw new IllegalArgumentException("Unknown change type: " + changeTypeFilter);
            }
        }
    }

    private static class DiffEntryProducer implements Consumer {

        private boolean reportFeatures = true, reportTrees = false;

        private BlockingQueue<DiffEntry> entries;

        private volatile boolean finished;

        private boolean recursive = true;

        public DiffEntryProducer(BlockingQueue<DiffEntry> queue) {
            this.entries = queue;
        }

        @Override
        public boolean feature(NodeRef left, NodeRef right) {
            if (!finished && reportFeatures) {
                try {
                    entries.put(new DiffEntry(left, right));
                } catch (InterruptedException e) {
                    // throw Throwables.propagate(e);
                }
            }
            return true;
        }

        public void setRecursive(boolean recursive) {
            this.recursive = recursive;
        }

        public void setReportTrees(boolean reportTrees) {
            this.reportTrees = reportTrees;
        }

        public boolean isFinished() {
            return finished;
        }

        @Override
        public boolean tree(NodeRef left, NodeRef right) {
            final String parentPath = left == null ? right.getParentPath() : left.getParentPath();

            if (!finished && reportTrees) {
                if (parentPath != null) {// do not report the root tree
                    try {
                        entries.put(new DiffEntry(left, right));
                    } catch (InterruptedException e) {
                        // throw Throwables.propagate(e);
                        // die gracefully
                        return false;
                    }
                }
            }
            if (recursive) {
                return !finished;
            }
            return parentPath == null;
        }

        @Override
        public void endTree(NodeRef left, NodeRef right) {
            final String name = (left == null ? right : left).name();

            if (NodeRef.ROOT.equals(name)) {
                LOGGER.trace("Reached end of tree traversal");
                finished = true;
            }
        }

        @Override
        public boolean bucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                @Nullable Bucket left, @Nullable Bucket right) {

            return !finished;
        }

        @Override
        public void endBucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                @Nullable Bucket left, @Nullable Bucket right) {

            // do nothing

        }
    }

    /**
     * @param reportTrees
     * @return
     */
    public DiffTree setReportTrees(boolean reportTrees) {
        this.reportTrees = reportTrees;
        return this;
    }

    /**
     * Sets whether to return differences recursively ({@code true} or just for direct children (
     * {@code false}. Defaults to {@code true}
     */
    public DiffTree setRecursive(boolean recursive) {
        this.recursive = recursive;
        return this;
    }

    public DiffTree setMaxDiffs(@Nullable Long limit) {
        Preconditions.checkArgument(limit == null || limit.longValue() >= 0L,
                "limit must be >= 0: ", limit);
        this.limit = limit;
        return this;
    }

    public DiffTree setDefaultMetadataId(ObjectId metadataId) {
        this.metadataId = metadataId;
        return this;
    }

    public DiffTree setLeftSource(ObjectStore leftSource) {
        this.leftSource = leftSource;
        return this;
    }

    public DiffTree setRightSource(ObjectStore rightSource) {
        this.rightSource = rightSource;
        return this;
    }
}
