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

import java.util.Collections;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.locationtech.geogig.flatbuffers.generated.v1.LeafTree;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSortedMap;

import lombok.NonNull;

final class FBLeafTree extends FBRevObject<LeafTree> implements RevTree {

    public FBLeafTree(@NonNull LeafTree t) {
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
        return getTable().numDirectTreeNodes();
    }

    public @Override int treesSize() {
        return getTable().numDirectTreeNodes();
    }

    public @Override ImmutableList<Node> trees() {
        if (0 == treesSize()) {
            return ImmutableList.of();
        }
        Builder<Node> builder = ImmutableList.builder();
        forEachTree(builder::add);
        return builder.build();
    }

    public @Override int featuresSize() {
        int numNodes = getTable().nodesIdsLength();
        int numTreeNodes = treesSize();
        return numNodes - numTreeNodes;
    }

    public @Override ImmutableList<Node> features() {
        final int featuresSize = featuresSize();
        if (0 == featuresSize) {
            return ImmutableList.of();
        }
        Builder<Node> builder = ImmutableList.builder();
        forEachFeature(builder::add);
        return builder.build();
    }

    public @Override int bucketsSize() {
        return 0;
    }

    public @Deprecated @Override ImmutableSortedMap<Integer, Bucket> buckets() {
        return ImmutableSortedMap.of();
    }

    public @Override Iterable<Bucket> getBuckets() {
        return Collections.emptySet();
    }

    public @Override void forEachTree(Consumer<Node> consumer) {
        final int treesSize = treesSize();
        for (int nodeIndex = 0; nodeIndex < treesSize; nodeIndex++) {
            consumer.accept(FBNode.treeNode(getTable(), nodeIndex));
        }
    }

    public @Override void forEachFeature(Consumer<Node> consumer) {
        final int treesSize = treesSize();
        final int numNodes = getTable().nodesIdsLength();
        for (int nodeIndex = treesSize; nodeIndex < numNodes; nodeIndex++) {
            consumer.accept(FBNode.featureNode(getTable(), nodeIndex));
        }
    }

    public @Deprecated @Override void forEachBucket(BiConsumer<Integer, Bucket> consumer) {
        // no-op
    }

    public @Override void forEachBucket(Consumer<Bucket> consumer) {
        // no-op
    }

    public @Override Optional<Bucket> getBucket(final int bucketIndex) {
        return Optional.empty();
    }
}
