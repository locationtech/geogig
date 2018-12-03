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

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;

import lombok.NonNull;

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

    public @Nullable Object getExtraData(String key) {
        return getExtraData().get(key);
    }

    /**
     * Provides for natural ordering of {@code Node}, based on {@link #getName() name}
     */
    public final @Override int compareTo(Node o) {
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
    public final @Override int hashCode() {
        return RevObjects.hashCode(this);
    }

    /**
     * Equality check based on {@link #getName() name}, {@link #getType() type}, and
     * {@link #getObjectId() objectId}; {@link #getMetadataId()} is NOT part of the equality check.
     */
    public final @Override boolean equals(Object o) {
        return RevObjects.equals(this, o);
    }

    /**
     * @return the Node represented as a readable string.
     */
    public @Override String toString() {
        return RevObjects.toString(this);
    }

    public Node update(final ObjectId newId) {
        return update(newId, bounds().orNull());
    }

    public Node update(final ObjectId newId, final @Nullable Envelope newBounds) {
        ObjectId mdId = getMetadataId().or(ObjectId.NULL);

        return RevObjectFactory.defaultInstance().createNode(getName(), newId, mdId, getType(),
                newBounds, getExtraData());
    }

    /**
     * @deprecated use {@link RevObjectFactory#createNode}
     */
    public static Node tree(final String name, final ObjectId oid, final ObjectId metadataId) {
        return create(name, oid, metadataId, TYPE.TREE, null);
    }

    /**
     * @deprecated use {@link RevObjectFactory#createNode}
     */
    public static Node create(final String name, final ObjectId oid, final ObjectId metadataId,
            final TYPE type, @Nullable final Envelope bounds) {

        return create(name, oid, metadataId, type, bounds, null);
    }

    /**
     * @deprecated use {@link RevObjectFactory#createNode}
     */
    public static Node create(final @NonNull String name, final @NonNull ObjectId oid,
            final @NonNull ObjectId metadataId, final @NonNull TYPE type, @Nullable Envelope bounds,
            @Nullable Map<String, Object> extraData) {

        return RevObjectFactory.defaultInstance().createNode(name, oid, metadataId, type, bounds,
                extraData);
    }
}
