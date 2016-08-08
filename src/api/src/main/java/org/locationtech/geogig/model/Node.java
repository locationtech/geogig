/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.RevObject.TYPE;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Envelope;

/**
 * An identifier->object id mapping for an object
 * 
 * 
 * @since 1.0
 */
public abstract class Node implements Bounded, Comparable<Node> {

    /**
     * The name of the element
     */
    private String name;

    /**
     * Optional ID corresponding to metadata for the element
     */
    @Nullable
    private ObjectId metadataId;

    /**
     * Id of the object this ref points to
     */
    private ObjectId objectId;

    private Map<String, Object> extraData;

    private Node(final String name, final ObjectId oid, final ObjectId metadataId,
            Map<String, Object> extraData) {
        checkNotNull(name);
        checkNotNull(oid);
        checkNotNull(metadataId);
        this.name = name;
        this.objectId = oid;
        this.metadataId = metadataId.isNull() ? null : metadataId;
        this.extraData = extraData == null ? null : new HashMap<>(extraData);
    }

    public Node update(final ObjectId newId) {
        return update(newId, bounds().orNull());
    }

    public Node update(final ObjectId newId, final @Nullable Envelope newBounds) {
        ObjectId mdId = metadataId == null ? ObjectId.NULL : metadataId;
        return Node.create(name, newId, mdId, getType(), newBounds, extraData);
    }

    public Optional<ObjectId> getMetadataId() {
        return Optional.fromNullable(metadataId);
    }

    /**
     * @return the name of the {@link RevObject} this node points to
     */
    public String getName() {
        return name;
    }

    /**
     * @return the id of the {@link RevObject} this Node points to
     */
    public ObjectId getObjectId() {
        return objectId;
    }

    /**
     * @return the type of {@link RevObject} this node points to
     */
    public abstract TYPE getType();

