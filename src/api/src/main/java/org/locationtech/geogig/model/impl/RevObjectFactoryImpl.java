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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.SortedSet;

import javax.annotation.Nonnegative;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.FieldType;
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
import org.locationtech.geogig.model.ValueArray;
import org.locationtech.geogig.model.impl.RevTreeImpl.LeafTree;
import org.locationtech.geogig.model.impl.RevTreeImpl.NodeTree;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Optional;
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
        if (parents.indexOf(null) > -1) {
            throw new NullPointerException("null parent at index " + parents.indexOf(null));
        }
        return new RevCommitImpl(id, treeId, ImmutableList.copyOf(parents), author, committer,
                message);
    }

    public @Override @NonNull RevTree createTree(final @NonNull ObjectId id, final long size,
            @NonNull List<Node> trees, @NonNull List<Node> features) {
        if (size < 0L) {
            throw new IllegalArgumentException("Cannot create a tree with negative size: " + size);
        }
        Node[] f = features.isEmpty() ? null : features.toArray(new Node[features.size()]);
        Node[] t = trees.isEmpty() ? null : trees.toArray(new Node[trees.size()]);
        checkNodes(t, TYPE.TREE);
        checkNodes(f, TYPE.FEATURE);
        return new LeafTree(id, size, f, t);
    }

    private void checkNodes(Node[] nodes, TYPE type) {
        if (nodes == null) {
            return;
        }
        for (int i = 0; i < nodes.length; i++) {
            Node node = nodes[i];
            if (node == null) {
                throw new NullPointerException(
                        "null node in " + type.toString().toLowerCase() + "s at index " + i);
            }
            if (node.getType() != type) {
                throw new IllegalArgumentException(type.toString().toLowerCase() + "s contains "
                        + node.getType() + " node at index " + i);
            }
        }
    }

    public @Override @NonNull RevTree createTree(final @NonNull ObjectId id, final long size,
            final int childTreeCount, @NonNull SortedSet<Bucket> buckets) {
        if (size < 0L) {
            throw new IllegalArgumentException("Cannot create a tree with negative size: " + size);
        }
        if (childTreeCount < 0) {
            throw new IllegalArgumentException(
                    "Cannot create a tree with negative child tree count: " + childTreeCount);
        }
        Bucket[] array = buckets.toArray(new Bucket[buckets.size()]);
        for (Bucket b : array) {
            if (b == null) {
                throw new NullPointerException("There's a null bucket in the buckets set");
            }
        }
        return new NodeTree(id, size, childTreeCount, array);
    }

    public @Override Bucket createBucket(final @NonNull ObjectId bucketTree,
            @Nonnegative int bucketIndex, final @Nullable Envelope bounds) {
        if (bucketIndex < 0) {
            throw new IllegalArgumentException(
                    "Bucket cannot have a negative index: " + bucketIndex);
        }
        Float32Bounds b32 = Float32Bounds.valueOf(bounds);
        return new BucketImpl(bucketTree, bucketIndex, b32);
    }

    public @Override Node createNode(final @NonNull String name, final @NonNull ObjectId objectId,
            final @NonNull ObjectId metadataId, final @NonNull TYPE type, @Nullable Envelope bounds,
            @Nullable Map<String, Object> extraData) {

        bounds = bounds == null || bounds.isNull() ? null : bounds;

        switch (type) {
        case FEATURE:
            return new FeatureNode(name, objectId, metadataId, bounds, extraData);
        case TREE:
            return new TreeNode(name, objectId, metadataId, bounds, extraData);
        default:
            throw new IllegalArgumentException(
                    "Invalid object type " + type + ": only FEATURE and TREE nodes can be created");
        }
    }

    public @Override @NonNull RevTag createTag(@NonNull ObjectId id, @NonNull String name,
            @NonNull ObjectId commitId, @NonNull String message, @NonNull RevPerson tagger) {
        return new RevTagImpl(id, name, commitId, message, tagger);
    }

    public @Override @NonNull RevFeatureType createFeatureType(@NonNull ObjectId id,
            @NonNull FeatureType ftype) {

        Collection<PropertyDescriptor> descriptors = ftype.getDescriptors();
        descriptors.forEach(d -> {
            Class<?> binding = d.getType().getBinding();
            Objects.requireNonNull(binding,
                    "got null binding for attribute " + d.getName().getLocalPart());
            FieldType fieldType = FieldType.forBinding(binding);
            if (FieldType.NULL == fieldType || FieldType.UNKNOWN == fieldType) {
                String msg = String.format(
                        "Attribute %s of FeatureType %s is of an unsupported type: %s",
                        d.getName().getLocalPart(), ftype.getName().getLocalPart(),
                        binding.getName());
                throw new IllegalArgumentException(msg);
            }
        });
        return new RevFeatureTypeImpl(id, ftype);
    }

    /**
     * @implNote: In order to preserve the {@link ValueArray}'s immutability, a safe copy of each
     *            element in {@code values} will be assigned if it's a mutable type.
     */
    public @Override @NonNull ValueArray createValueArray(@NonNull List<Object> values) {
        Object[] safe = new Object[values.size()];
        for (int i = 0; i < values.size(); i++) {
            safe[i] = ValueArray.safeCopy(values.get(i));
        }
        return new ValueArrayImpl(safe);
    }

    /**
     * @implNote: In order to preserve the {@link ValueArray}'s immutability, a safe copy of each
     *            element in {@code values} will be assigned if it's a mutable type.
     */
    public @Override @NonNull ValueArray createValueArray(@NonNull Object... values) {
        Object[] safe = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            safe[i] = ValueArray.safeCopy(values[i]);
        }
        return new ValueArrayImpl(safe);
    }

    /**
     * @implNote: In order to preserve the {@link ValueArray}'s immutability, a safe copy of each
     *            element in {@code values} will be assigned if it's a mutable type.
     */
    public @Override @NonNull RevFeature createFeature(@NonNull ObjectId id,
            @NonNull List<Object> values) {
        Object[] safe = new Object[values.size()];
        for (int i = 0; i < values.size(); i++) {
            safe[i] = ValueArray.safeCopy(values.get(i));
        }
        return new RevFeatureImpl(id, safe);
    }

    /**
     * @implNote: In order to preserve the {@link ValueArray}'s immutability, a safe copy of each
     *            element in {@code values} will be assigned if it's a mutable type.
     */
    public @Override @NonNull RevFeature createFeature(@NonNull ObjectId id,
            @NonNull Object... values) {
        Object[] safe = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            safe[i] = ValueArray.safeCopy(values[i]);
        }
        return new RevFeatureImpl(id, safe);
    }

}
