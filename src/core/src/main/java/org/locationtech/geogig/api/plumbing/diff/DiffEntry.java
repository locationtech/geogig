/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.diff;

import static com.google.common.base.Preconditions.checkArgument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.meta.When;

import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.repository.SpatialOps;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Provides a way of describing the between two different {@link Node}s.
 */
public class DiffEntry {

    /**
     * The possible types of change between the two {@link Node}s
     */
    public static enum ChangeType {
        /**
         * Add a new Feature
         */
        ADDED {
            @Override
            public int value() {
                return 0;
            }
        },

        /**
         * Modify an existing Feature
         */
        MODIFIED {
            @Override
            public int value() {
                return 1;
            }
        },

        /**
         * Delete an existing Feature
         */
        REMOVED {
            @Override
            public int value() {
                return 2;
            }
        };

        public abstract int value();

        public static ChangeType valueOf(int value) {
            // relying in the enum ordinal, beware
            return ChangeType.values()[value];
        }
    }

    private final NodeRef oldObject;

    private final NodeRef newObject;

    /**
     * Constructs a new {@code DiffEntry} from two different {@link Node}s
     * 
     * @param oldObject the old node ref
     * @param newObject the new node ref
     */
    public DiffEntry(@Nonnull(when = When.MAYBE) NodeRef oldObject,
            @Nonnull(when = When.MAYBE) NodeRef newObject) {

        Preconditions.checkArgument(oldObject != null || newObject != null,
                "Either oldObject or newObject shall not be null");

        if (oldObject != null && oldObject.equals(newObject)) {
            throw new IllegalArgumentException(
                    "Trying to create a DiffEntry for the same object id, means the object didn't change: "
                            + oldObject.toString());
        }
        if (oldObject != null && newObject != null) {
            checkArgument(oldObject.getType().equals(newObject.getType()), String.format(
                    "Types don't match: %s : %s", oldObject.getType().toString(), newObject
                            .getType().toString()));
        }

        this.oldObject = oldObject;
        this.newObject = newObject;
    }

    /**
     * @return the id of the old version id of the object, or {@link ObjectId#NULL} if
     *         {@link #changeType()} is {@code ADD}
     */
    public ObjectId oldObjectId() {
        return oldObject == null ? ObjectId.NULL : oldObject.objectId();
    }

    /**
     * @return the old object, or {@code null} if {@link #changeType()} is {@code ADD}
     */
    public NodeRef getOldObject() {
        return oldObject;
    }

    /**
     * @return the id of the new version id of the object, or {@link ObjectId#NULL} if
     *         {@link #changeType()} is {@code DELETE}
     */
    public ObjectId newObjectId() {
        return newObject == null ? ObjectId.NULL : newObject.objectId();
    }

    /**
     * @return the id of the new version of the object, or {@code null} if {@link #changeType()} is
     *         {@code DELETE}
     */
    public NodeRef getNewObject() {
        return newObject;
    }

    /**
     * @return the type of change
     */
    public ChangeType changeType() {
        ChangeType type;
        if (oldObject == null || oldObject.objectId().isNull()) {
            type = ChangeType.ADDED;
        } else if (newObject == null || newObject.objectId().isNull()) {
            type = ChangeType.REMOVED;
        } else {
            type = ChangeType.MODIFIED;
        }

        return type;
    }

    /**
     * @return the affected geographic region of the change, may be {@code null}
     */
    public Envelope where() {
        Envelope bounds = SpatialOps.aggregatedBounds(oldObject.getNode(), newObject.getNode());
        return bounds;
    }

    /**
     * @return the {@code DiffEntry} in the form of a readable {@code String}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(changeType().toString());
        if (!isAdd()) {
            sb.append(" [").append(oldObject).append("]");
        }
        if (isChange()) {
            sb.append(" -> ");
        }
        if (!isDelete()) {
            sb.append(" [").append(newObject).append(']');
        }
        return sb.toString();
    }

    /**
     * @return the path of the old object
     */
    public @Nullable
    String oldPath() {
        return oldObject == null ? null : oldObject.path();
    }

    /**
     * @return the path of the new object
     */
    public @Nullable
    String newPath() {
        return newObject == null ? null : newObject.path();
    }

    /**
     * @return the name of the new object
     */
    public @Nullable
    String newName() {
        return newObject == null ? null : newObject.getNode().getName();
    }

    /**
     * @return the name of the old object
     */
    public @Nullable
    String oldName() {
        return oldObject == null ? null : oldObject.getNode().getName();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DiffEntry)) {
            return false;
        }
        DiffEntry de = (DiffEntry) o;
        return Objects.equal(oldObject, de.oldObject) && Objects.equal(newObject, de.newObject);
    }

    /**
     * 
     * @param target
     */
    public void expand (Envelope target){
        if (oldObject != null) {
                oldObject.expand(target);
         }
         if(newObject != null){
                newObject.expand(target);
         }
        }
        
    @Override
    public int hashCode() {
        return Objects.hashCode(oldObject, newObject);
    }

    public boolean isDelete() {
        return ChangeType.REMOVED.equals(changeType());
    }

    public boolean isAdd() {
        return ChangeType.ADDED.equals(changeType());
    }

    public boolean isChange() {
        return ChangeType.MODIFIED.equals(changeType());
    }
}
