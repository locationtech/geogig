/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import java.util.Comparator;
import java.util.Optional;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Objects;

import lombok.NonNull;

/**
 * Provides a way of describing the between two different {@link Node}s.
 * 
 * @since 1.0
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
            public @Override int value() {
                return 0;
            }
        },

        /**
         * Modify an existing Feature
         */
        MODIFIED {
            public @Override int value() {
                return 1;
            }
        },

        /**
         * Delete an existing Feature
         */
        REMOVED {
            public @Override int value() {
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
    public DiffEntry(NodeRef oldObject, NodeRef newObject) {

        Preconditions.checkArgument(oldObject != null || newObject != null,
                "Either oldObject or newObject shall not be null");

        if (oldObject != null && oldObject.equals(newObject)) {
            throw new IllegalArgumentException(
                    "Trying to create a DiffEntry for the same object id, means the object didn't change: "
                            + oldObject.toString());
        }
        if (oldObject != null && newObject != null) {
            if (!oldObject.getType().equals(newObject.getType())) {
                String msg = String.format("Types don't match: %s : %s", oldObject.getType(),
                        newObject.getType());
                throw new IllegalArgumentException(msg);
            }
        }

        this.oldObject = oldObject;
        this.newObject = newObject;
    }

    public static DiffEntry add(@NonNull NodeRef newObject) {
        return new DiffEntry(null, newObject);
    }

    public static DiffEntry delete(@NonNull NodeRef oldObject) {
        return new DiffEntry(oldObject, null);
    }

    public static DiffEntry modify(@NonNull NodeRef oldObject, @NonNull NodeRef newObject) {
        return new DiffEntry(oldObject, newObject);
    }

    /**
     * @return the id of the old version id of the object, or {@link ObjectId#NULL} if
     *         {@link #changeType()} is {@code ADD}
     */
    public @NonNull ObjectId oldObjectId() {
        return oldObject == null ? ObjectId.NULL : oldObject.getObjectId();
    }

    /**
     * @return the old object, or {@code null} if {@link #changeType()} is {@code ADD}
     */
    public NodeRef getOldObject() {
        return oldObject;
    }

    /**
     * @return an {@link Optional} with the old object, or {@link Optional#empty()} if there was no
     *         old object
     */
    public Optional<NodeRef> oldObject() {
        return Optional.ofNullable(oldObject);
    }

    /**
     * @return the id of the new version id of the object, or {@link ObjectId#NULL} if
     *         {@link #changeType()} is {@code DELETE}
     */
    public @NonNull ObjectId newObjectId() {
        return newObject == null ? ObjectId.NULL : newObject.getObjectId();
    }

    /**
     * @return the id of the new version of the object, or {@code null} if {@link #changeType()} is
     *         {@code DELETE}
     */
    public NodeRef getNewObject() {
        return newObject;
    }

    /**
     * @return an {@link Optional} with the new object, or {@link Optional#empty()} if there was no
     *         new object
     */
    public Optional<NodeRef> newObject() {
        return Optional.ofNullable(newObject);
    }

    /**
     * @return the type of change
     */
    public ChangeType changeType() {
        ChangeType type;
        if (oldObject == null) {
            type = ChangeType.ADDED;
        } else if (newObject == null) {
            type = ChangeType.REMOVED;
        } else {
            type = ChangeType.MODIFIED;
        }

        return type;
    }

    /**
     * @return the {@code DiffEntry} in the form of a readable {@code String}
     */
    public @Override String toString() {
        StringBuilder sb = new StringBuilder(changeType().toString());
        if (!isAdd()) {
            sb.append(" [").append(oldObject).append("] ");
        }
        if (isChange()) {
            sb.append("->");
        }
        if (!isDelete()) {
            sb.append(" [").append(newObject).append("]");
        }
        return sb.toString();
    }

    /**
     * @return the path represented by this entry; if there is no new object, the path will be that
     *         of the old object
     */
    public String path() {
        return newObject == null ? oldObject.path() : newObject.path();
    }

    public String parentPath() {
        return (newObject == null ? oldObject : newObject).getParentPath();
    }

    public @NonNull String name() {
        return newObject == null ? oldObject.name() : newObject.name();
    }

    public Optional<Node> newNode() {
        return newObject().map(NodeRef::getNode);
    }

    public Optional<Node> oldNode() {
        return oldObject().map(NodeRef::getNode);
    }

    /**
     * Determines if this {@code DiffEntry} is the same as another.
     * 
     * @param o the other object
     */
    public @Override boolean equals(Object o) {
        if (!(o instanceof DiffEntry)) {
            return false;
        }
        DiffEntry de = (DiffEntry) o;
        return Objects.equal(oldObject, de.oldObject) && Objects.equal(newObject, de.newObject);
    }

    /**
     * Expands an {@link Envelope} to include both the old object and the new object if they exist.
     * 
     * @param target the {@link Envelope} to expand
     */
    public void expand(Envelope target) {
        if (oldObject != null) {
            oldObject.expand(target);
        }
        if (newObject != null) {
            newObject.expand(target);
        }
    }

    /**
     * Generates a hash code for this entry.
     */
    public @Override int hashCode() {
        return Objects.hashCode(oldObject, newObject);
    }

    /**
     * @return {@code true} if the objects in this entry represent a deleted feature
     */
    public boolean isDelete() {
        return ChangeType.REMOVED.equals(changeType());
    }

    /**
     * @return {@code true} if the objects in this entry represent an added feature
     */
    public boolean isAdd() {
        return ChangeType.ADDED.equals(changeType());
    }

    /**
     * @return {@code true} if the objects in this entry represent a changed feature
     */
    public boolean isChange() {
        return ChangeType.MODIFIED.equals(changeType());
    }

    /**
     * {@link Comparator} for comparing the two {@code DiffEntries}. Primarily used to determine
     * which of the {@code DiffEntries} would come first in a diff traversal. The comparator follows
     * a set of rules to determine the order:
     * <p>
     * - If both {@code DiffEntries} represent changed features, the order is determined by the
     * comparison of their nodes.
     * <p>
     * - A {@code DiffEntry} that represents a changed tree will come before a {@code DiffEntry}
     * that represents a changed feature of that same tree.
     * <p>
     * - A {@code DiffEntry} that represents a changed tree will come after a {@code DiffEntry} that
     * represents a changed feature in a different tree.
     */
    public static Comparator<DiffEntry> COMPARATOR = new Comparator<DiffEntry>() {

        public @Override int compare(DiffEntry left, DiffEntry right) {
            final NodeRef nodeRef1 = left.oldObject().orElse(left.getNewObject());
            final NodeRef nodeRef2 = right.oldObject().orElse(right.getNewObject());

            if (nodeRef1.getType().equals(nodeRef2.getType())) {
                return CanonicalNodeOrder.INSTANCE.compare(nodeRef1.getNode(), nodeRef2.getNode());
            }
            // one is a tree, the other a feature.
            // the tree comes first if it's a feature's parent
            boolean leftIsRightsParent = NodeRef.isChild(nodeRef1.path()/* parent */,
                    nodeRef2.path()/* child */);
            if (leftIsRightsParent) {
                return -1;
            }
            boolean rightIsLeftsParent = NodeRef.isDirectChild(nodeRef2.path()/* parent */,
                    nodeRef1.path()/* child */);
            if (rightIsLeftsParent) {
                return 1;
            }
            // feature wins (got a new tree on one end while all features of the previous tree
            // haven't been consumed)
            return nodeRef1.getType() == TYPE.FEATURE ? -1 : 1;
        }
    };

    /**
     * @return the {@link RevObject.TYPE} of the new object, or {@code null} if there isn't one
     */
    public Optional<TYPE> newObjectType() {
        return newObject().map(NodeRef::getType);
    }

    /**
     * @return the {@link RevObject.TYPE} of the old object, or {@code null} if there isn't one
     */
    public Optional<TYPE> oldObjectType() {
        return oldObject().map(NodeRef::getType);
    }

    /**
     * @return the metadata {@link ObjectId} of the new object, or {@link ObjectId#NULL} if there
     *         isn't one
     */
    public @NonNull ObjectId newMetadataId() {
        NodeRef newObject = getNewObject();
        return newObject != null ? newObject.getMetadataId() : ObjectId.NULL;
    }

    /**
     * @return the metadata {@link ObjectId} of the old object, or {@link ObjectId#NULL} if there
     *         isn't one
     */
    public @NonNull ObjectId oldMetadataId() {
        NodeRef oldObject = getOldObject();
        return oldObject != null ? oldObject.getMetadataId() : ObjectId.NULL;
    }
}
