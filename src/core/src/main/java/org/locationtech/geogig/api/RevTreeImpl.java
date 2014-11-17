/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.locationtech.geogig.storage.NodeStorageOrder;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterators;

/**
 *
 */
public abstract class RevTreeImpl extends AbstractRevObject implements RevTree {

    private static final class LeafTree extends RevTreeImpl {

        private final Optional<ImmutableList<Node>> features;

        private final Optional<ImmutableList<Node>> trees;

        public LeafTree(final ObjectId id, final long size,
                final Optional<ImmutableList<Node>> features, Optional<ImmutableList<Node>> trees) {
            super(id, size);
            this.features = features;
            this.trees = trees;
        }

        @Override
        public Optional<ImmutableList<Node>> features() {
            return features;
        }

        @Override
        public Optional<ImmutableList<Node>> trees() {
            return trees;
        }

        @Override
        public int numTrees() {
            return trees.isPresent() ? trees.get().size() : 0;
        }

        @Override
        public final boolean isEmpty() {
            return features.isPresent() ? features.get().isEmpty() : (trees.isPresent() ? trees
                    .get().isEmpty() : true);
        }
    }

    private static final class NodeTree extends RevTreeImpl {

        private final Optional<ImmutableSortedMap<Integer, Bucket>> buckets;

        private final int childTreeCount;

        public NodeTree(final ObjectId id, final long size, final int childTreeCount,
                final ImmutableSortedMap<Integer, Bucket> innerTrees) {
            super(id, size);
            this.childTreeCount = childTreeCount;
            if (innerTrees.isEmpty()) {
                this.buckets = Optional.absent();
            } else {
                this.buckets = Optional.of(innerTrees);
            }
        }

        @Override
        public Optional<ImmutableSortedMap<Integer, Bucket>> buckets() {
            return buckets;
        }

        @Override
        public final boolean isEmpty() {
            return buckets().isPresent() ? buckets().get().isEmpty() : true;
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

    public static RevTreeImpl createLeafTree(ObjectId id, long size, ImmutableList<Node> features,
            ImmutableList<Node> trees) {

        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(features);
        Preconditions.checkNotNull(trees);

        Optional<ImmutableList<Node>> f = Optional.absent();
        Optional<ImmutableList<Node>> t = Optional.absent();
        if (!features.isEmpty()) {
            f = Optional.of(features);
        }
        if (!trees.isEmpty()) {
            t = Optional.of(trees);
        }
        return new LeafTree(id, size, f, t);
    }

    private static final NodeStorageOrder ordering = new NodeStorageOrder();

    public static RevTreeImpl createLeafTree(ObjectId id, long size, Collection<Node> features,
            Collection<Node> trees) {
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(features);

        ImmutableList<Node> featuresList = ImmutableList.of();
        ImmutableList<Node> treesList = ImmutableList.of();

        if (!features.isEmpty()) {
            featuresList = ordering.immutableSortedCopy(features);
        }
        if (!trees.isEmpty()) {
            treesList = ordering.immutableSortedCopy(trees);
        }
        return createLeafTree(id, size, featuresList, treesList);
    }

    public static RevTreeImpl createNodeTree(final ObjectId id, final long size,
            final int childTreeCount, final Map<Integer, Bucket> bucketTrees) {

        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(bucketTrees);
        Preconditions.checkArgument(bucketTrees.size() <= RevTree.MAX_BUCKETS);

        ImmutableSortedMap<Integer, Bucket> innerTrees = ImmutableSortedMap.copyOf(bucketTrees);

        return new NodeTree(id, size, childTreeCount, innerTrees);
    }

    public static RevTreeImpl create(ObjectId id, long size, RevTree unidentified) {
        if (unidentified.buckets().isPresent()) {
            return new NodeTree(id, size, unidentified.numTrees(), unidentified.buckets().get());
        }
        final Optional<ImmutableList<Node>> features;
        if (unidentified.features().isPresent()) {
            features = Optional.of(unidentified.features().get());
        } else {
            features = Optional.absent();
        }
        final Optional<ImmutableList<Node>> trees;
        if (unidentified.trees().isPresent()) {
            trees = Optional.of(unidentified.trees().get());
        } else {
            trees = Optional.absent();
        }
        return new LeafTree(id, size, features, trees);
    }

    @Override
    public TYPE getType() {
        return TYPE.TREE;
    }

    @Override
    public RevTreeBuilder builder(ObjectDatabase target) {
        return new RevTreeBuilder(target, this);
    }

    @Override
    public Iterator<Node> children() {
        Preconditions.checkState(!buckets().isPresent());
        ImmutableList<Node> trees = trees().or(ImmutableList.<Node> of());
        ImmutableList<Node> features = features().or(ImmutableList.<Node> of());
        if (trees.isEmpty()) {
            return features.iterator();
        }
        if (features.isEmpty()) {
            return trees.iterator();
        }
        return Iterators.mergeSorted(ImmutableList.of(trees.iterator(), features.iterator()),
                ordering);
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
        builder.append(size);
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
