/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.model.impl;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevTreeImpl.LeafTree;
import org.locationtech.geogig.model.impl.RevTreeImpl.NodeTree;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.FeatureType;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import lombok.NonNull;

/**
 * Default implementation of {@link RevObjectFactory}, to be found by
 * {@link RevObjectFactory#defaultInstance()} using the {@link ServiceLoader} SPI, and with the
 * lowest {@link #getPriority() priority} to be overridden by default in case there are other
 * implementations.
 */
public class RevObjectFactoryImpl implements RevObjectFactory {

    /**
     * @return {@code 0}, lowest priority
     */
    public @Override int getPriority() {
        return 0;
    }

    public @Override @NonNull RevPerson createPerson(@Nullable String name, @Nullable String email,
            long timeStamp, int timeZoneOffset) {
        return new RevPersonImpl(Optional.fromNullable(name), Optional.fromNullable(email),
                timeStamp, timeZoneOffset);
    }

    public @Override @NonNull RevCommit createCommit(@NonNull ObjectId id, @NonNull ObjectId treeId,
            @NonNull List<ObjectId> parents, @NonNull RevPerson author,
            @NonNull RevPerson committer, @NonNull String message) {

        return new RevCommitImpl(id, treeId, ImmutableList.copyOf(parents), author, committer,
                message);
    }

    public @Override @NonNull RevTree createTree(final @NonNull ObjectId id, final long size,
            @NonNull List<Node> trees, @NonNull List<Node> features) {

        Node[] f = features.isEmpty() ? null : features.toArray(new Node[features.size()]);
        Node[] t = trees.isEmpty() ? null : trees.toArray(new Node[trees.size()]);
        return new LeafTree(id, size, f, t);
    }

    @Deprecated
    public @Override @NonNull RevTree createTree(final @NonNull ObjectId id, final long size,
            final int childTreeCount, @NonNull SortedMap<Integer, Bucket> buckets) {

        return new NodeTree(id, size, childTreeCount, new TreeSet<>(buckets.values()));
    }

    public @Override @NonNull RevTree createTree(final @NonNull ObjectId id, final long size,
            final int childTreeCount, @NonNull SortedSet<Bucket> buckets) {

        return new NodeTree(id, size, childTreeCount, buckets);
    }

    public @Override Bucket createBucket(final @NonNull ObjectId bucketTree, int bucketIndex,
            final @Nullable Envelope bounds) {
        Preconditions.checkNotNull(bucketTree);
        Float32Bounds b32 = Float32Bounds.valueOf(bounds);
        return new BucketImpl(bucketTree, bucketIndex, b32);
    }

    public @Override Node createNode(final @NonNull String name, final @NonNull ObjectId oid,
            final @NonNull ObjectId metadataId, final @NonNull TYPE type, @Nullable Envelope bounds,
            @Nullable Map<String, Object> extraData) {

        bounds = bounds == null || bounds.isNull() ? null : bounds;

        switch (type) {
        case FEATURE:
            return new FeatureNode(name, oid, metadataId, bounds, extraData);
        case TREE:
            return new TreeNode(name, oid, metadataId, bounds, extraData);
        default:
            throw new IllegalArgumentException(
                    "Only FEATURE and TREE nodes can be created, got type " + type);
        }
    }

    public @Override @NonNull RevTag createTag(@NonNull ObjectId id, @NonNull String name,
            @NonNull ObjectId commitId, @NonNull String message, @NonNull RevPerson tagger) {
        return new RevTagImpl(id, name, commitId, message, tagger);
    }

    public @Override @NonNull RevFeatureType createFeatureType(@NonNull ObjectId id,
            @NonNull FeatureType ftype) {

        return new RevFeatureTypeImpl(id, ftype);
    }

    public @Override @NonNull RevFeature createFeature(@NonNull ObjectId id,
            @NonNull List<Object> values) {
        return new RevFeatureImpl(id, values.toArray());
    }

    public @Override @NonNull RevFeature createFeature(@NonNull ObjectId id,
            @NonNull Object... values) {
        return new RevFeatureImpl(id, values.clone());
    }

}
