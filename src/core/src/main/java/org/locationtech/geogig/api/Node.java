/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.RevObject.TYPE;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Envelope;

/**
 * An identifier->object id mapping for an object
 * 
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

    private Node(final String name, final ObjectId oid, final ObjectId metadataId) {
        checkNotNull(name);
        checkNotNull(oid);
        checkNotNull(metadataId);
        this.name = name;
        this.objectId = oid;
        this.metadataId = metadataId.isNull() ? null : metadataId;
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
        return name.compareTo(o.getName());
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

        switch (type) {
        case FEATURE:
            if (bounds == null || bounds.isNull()) {
                return new FeatureNode(name, oid, metadataId);
            } else {
                return new BoundedFeatureNode(name, oid, metadataId, bounds);
            }
        case TREE:
            if (bounds == null || bounds.isNull()) {
                return new TreeNode(name, oid, metadataId);
            } else {
                return new BoundedTreeNode(name, oid, metadataId, bounds);
            }
        default:
            throw new IllegalArgumentException(
                    "Only FEATURE and TREE nodes can be created, got type " + type);
        }
    }

    private static class TreeNode extends Node {

        public TreeNode(String name, ObjectId oid, ObjectId mdid) {
            super(name, oid, mdid);
        }

        @Override
        public final TYPE getType() {
            return TYPE.TREE;
        }
    }

    private static final class BoundedTreeNode extends TreeNode {

        // dim0(0),dim0(1),dim1(0),dim1(1)
        private float[] bounds;

        public BoundedTreeNode(String name, ObjectId oid, ObjectId mdid, Envelope env) {
            super(name, oid, mdid);
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
    }

    private static class FeatureNode extends Node {

        public FeatureNode(String name, ObjectId oid, ObjectId mdid) {
            super(name, oid, mdid);
        }

        @Override
        public final TYPE getType() {
            return TYPE.FEATURE;
        }
    }

    private static final class BoundedFeatureNode extends FeatureNode {

        // dim0(0),dim1(0),dim0(1),dim1(1)
        private float[] bounds;

        public BoundedFeatureNode(String name, ObjectId oid, ObjectId mdid, Envelope env) {
            super(name, oid, mdid);
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
    }
}
