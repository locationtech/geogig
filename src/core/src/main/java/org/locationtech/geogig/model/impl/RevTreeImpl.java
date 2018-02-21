/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedMap.Builder;

/**
 *
 */
abstract class RevTreeImpl extends AbstractRevObject implements RevTree {

    static final class LeafTree extends RevTreeImpl {

        private final Node[] features;

        private final Node[] trees;

        public LeafTree(final ObjectId id, final long size, final @Nullable Node[] features,
                @Nullable Node[] trees) {
            super(id, size);
            this.features = features;
            this.trees = trees;
        }

        @Override
        public ImmutableList<Node> features() {
            return features == null ? ImmutableList.of() : ImmutableList.copyOf(features);
        }

        @Override
        public ImmutableList<Node> trees() {
            return trees == null ? ImmutableList.of() : ImmutableList.copyOf(trees);
        }

        @Override
        public int numTrees() {
            return trees == null ? 0 : trees.length;
        }
    }

    static final class NodeTree extends RevTreeImpl {

        // private final ImmutableSortedMap<Integer, Bucket> buckets;

        private final int childTreeCount;

        private final IndexedBucket[] ibuckets;

        public NodeTree(final ObjectId id, final long size, final int childTreeCount,
                final SortedMap<Integer, Bucket> innerTrees) {
            super(id, size);
            checkNotNull(innerTrees);
            this.childTreeCount = childTreeCount;
            // this.buckets = innerTrees;
            // this.buckets = null;
            ArrayList<IndexedBucket> ibucketl = new ArrayList<>(innerTrees.size());
            innerTrees.forEach((i, b) -> ibucketl.add(new IndexedBucket(i, b)));
            this.ibuckets = ibucketl.toArray(new IndexedBucket[ibucketl.size()]);
        }

        private static class IndexedBucket {
            final int index;

            final Bucket bucket;

            IndexedBucket(int index, Bucket b) {
                this.index = index;
                this.bucket = b;
            }
        }

        @Override
        public ImmutableSortedMap<Integer, Bucket> buckets() {
            Builder<Integer, Bucket> builder = ImmutableSortedMap.naturalOrder();
            for (IndexedBucket ib : this.ibuckets) {
                builder.put(Integer.valueOf(ib.index), ib.bucket);
            }
            return builder.build();
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
    public ImmutableList<Node> features() {
        return ImmutableList.of();
    }

    @Override
    public ImmutableList<Node> trees() {
        return ImmutableList.of();
    }

    @Override
    public ImmutableSortedMap<Integer, Bucket> buckets() {
        return ImmutableSortedMap.of();
    }

    public static RevTree create(final ObjectId id, final long size, final int childTreeCount,
            @Nullable List<Node> trees, @Nullable List<Node> features,
            @Nullable SortedMap<Integer, Bucket> buckets) {

        checkNotNull(id);

        if (buckets == null || buckets.isEmpty()) {
            Node[] f = features == null ? null : features.toArray(new Node[features.size()]);
            Node[] t = trees == null ? null : trees.toArray(new Node[trees.size()]);
            return new LeafTree(id, size, f, t);
        }

        if ((features == null || features.isEmpty()) && (trees == null || trees.isEmpty())) {
            return new NodeTree(id, size, childTreeCount, buckets);
        }

        throw new IllegalArgumentException(
                "Mixed (containing nodes and buckets) trees are not supported");
    }

    @Override
    public String toString() {
        final int nSubtrees = trees().size();
        final int nBuckets = buckets().size();
        final int nFeatures = features().size();

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
