/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.RevObject.TYPE;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.vividsolutions.jts.geom.Envelope;

/**
 * An identifier->object id mapping for an object
 * 
 * 
 * @since 1.0
 */
public abstract class Node implements Bounded, Comparable<Node> {

    /**
     * @return the type of {@link RevObject} this node points to
     */
    public abstract TYPE getType();

    /**
     * @return the name of the {@link RevObject} this node points to
     */
    public abstract String getName();

    /**
     * @return the id of the {@link RevObject} this Node points to
     */
    public abstract ObjectId getObjectId();

    public abstract Optional<ObjectId> getMetadataId();

    /**
     * Returns the extra data stored with this node, which shall be considered immutable, regardless
     * of the concrete Map implementation returned.
     * <p>
     * Changes to the returned map, if it's not immutable, will not affect the internal state of the
     * node.
     * <p>
     * The returned map may contain {@code null} values, but not {@code null} key.
     * 
     * @return a non-null, possibly empty <b> copy </b> of this node's extra data map
     */
    public abstract Map<String, Object> getExtraData();

    /**
     * Provides for natural ordering of {@code Node}, based on {@link #getName() name}
     */
    @Override
    public final int compareTo(Node o) {
        int c = getName().compareTo(o.getName());
        if (c == 0) {
            c = getType().compareTo(o.getType());
        }
        if (c == 0) {
            c = getObjectId().compareTo(o.getObjectId());
        }
        return c;
    }

    /**
     * Hash code is based on name and object id
     */
    @Override
    public final int hashCode() {
        return 17 ^ getType().hashCode() * getName().hashCode() * getObjectId().hashCode();
    }

    /**
     * Equality check based on {@link #getName() name}, {@link #getType() type}, and
     * {@link #getObjectId() objectId}; {@link #getMetadataId()} is NOT part of the equality check.
     */
    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof Node)) {
            return false;
        }
        Node r = (Node) o;
        return getType().equals(r.getType()) && getName().equals(r.getName())
                && getObjectId().equals(r.getObjectId());
    }

    /**
     * @return the Node represented as a readable string.
     */
    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName()).append('[').append(getName())
                .append(" -> ").append(getObjectId()).append(']').toString();
    }

    private static abstract class BaseNode extends Node {

        /**
         * The name of the element
         */
        private final String name;

        /**
         * Optional ID corresponding to metadata for the element
         */
        @Nullable
        private final ObjectId metadataId;

        /**
         * Id of the object this ref points to
         */
        private final int objectId_h1;

        private final long objectId_h2, objectId_h3;

        private final ExtraData extraData;

        private final Float32Bounds bounds;

        private BaseNode(final String name, final ObjectId oid, final ObjectId metadataId,
                @Nullable Envelope bounds, @Nullable Map<String, Object> extraData) {
            checkNotNull(name);
            checkNotNull(oid);
            checkNotNull(metadataId);
            this.name = name;
            this.objectId_h1 = RevObjects.h1(oid);
            this.objectId_h2 = RevObjects.h2(oid);
            this.objectId_h3 = RevObjects.h3(oid);
            this.metadataId = metadataId.isNull() ? null : metadataId;
            this.extraData = ExtraData.of(extraData);
            this.bounds = Float32Bounds.valueOf(bounds);
        }

        @Override
        public Optional<ObjectId> getMetadataId() {
            return Optional.fromNullable(metadataId);
        }

        /**
         * @return the name of the {@link RevObject} this node points to
         */
        @Override
        public String getName() {
            return name;
        }

        /**
         * @return the id of the {@link RevObject} this Node points to
         */
        public ObjectId getObjectId() {
            return ObjectId.create(objectId_h1, objectId_h2, objectId_h3);
        }

        @Override
        public boolean intersects(Envelope env) {
            return bounds.intersects(env);
        }

        @Override
        public void expand(Envelope env) {
            bounds.expand(env);
        }

        @Override
        public Optional<Envelope> bounds() {
            return fromNullable(bounds.isNull() ? null : bounds.asEnvelope());
        }

        @Override
        public Map<String, Object> getExtraData() {
            return extraData.asMap();
        }

    }

    public Node update(final ObjectId newId) {
        return update(newId, bounds().orNull());
    }

    public Node update(final ObjectId newId, final @Nullable Envelope newBounds) {
        ObjectId mdId = getMetadataId().or(ObjectId.NULL);
        return Node.create(getName(), newId, mdId, getType(), newBounds, getExtraData());
    }

    public static Node tree(final String name, final ObjectId oid, final ObjectId metadataId) {
        return create(name, oid, metadataId, TYPE.TREE, null);
    }

    public static Node create(final String name, final ObjectId oid, final ObjectId metadataId,
            final TYPE type, @Nullable final Envelope bounds) {

        return create(name, oid, metadataId, type, bounds, null);
    }

    public static Node create(final String name, final ObjectId oid, final ObjectId metadataId,
            final TYPE type, @Nullable Envelope bounds, @Nullable Map<String, Object> extraData) {
        checkNotNull(name, "name");
        checkNotNull(oid, "oid");
        checkNotNull(metadataId, "metadataId");
        checkNotNull(type, "type");

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

    public static @Nullable Envelope makePrecise(@Nullable Envelope bounds) {
        Envelope float32Bounds = Float32Bounds.valueOf(bounds).asEnvelope();
        return float32Bounds.isNull() ? null : float32Bounds;
    }

    private static final class TreeNode extends BaseNode {

        // dim0(0),dim0(1),dim1(0),dim1(1)
        public TreeNode(String name, ObjectId oid, ObjectId mdid, @Nullable Envelope env,
                @Nullable Map<String, Object> extraData) {
            super(name, oid, mdid, env, extraData);
        }

        @Override
        public final TYPE getType() {
            return TYPE.TREE;
        }

    }

    private static final class FeatureNode extends BaseNode {

        // dim0(0),dim1(0),dim0(1),dim1(1)
        public FeatureNode(String name, ObjectId oid, ObjectId mdid, @Nullable Envelope env,
                @Nullable Map<String, Object> extraData) {
            super(name, oid, mdid, env, extraData);
        }

        @Override
        public final TYPE getType() {
            return TYPE.FEATURE;
        }

    }

    /**
     * Holds on the node's extra data as an array of objects to lower the memory impact of HashMap,
     * and makes sure the returned map is a recursive safe copy in order to preserve the node's
     * immutability
     *
     */
    private static class ExtraData {
        private static ExtraData EMPTY = new ExtraData(new Object[0]);

        private Object[] kvp;

        ExtraData(Object[] kvp) {
            this.kvp = kvp;
        }

        static ExtraData of(@Nullable Map<String, Object> map) {
            if (null == map || map.isEmpty()) {
                return EMPTY;
            }
            final int size = map.size();
            Object[] kvp = new Object[2 * size];
            int i = 0;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                kvp[i] = e.getKey();
                kvp[i + 1] = safeCopy(e.getValue());
                i += 2;
            }
            return new ExtraData(kvp);
        }

        public Map<String, Object> asMap() {
            final int size = kvp.length / 2;
            if (0 == size) {
                return ImmutableMap.of();
            }
            Map<String, Object> map = new HashMap<>(size);
            for (int i = 0, j = 0; i < size; i++, j += 2) {
                String k = (String) kvp[j];
                Object v = safeCopy(kvp[j + 1]);
                map.put(k, v);
            }
            return map;
        }

        private static Object safeCopy(Object v) {
            Object safeCopy = FieldType.forValue(v).safeCopy(v);
            return safeCopy;
        }
    }
}
