/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.experimental.internal;

import static com.google.common.base.Objects.equal;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;

import com.google.common.base.Preconditions;

class DAG implements Cloneable {

    public static enum STATE {
        INITIALIZED, MIRRORED, CHANGED;
    }

    /**
     * Nodes (both features and trees) that can't be promoted to one more depth level than this tree
     * is at (at the discretion of the {@link ClusteringStrategy})
     */
    @Nullable
    private Set<NodeId> nonPromotable;

    /**
     * Direct children feature and tree nodes that can be promoted to buckets if need be
     */
    @Nullable
    private Set<NodeId> children;

    /**
     * Bucket tree ids. At any point in time, a DAG has wither buckets or features and trees. It can
     * have non promotable nodes any time.
     */
    @Nullable
    private Set<TreeId> buckets;

    public final ObjectId treeId;

    private STATE state;

    /**
     * Total (recursive) number of children. Differs from tree size (computed at RevTree built time)
     * in that tree size includes the size of any child tree node.
     */
    public long childCount;

    @Override
    public DAG clone() {
        DAG c = new DAG(treeId);
        c.nonPromotable = nonPromotable == null ? null : new HashSet<>(nonPromotable);
        c.children = children == null ? null : new HashSet<>(children);
        c.buckets = buckets == null ? null : new HashSet<>(buckets);
        c.state = state;
        c.childCount = childCount;

        return c;
    }

    public DAG() {
        this.children = new HashSet<>();
        this.treeId = RevTree.EMPTY_TREE_ID;
        this.state = STATE.INITIALIZED;
    }

    DAG(final ObjectId originalTreeId, long childCount, STATE state, Set<NodeId> children,
            Set<NodeId> unpromotable, Set<TreeId> buckets) {

        this.treeId = originalTreeId;
        this.childCount = childCount;
        this.state = state;
        this.children = children == null ? (buckets == null ? new HashSet<>() : null) : children;
        this.nonPromotable = unpromotable;
        this.buckets = buckets;
    }

    public DAG(ObjectId originalTreeId) {
        this.children = new HashSet<>();
        this.treeId = originalTreeId;
        this.state = STATE.INITIALIZED;
    }

    public void setMirrored() {
        Preconditions.checkState(state == STATE.INITIALIZED);
        this.state = STATE.MIRRORED;
    }

    public void setChanged() {
        Preconditions.checkState(state == STATE.MIRRORED || state == STATE.CHANGED);
        this.state = STATE.CHANGED;
    }

    public STATE getState() {
        return this.state;
    }

    public @Nullable Set<NodeId> unpromotable() {
        return nonPromotable;
    }

    public @Nullable Set<NodeId> children() {
        return children;
    }

    public @Nullable Set<TreeId> buckets() {
        return buckets;
    }

    /**
     * Sets the internal set of children ids to {@code null} and sets up an empty set of bucket ids.
     * 
     * @return the set of children ids
     */
    public Set<NodeId> switchToBuckets() {
        Set<NodeId> c = children;
        children = null;
        if (buckets == null) {
            buckets = new HashSet<>();
        }
        return c;
    }

    public Set<NodeId> switchToLeaf() {
        this.buckets = null;
        this.children = new HashSet<>();
        return children;
    }

    public void addNonPromotable(Set<NodeId> nodeIds) {
        if (nonPromotable == null) {
            nonPromotable = new HashSet<>();
        }
        nonPromotable.addAll(nodeIds);
    }

    public synchronized void addNonPromotable(NodeId id) {
        if (nonPromotable == null) {
            nonPromotable = new HashSet<>();
        }
        if (!nonPromotable.add(id)) {
            nonPromotable.remove(id);
            nonPromotable.add(id);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DAG)) {
            return false;
        }
        DAG d = (DAG) o;
        return equal(treeId, d.treeId) && equal(childCount, d.childCount) && equal(state, d.state)
                && equal(children, d.children) && equal(nonPromotable, d.nonPromotable)
                && equal(buckets, d.buckets);
    }

    @Override
    public String toString() {
        return "DAG[features: " + children + ", buckets: " + buckets + ", non promotable: "
                + nonPromotable + "]";
    }

    public boolean isEmpty() {
        return (children == null || children.isEmpty()) && (buckets == null || buckets.isEmpty())
                && (nonPromotable == null || nonPromotable.isEmpty());
    }
}
