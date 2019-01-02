/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.flatbuffers;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.locationtech.geogig.flatbuffers.generated.v1.NodeTree;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

import lombok.NonNull;

final class FBNodeTree extends FBRevObject<NodeTree> implements RevTree {

    public FBNodeTree(@NonNull NodeTree t) {
        super(t);
    }

    public @Override TYPE getType() {
        return TYPE.TREE;
    }

    public @Override String toString() {
        return RevObjects.toString(this);
    }

    public @Override long size() {
        return getTable().size();
    }

    public @Override int numTrees() {
        return getTable().numTreesRecursive();
    }

    public @Override int treesSize() {
        return 0;
    }

    public @Override ImmutableList<Node> trees() {
        return ImmutableList.of();
    }

    public @Override int featuresSize() {
        return 0;
    }

    public @Override ImmutableList<Node> features() {
        return ImmutableList.of();
    }

    public @Override int bucketsSize() {
        return getTable().bucketsLength();
    }

    public @Deprecated @Override ImmutableSortedMap<Integer, Bucket> buckets() {
        if (getTable().bucketsLength() == 0) {
            return ImmutableSortedMap.of();
        }
        ImmutableSortedMap.Builder<Integer, Bucket> builder = ImmutableSortedMap.naturalOrder();
        forEachBucket(bucket -> builder.put(bucket.getIndex(), bucket));
        return builder.build();
    }

    public @Override Iterable<Bucket> getBuckets() {
        final int size = bucketsSize();
        return () -> IntStream.range(0, size).mapToObj(this::getByIndex).iterator();
    }

    private Bucket getByIndex(int index) {
        org.locationtech.geogig.flatbuffers.generated.v1.Bucket bucket = getTable().buckets(index);
        return new FBBucket(bucket);
    }

    public @Override void forEachTree(Consumer<Node> consumer) {
        // no-op
    }

    public @Override void forEachFeature(Consumer<Node> consumer) {
        // no-op
    }

    public @Override void forEachBucket(Consumer<Bucket> consumer) {
        getBuckets().forEach(consumer);
    }

    public @Deprecated @Override void forEachBucket(BiConsumer<Integer, Bucket> consumer) {
        getBuckets().forEach(b -> consumer.accept(Integer.valueOf(b.getIndex()), b));
    }

    public @Override Optional<Bucket> getBucket(final int bucketIndex) {
        final int size = bucketsSize();
        for (int i = 0; i < size; i++) {
            org.locationtech.geogig.flatbuffers.generated.v1.Bucket bucket = getTable().buckets(i);
            if (bucketIndex == bucket.index()) {
                return Optional.of(new FBBucket(bucket));
            }
        }
        return Optional.empty();
    }

}
