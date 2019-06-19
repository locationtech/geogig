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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;

import lombok.NonNull;

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

        public @Override List<Node> features() {
            return features == null ? Collections.emptyList()
                    : Collections.unmodifiableList(Arrays.asList(features));
        }

        public @Override List<Node> trees() {
            return trees == null ? Collections.emptyList()
                    : Collections.unmodifiableList(Arrays.asList(trees));
        }

        public @Override int numTrees() {
            return trees == null ? 0 : trees.length;
        }

        public @Override int treesSize() {
            return numTrees();// being a leaf tree, numTrees and treesSize are the same
        }

        public @Override Node getTree(int index) {
            if (trees == null) {
                throw new IndexOutOfBoundsException("index: " + index + ", size: 0");
            }
            return trees[index];
        }

        public @Override void forEachTree(Consumer<Node> consumer) {
            if (trees != null) {
                for (int i = 0; i < trees.length; i++) {
                    consumer.accept(trees[i]);
                }
            }
        }

        public @Override int featuresSize() {
            return features == null ? 0 : features.length;
        }

        public @Override Node getFeature(int index) {
            if (features == null) {
                throw new IndexOutOfBoundsException("index: " + index + ", size: 0");
            }
            return features[index];
        }

        public @Override void forEachFeature(Consumer<Node> consumer) {
            if (features != null) {
                for (int i = 0; i < features.length; i++) {
                    consumer.accept(features[i]);
                }
            }
        }

    }

    static final class NodeTree extends RevTreeImpl {

        private final int childTreeCount;

        private final Bucket[] buckets;

        public NodeTree(final ObjectId id, final long size, final int childTreeCount,
                final @NonNull Bucket[] buckets) {
            super(id, size);
            this.childTreeCount = childTreeCount;
            this.buckets = buckets;
        }

        public @Override int numTrees() {
            return childTreeCount;
        }

        public @Override int bucketsSize() {
            return buckets.length;
        }

        public @Override void forEachBucket(Consumer<Bucket> consumer) {
            for (Bucket b : buckets) {
                consumer.accept(b);
            }
        }

        public @Override Optional<Bucket> getBucket(int bucketIndex) {
            Bucket searchKey = new BucketImpl(ObjectId.NULL, bucketIndex, null);
            int index = Arrays.binarySearch(buckets, searchKey,
                    (b1, b2) -> Integer.compare(b1.getIndex(), b2.getIndex()));

            return index < 0 ? Optional.empty() : Optional.of(buckets[index]);
        }

        public @Override Iterable<Bucket> getBuckets() {
            return Arrays.asList(buckets);
        }
    }

    private final long size;

    private RevTreeImpl(ObjectId id, long size) {
        super(id);
        this.size = size;
    }

    public final @Override long size() {
        return size;
    }

    public @Override List<Node> features() {
        return Collections.emptyList();
    }

    public @Override List<Node> trees() {
        return Collections.emptyList();
    }

    public @Override Iterable<Bucket> getBuckets() {
        return Collections.emptySortedSet();
    }

    public @Override String toString() {
        return RevObjects.toString(this);
    }
}
