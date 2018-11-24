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

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

/**
 *
 */
abstract class RevTreeImpl extends AbstractRevObject implements RevTree {

    static final class LeafTree extends RevTreeImpl {

        private final ImmutableList<Node> features;

        private final ImmutableList<Node> trees;

        public LeafTree(final ObjectId id, final long size,
                final @Nullable ImmutableList<Node> features, @Nullable ImmutableList<Node> trees) {
            super(id, size);
            this.features = features == null ? ImmutableList.of() : features;
            this.trees = trees == null ? ImmutableList.of() : trees;
        }

        @Override
        public ImmutableList<Node> features() {
            return features;
        }

        @Override
        public ImmutableList<Node> trees() {
            return trees;
        }

        @Override
        public int numTrees() {
            return trees != null ? trees.size() : 0;
        }
    }

    static final class NodeTree extends RevTreeImpl {

        private final ImmutableSortedMap<Integer, Bucket> buckets;

        private final int childTreeCount;

        public NodeTree(final ObjectId id, final long size, final int childTreeCount,
                final ImmutableSortedMap<Integer, Bucket> innerTrees) {
            super(id, size);
            checkNotNull(innerTrees);
            this.childTreeCount = childTreeCount;
            this.buckets = innerTrees;
        }

        @Override
        public ImmutableSortedMap<Integer, Bucket> buckets() {
            return buckets;
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
            @Nullable ImmutableList<Node> trees, @Nullable ImmutableList<Node> features,
            @Nullable ImmutableSortedMap<Integer, Bucket> buckets) {

        checkNotNull(id);

        if (buckets == null || buckets.isEmpty()) {
            return new LeafTree(id, size, features, trees);
        }

        if ((features == null || features.isEmpty()) && (trees == null || trees.isEmpty())) {
            return new NodeTree(id, size, childTreeCount, buckets);
        }

        throw new IllegalArgumentException(
                "Mixed (containing nodes and buckets) trees are not supported");
    }

    @Override
    public String toString() {
        final int nSubtrees = treesSize();
        final int nBuckets = bucketsSize();
        final int nFeatures = featuresSize();

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
