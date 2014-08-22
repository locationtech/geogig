/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Bounded;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.diff.BoundsFilteringDiffConsumer;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry.ChangeType;
import org.locationtech.geogig.api.plumbing.diff.DiffPathTracker;
import org.locationtech.geogig.api.plumbing.diff.DiffTreeVisitor;
import org.locationtech.geogig.api.plumbing.diff.DiffTreeVisitor.Consumer;
import org.locationtech.geogig.api.plumbing.diff.DiffTreeVisitor.ForwardingConsumer;
import org.locationtech.geogig.api.plumbing.diff.PathFilteringDiffConsumer;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

/**
 * Compares the content and metadata links of blobs found via two tree objects on the repository's
 * {@link ObjectDatabase}
 */
public class DiffTree extends AbstractGeoGigOp<Iterator<DiffEntry>> implements
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
        this.oldRefSpec = oldTreeId.toString();
        return this;
    }

    /**
     * @param newTreeId the {@link ObjectId} of the "new" tree
     * @return {@code this}
     */
    public DiffTree setNewTree(ObjectId newTreeId) {
        this.newRefSpec = newTreeId.toString();
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

    public DiffTree setCustomFilter(Predicate<Bounded> customFilter) {
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
        checkNotNull(oldRefSpec, "old version not specified");
        checkNotNull(newRefSpec, "new version not specified");
        final RevTree oldTree = resolveTree(oldRefSpec);
        final RevTree newTree = resolveTree(newRefSpec);

        if (oldTree.equals(newTree)) {
            return Iterators.emptyIterator();
        }

        ObjectDatabase leftSource = resolveSource(oldTree.getId());
        ObjectDatabase rightSource = resolveSource(newTree.getId());
        final DiffTreeVisitor visitor = new DiffTreeVisitor(oldTree, newTree, leftSource,
                rightSource);

        final BlockingQueue<DiffEntry> queue = new ArrayBlockingQueue<>(100);
        final DiffEntryProducer diffProducer = new DiffEntryProducer(queue);
        diffProducer.setReportTrees(this.reportTrees);
        diffProducer.setRecursive(this.recursive);

        final List<RuntimeException> producerErrors = new LinkedList<>();

        Thread producerThread = new Thread() {
            @Override
            public void run() {
                Consumer consumer = diffProducer;
                if (customFilter != null) {// evaluated the latest
                    consumer = new DiffTreeVisitor.FilteringConsumer(consumer, customFilter);
                }
                if (changeTypeFilter != null) {
                    consumer = new ChangeTypeFilteringDiffConsumer(changeTypeFilter, consumer);
                }
                if (boundsFilter != null) {
                    consumer = new BoundsFilteringDiffConsumer(boundsFilter, consumer,
                            stagingDatabase());
                }
                if (!pathFilters.isEmpty()) {// evaluated the former
                    consumer = new PathFilteringDiffConsumer(pathFilters, consumer);
                }
                try {
                    visitor.walk(consumer);
                } catch (RuntimeException e) {
                    LOGGER.error("Error traversing diffs", e);
                    producerErrors.add(e);
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

    private RevTree resolveTree(final String treeIsh) {
        RevTree tree;
        if (treeIsh.equals(ObjectId.NULL.toString())) {
            tree = RevTree.EMPTY;
        } else {
            final Optional<ObjectId> treeId = command(ResolveTreeish.class).setTreeish(treeIsh)
                    .call();
            checkArgument(treeId.isPresent(), treeIsh + " did not resolve to a tree");
            tree = command(RevObjectParse.class).setObjectId(treeId.get()).call(RevTree.class)
                    .or(RevTree.EMPTY);
        }
        return tree;
    }

    private ObjectDatabase resolveSource(ObjectId treeId) {
        return objectDatabase().equals(treeId) ? objectDatabase() : stagingDatabase();
    }

    private static class ChangeTypeFilteringDiffConsumer extends ForwardingConsumer {

        private final ChangeType changeTypeFilter;

        public ChangeTypeFilteringDiffConsumer(ChangeType changeTypeFilter, Consumer consumer) {
            super(consumer);
            this.changeTypeFilter = changeTypeFilter;
        }

        @Override
        public void feature(final Node left, final Node right) {
            if (featureApplies(left, right)) {
                super.feature(left, right);
            }
        }

        @Override
        public boolean tree(final Node left, final Node right) {
            if (isRoot(left, right) || treeApplies(left, right)) {
                return super.tree(left, right);
            }
            return false;
        }

        @Override
        public void endTree(final Node left, final Node right) {
            if (isRoot(left, right) || treeApplies(left, right)) {
                super.endTree(left, right);
            }
        }

        @Override
        public boolean bucket(final int bucketIndex, final int bucketDepth, final Bucket left,
                final Bucket right) {
            return treeApplies(left, right) && super.bucket(bucketIndex, bucketDepth, left, right);
        }

        @Override
        public void endBucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
            if (treeApplies(left, right)) {
                super.endBucket(bucketIndex, bucketDepth, left, right);
            }
        }

        private boolean isRoot(final Node left, final Node right) {
            return left == null ? right.getName().isEmpty() : left.getName().isEmpty();
        }

        private boolean featureApplies(final Node left, final Node right) {
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

        private DiffPathTracker tracker = new DiffPathTracker();

        private boolean reportFeatures = true, reportTrees = false;

        private BlockingQueue<DiffEntry> entries;

        private volatile boolean finished;

        private boolean recursive = true;

        public DiffEntryProducer(BlockingQueue<DiffEntry> queue) {
            this.entries = queue;
        }

        @Override
        public void feature(Node left, Node right) {
            if (!finished && reportFeatures) {
                String treePath = tracker.getCurrentPath();

                NodeRef oldRef = left == null ? null : new NodeRef(left, treePath, tracker
                        .currentLeftMetadataId().or(ObjectId.NULL));
                NodeRef newRef = right == null ? null : new NodeRef(right, treePath, tracker
                        .currentRightMetadataId().or(ObjectId.NULL));

                try {
                    entries.put(new DiffEntry(oldRef, newRef));
                } catch (InterruptedException e) {
                    // throw Throwables.propagate(e);
                }
            }
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
        public boolean tree(Node left, Node right) {
            final String parentPath = tracker.getCurrentPath();
            tracker.tree(left, right);
            // System.err.printf("%s.tree(%s, %s)\n", getClass().getSimpleName(), left, right);
            if (!finished && reportTrees) {
                if (parentPath != null) {// do not report the root tree
                    NodeRef oldRef = left == null ? null : new NodeRef(left, parentPath, tracker
                            .currentLeftMetadataId().or(ObjectId.NULL));

                    NodeRef newRef = right == null ? null : new NodeRef(right, parentPath, tracker
                            .currentRightMetadataId().or(ObjectId.NULL));
                    try {
                        entries.put(new DiffEntry(oldRef, newRef));
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
        public void endTree(Node left, Node right) {
            // System.err.printf("%s.endTree(%s, %s)\n", getClass().getSimpleName(), left, right);
            tracker.endTree(left, right);
            if (tracker.isEmpty()) {
                finished = true;
            }
        }

        @Override
        public boolean bucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
            return !finished;
        }

        @Override
        public void endBucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
            // no action required
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
}