    /**
     * Provides for natural ordering of {@code Node}, based on {@link #getName() name}
     */
    @Override
    public int compareTo(Node o) {
        int c = name.compareTo(o.getName());
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
    public int hashCode() {
        return 17 ^ getType().hashCode() * name.hashCode() * objectId.hashCode();
    }

    /**
     * Equality check based on {@link #getName() name}, {@link #getType() type}, and
     * {@link #getObjectId() objectId}; {@link #getMetadataId()} is NOT part of the equality check.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Node)) {
            return false;
        }
        Node r = (Node) o;
        return getType().equals(r.getType()) && name.equals(r.name) && objectId.equals(r.objectId);
    }

    /**
     * @return the Node represented as a readable string.
     */
    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName()).append('[').append(getName())
                .append(" -> ").append(getObjectId()).append(']').toString();
    }

    @Override
    public boolean intersects(Envelope env) {
        return false;
    }

    @Override
    public void expand(Envelope env) {
        //
    }

    public static Node tree(final String name, final ObjectId oid, final ObjectId metadataId) {
        return create(name, oid, metadataId, TYPE.TREE, null);
    }

    public static Node create(final String name, final ObjectId oid, final ObjectId metadataId,
            final TYPE type, @Nullable final Envelope bounds) {

        return create(name, oid, metadataId, type, bounds, null);
    }

    public static Node create(final String name, final ObjectId oid, final ObjectId metadataId,
            final TYPE type, @Nullable final Envelope bounds,
            @Nullable Map<String, Object> extraData) {
        checkNotNull(name, "name");
        checkNotNull(oid, "oid");
        checkNotNull(metadataId, "metadataId");
        checkNotNull(type, "type");

        switch (type) {
        case FEATURE:
            if (bounds == null || bounds.isNull()) {
                return new FeatureNode(name, oid, metadataId, extraData);
            } else {
                return new BoundedFeatureNode(name, oid, metadataId, bounds, extraData);
            }
        case TREE:
            if (bounds == null || bounds.isNull()) {
                return new TreeNode(name, oid, metadataId, extraData);
            } else {
                return new BoundedTreeNode(name, oid, metadataId, bounds, extraData);
            }
        default:
            throw new IllegalArgumentException(
                    "Only FEATURE and TREE nodes can be created, got type " + type);
        }
    }

    private static class TreeNode extends Node {

        public TreeNode(String name, ObjectId oid, ObjectId mdid, Map<String, Object> extraData) {
            super(name, oid, mdid, extraData);
        }

        @Override
        public final TYPE getType() {
            return TYPE.TREE;
        }

        @Override
        public Optional<Envelope> bounds() {
            return Optional.absent();
        }
    }

    private static final class BoundedTreeNode extends TreeNode {

        // dim0(0),dim0(1),dim1(0),dim1(1)
        private float[] bounds;

        public BoundedTreeNode(String name, ObjectId oid, ObjectId mdid, Envelope env,
                Map<String, Object> extraData) {
            super(name, oid, mdid, extraData);
            Preconditions.checkArgument(!env.isNull());

            if (env.getWidth() == 0 && env.getHeight() == 0) {
                bounds = new float[2];
            } else {
                bounds = new float[4];
                bounds[2] = (float) env.getMaxX();
                bounds[3] = (float) env.getMaxY();
            }
            bounds[0] = (float) env.getMinX();
            bounds[1] = (float) env.getMinY();
        }

        @Override
        public boolean intersects(Envelope env) {
            if (env.isNull()) {
                return false;
            }
            if (bounds.length == 2) {
                return env.intersects(bounds[0], bounds[1]);
            }
            return !(env.getMinX() > bounds[2] || env.getMaxX() < bounds[0]
                    || env.getMinY() > bounds[3] || env.getMaxY() < bounds[1]);
        }

        @Override
        public void expand(Envelope env) {
            env.expandToInclude(bounds[0], bounds[1]);
            if (bounds.length > 2) {
                env.expandToInclude(bounds[2], bounds[3]);
            }
        }

        @Override
        public Optional<Envelope> bounds() {
            Envelope b;
            if (bounds.length == 2) {
                b = new Envelope(bounds[0], bounds[0], bounds[1], bounds[1]);
            } else {
                b = new Envelope(bounds[0], bounds[1], bounds[2], bounds[3]);
            }
            return Optional.of(b);
        }
    }

    private static class FeatureNode extends Node {

        public FeatureNode(String name, ObjectId oid, ObjectId mdid,
                Map<String, Object> extraData) {
            super(name, oid, mdid, extraData);
        }

        @Override
        public final TYPE getType() {
            return TYPE.FEATURE;
        }

        @Override
        public Optional<Envelope> bounds() {
            return Optional.absent();
        }

    }

    private static final class BoundedFeatureNode extends FeatureNode {

        // dim0(0),dim1(0),dim0(1),dim1(1)
        private float[] bounds;

        public BoundedFeatureNode(String name, ObjectId oid, ObjectId mdid, Envelope env,
                Map<String, Object> extraData) {
            super(name, oid, mdid, extraData);
            Preconditions.checkArgument(!env.isNull());

            if (env.getWidth() == 0 && env.getHeight() == 0) {
                bounds = new float[2];
            } else {
                bounds = new float[4];
                bounds[2] = (float) env.getMaxX();
                bounds[3] = (float) env.getMaxY();
            }
            bounds[0] = (float) env.getMinX();
            bounds[1] = (float) env.getMinY();
        }

        @Override
        public boolean intersects(Envelope env) {
            if (env.isNull()) {
                return false;
            }
            if (bounds.length == 2) {
                return env.intersects(bounds[0], bounds[1]);
            }
            return !(env.getMinX() > bounds[2] || env.getMaxX() < bounds[0]
                    || env.getMinY() > bounds[3] || env.getMaxY() < bounds[1]);
        }

        @Override
        public void expand(Envelope env) {
            env.expandToInclude(bounds[0], bounds[1]);
            if (bounds.length > 2) {
                env.expandToInclude(bounds[2], bounds[3]);
            }
        }

        @Override
        public Optional<Envelope> bounds() {
            Envelope b;
            if (bounds.length == 2) {
                b = new Envelope(bounds[0], bounds[0], bounds[1], bounds[1]);
            } else {
                b = new Envelope(bounds[0], bounds[1], bounds[2], bounds[3]);
            }
            return Optional.of(b);
        }
    }

    @Nullable
    public Map<String, Object> getExtraData() {
        return extraData;
    }

    public void setExtraData(@Nullable Map<String, Object> extraData) {
        this.extraData = extraData;
    }
}
