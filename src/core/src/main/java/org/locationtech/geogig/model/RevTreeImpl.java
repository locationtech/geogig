/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.SortedMap;

import org.eclipse.jdt.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterators;

/**
 *
 */
abstract class RevTreeImpl extends AbstractRevObject implements RevTree {

    static final class LeafTree extends RevTreeImpl {

        private @Nullable final ImmutableList<Node> features;

        private @Nullable final ImmutableList<Node> trees;

        public LeafTree(final ObjectId id, final long size,
                final @Nullable ImmutableList<Node> features, @Nullable ImmutableList<Node> trees) {
            super(id, size);
            this.features = features == null || features.isEmpty() ? null : features;
            this.trees = trees == null || trees.isEmpty() ? null : trees;
        }

        @Override
        public Optional<ImmutableList<Node>> features() {
            return Optional.fromNullable(features);
        }

        @Override
        public Optional<ImmutableList<Node>> trees() {
            return Optional.fromNullable(trees);
        }

        @Override
        public int numTrees() {
            return trees != null ? trees.size() : 0;
        }

        @Override
        public final boolean isEmpty() {
            return (features == null || features.isEmpty()) && (trees == null || trees.isEmpty());
        }
    }

    static final class NodeTree extends RevTreeImpl {

        private final @Nullable ImmutableSortedMap<Integer, Bucket> buckets;

        private final int childTreeCount;

        public NodeTree(final ObjectId id, final long size, final int childTreeCount,
                final ImmutableSortedMap<Integer, Bucket> innerTrees) {
            super(id, size);
            checkNotNull(innerTrees);
            this.childTreeCount = childTreeCount;
            if (innerTrees.isEmpty()) {
                this.buckets = null;
            } else {
                this.buckets = innerTrees;
            }
        }

        @Override
        public Optional<ImmutableSortedMap<Integer, Bucket>> buckets() {
            return Optional.fromNullable(buckets);
        }

        @Override
        public final boolean isEmpty() {
            return buckets == null || buckets.isEmpty();
        }

        @Override
        public int numTrees() {
            return childTreeCount;
        }
    }

    private static final class MixedTree extends RevTreeImpl {

        private final int childTreeCount;

        private @Nullable final ImmutableList<Node> trees;

        private @Nullable final ImmutableList<Node> features;

        private @Nullable final ImmutableSortedMap<Integer, Bucket> buckets;

        public MixedTree(final ObjectId id, final long size, final int childTreeCount,
                @Nullable final ImmutableList<Node> trees,
                @Nullable final ImmutableList<Node> features,
                @Nullable final ImmutableSortedMap<Integer, Bucket> buckets) {
            super(id, size);
            this.childTreeCount = childTreeCount;

            this.trees = trees == null || trees.isEmpty() ? null : trees;
            this.features = features == null || features.isEmpty() ? null : features;
            this.buckets = buckets == null || buckets.isEmpty() ? null : buckets;
        }

        @Override
        public Optional<ImmutableList<Node>> features() {
            return Optional.fromNullable(features);
        }

        @Override
        public Optional<ImmutableList<Node>> trees() {
            return Optional.fromNullable(trees);
        }

        @Override
        public Optional<ImmutableSortedMap<Integer, Bucket>> buckets() {
            return Optional.fromNullable(buckets);
        }

        @Override
        public final boolean isEmpty() {
            return (trees == null || trees.isEmpty()) && (features == null || features.isEmpty())
                    && (buckets == null || buckets.isEmpty());
        }

        @Override
        public int numTrees() {
            return childTreeCount;
        }
    }

    private final long size;

    private RevTreeImpl(ObjectId id, long size) {
        super(id);
        this.size = size;
    }

    @Override
    public final long size() {
        return size;
    }

    @Override
    public Optional<ImmutableList<Node>> features() {
        return Optional.absent();
    }

    @Override
    public Optional<ImmutableList<Node>> trees() {
        return Optional.absent();
    }

    @Override
    public Optional<ImmutableSortedMap<Integer, Bucket>> buckets() {
        return Optional.absent();
    }

    public static RevTree create(final ObjectId id, final long size, final int childTreeCount,
            @Nullable ImmutableList<Node> trees, @Nullable ImmutableList<Node> features,
            @Nullable SortedMap<Integer, Bucket> buckets) {

        checkNotNull(id);

        if (buckets == null || buckets.isEmpty()) {
            return new LeafTree(id, size, features, trees);
        }

        ImmutableSortedMap<Integer, Bucket> immutableBuckets = ImmutableSortedMap
                .copyOfSorted(buckets);

        if ((features == null || features.isEmpty()) && (trees == null || trees.isEmpty())) {
            return new NodeTree(id, size, childTreeCount, immutableBuckets);
        }
        return new MixedTree(id, size, childTreeCount, trees, features, immutableBuckets);
    }

    @Override
    public TYPE getType() {
        return TYPE.TREE;
    }

    @Override
    public Iterator<Node> children() {
        ImmutableList<Node> trees = trees().or(ImmutableList.<Node> of());
        ImmutableList<Node> features = features().or(ImmutableList.<Node> of());
        if (trees.isEmpty()) {
            return features.iterator();
        }
        if (features.isEmpty()) {
            return trees.iterator();
        }
        return Iterators.mergeSorted(ImmutableList.of(trees.iterator(), features.iterator()),
                CanonicalNodeOrder.INSTANCE);
    }

    @Override
    public String toString() {
        final int nSubtrees;
        if (trees().isPresent()) {
            nSubtrees = trees().get().size();
        } else {
            nSubtrees = 0;
        }
        final int nBuckets;
        if (buckets().isPresent()) {
            nBuckets = buckets().get().size();
        } else {
            nBuckets = 0;
        }
        final int nFeatures;
        if (features().isPresent()) {
            nFeatures = features().get().size();
        } else {
            nFeatures = 0;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Tree[");
        builder.append(getId().toString());
        builder.append("; size=");
        builder.append(String.format("%,d", size));
        builder.append("; subtrees=");
        builder.append(nSubtrees);
        builder.append(", buckets=");
        builder.append(nBuckets);
        builder.append(", features=");
        builder.append(nFeatures);
        builder.append(']');
        return builder.toString();
    }
}
