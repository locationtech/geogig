/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import static com.google.common.base.Objects.equal;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.datastream.Varint;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

final class DAG implements Cloneable, Serializable {

    private static final long serialVersionUID = 1L;

    public static enum STATE {
        /**
         * The DAG is just created and has not been mirrored to the original RevTree
         */
        INITIALIZED,
        /**
         * The DAG structre mirrors the original RevTree
         */
        MIRRORED,
        /**
         * The structure of the DAG has changed wrt it's original RevTree {@link DAG#treeId}
         */
        CHANGED;
    }

    /**
     * Nodes (both features and trees) that can't be promoted to one more depth level than this tree
     * is at (at the discretion of the {@link ClusteringStrategy})
     */
    private Set<NodeId> nonPromotable;

    /**
     * Direct children feature and tree nodes that can be promoted to buckets if need be
     */
    private Set<NodeId> children;

    /**
     * Bucket tree ids. At any point in time, a DAG has either buckets or feature/tree nodes. It can
     * have non promotable nodes at any time.
     */
    private Set<TreeId> buckets;

    /**
     * The object identifier for the {@link RevTree} this DAG was created from, or
     * {@link RevTree#EMPTY} if created from scratch.
     */
    public final ObjectId originalTreeId;

    private STATE state;

    /**
     * Total (recursive) number of children. Differs from tree size (computed at RevTree built time)
     * in that tree size includes the size of any child tree node.
     */
    private long childCount;

    private transient boolean mutated;

    @Override
    public DAG clone() {
        DAG c = new DAG(originalTreeId);
        if (!nonPromotable.isEmpty()) {
            c.nonPromotable = new HashSet<>(nonPromotable);
        }
        if (!children.isEmpty()) {
            c.children = new HashSet<>(children);
        }
        if (!buckets.isEmpty()) {
            c.buckets = new HashSet<>(buckets);
        }
        c.state = state;
        c.setChildCount(childCount);
        c.mutated = this.mutated;
        return c;
    }

    public DAG() {
        this.children = new HashSet<>();
        this.originalTreeId = RevTree.EMPTY_TREE_ID;
        this.state = STATE.INITIALIZED;
    }

    DAG(final ObjectId originalTreeId, long childCount, STATE state, Set<NodeId> children,
            Set<NodeId> unpromotable, Set<TreeId> buckets) {

        this.originalTreeId = originalTreeId;
        this.setChildCount(childCount);
        this.state = state;
        this.children = children;
        this.nonPromotable = unpromotable;
        this.buckets = buckets;
    }

    public DAG(ObjectId originalTreeId) {
        this.children = new HashSet<>();
        this.originalTreeId = originalTreeId;
        this.state = STATE.INITIALIZED;
        this.nonPromotable = ImmutableSet.of();
        this.children = ImmutableSet.of();
        this.buckets = ImmutableSet.of();
    }

    public void setMirrored() {
        Preconditions.checkState(state == STATE.INITIALIZED);
        this.mutated = true;
        this.state = STATE.MIRRORED;
    }

    public void setChanged() {
        Preconditions.checkState(state == STATE.MIRRORED || state == STATE.CHANGED);
        if (this.state != STATE.CHANGED) {
            this.mutated = true;
        }
        this.state = STATE.CHANGED;
    }

    public STATE getState() {
        return this.state;
    }

    // public Set<NodeId> unpromotable() {
    // return nonPromotable;
    // }
    //
    public void clearUnpromotable() {
        if (!nonPromotable.isEmpty()) {
            this.mutated = true;
            this.nonPromotable = ImmutableSet.of();
        }
    }

    public boolean addUnpromotable(NodeId nodeId) {
        if (this.nonPromotable.isEmpty()) {
            this.nonPromotable = new HashSet<>();
        }
        boolean changed = this.nonPromotable.add(nodeId);
        if (changed) {
            this.mutated = true;
        }
        return changed;
    }

    // public Set<NodeId> children() {
    // return children;
    // }
    //
    public void clearChildren() {
        if (!children.isEmpty()) {
            this.mutated = true;
            this.children = ImmutableSet.of();
        }
    }

    public boolean addChild(NodeId nodeId) {
        if (this.children.isEmpty()) {
            this.children = new HashSet<>();
        }
        boolean changed = this.children.add(nodeId);
        if (changed) {
            this.mutated = true;
        }
        return changed;
    }

    // public Set<TreeId> buckets() {
    // return buckets;
    // }
    //
    public void clearBuckets() {
        if (!buckets.isEmpty()) {
            this.mutated = true;
            this.buckets = ImmutableSet.of();
        }
    }

    public boolean addBucket(TreeId treeId) {
        if (this.buckets.isEmpty()) {
            this.buckets = new HashSet<>();
        }
        boolean changed = this.buckets.add(treeId);
        if (changed) {
            this.mutated = true;
        }
        return changed;
    }

    public void addNonPromotable(NodeId id) {
        if (nonPromotable.isEmpty()) {
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
        return equal(originalTreeId, d.originalTreeId) && equal(getChildCount(), d.getChildCount())
                && equal(state, d.state) && equal(children, d.children)
                && equal(nonPromotable, d.nonPromotable) && equal(buckets, d.buckets);
    }

    @Override
    public String toString() {
        return "DAG[features: " + children + ", buckets: " + buckets + ", non promotable: "
                + nonPromotable + "]";
    }

    public boolean isEmpty() {
        return children.isEmpty() && buckets.isEmpty() && nonPromotable.isEmpty();
    }

    public long getChildCount() {
        return childCount;
    }

    public void setChildCount(long childCount) {
        if (this.childCount != childCount) {
            this.mutated = true;
        }
        this.childCount = childCount;
    }

    public boolean removeChild(NodeId nodeId) {
        if (children.isEmpty()) {
            return false;
        }
        boolean removed = children.remove(nodeId);
        if (removed) {
            this.mutated = true;
        }
        return removed;
    }

    public int numBuckets() {
        return buckets.size();
    }

    public int numChildren() {
        return children.size();
    }

    public int numUnpromotable() {
        return nonPromotable.size();
    }

    public void forEachBucket(Consumer<? super TreeId> action) {
        this.buckets.forEach(action);
    }

    public void forEachChild(Consumer<? super NodeId> action) {
        this.children.forEach(action);
    }

    public void forEachUnpromotableChild(Consumer<? super NodeId> action) {
        this.nonPromotable.forEach(action);
    }

    boolean isMutated() {
        return mutated;
    }

    void setMutated(boolean mutated) {
        this.mutated = mutated;
    }

    public static void serialize(DAG dag, DataOutput out) throws IOException {
        final ObjectId treeId = dag.originalTreeId;
        final STATE state = dag.getState();
        final long childCount = dag.getChildCount();
        final Set<NodeId> nonPromotable = dag.nonPromotable;
        final Set<NodeId> children = dag.children;
        final Set<TreeId> buckets = dag.buckets;

        treeId.writeTo(out);
        out.writeByte(state.ordinal());
        Varint.writeUnsignedVarLong(childCount, out);

        Varint.writeUnsignedVarInt(nonPromotable.size(), out);
        Varint.writeUnsignedVarInt(children.size(), out);
        Varint.writeUnsignedVarInt(buckets.size(), out);

        for (NodeId nodeid : nonPromotable) {
            out.writeUTF(((CanonicalNodeId) nodeid).name());
        }
        for (NodeId nodeid : children) {
            out.writeUTF(((CanonicalNodeId) nodeid).name());
        }
        for (TreeId tid : buckets) {
            byte[] bucketIndicesByDepth = tid.bucketIndicesByDepth;
            Varint.writeUnsignedVarInt(bucketIndicesByDepth.length, out);
            out.write(bucketIndicesByDepth);
        }
    }

    public static DAG deserialize(DataInput in) throws IOException {
        final ObjectId treeId = ObjectId.readFrom(in);
        final STATE state = STATE.values()[in.readByte() & 0xFF];
        final long childCount = Varint.readUnsignedVarLong(in);

        final int nonPromotableSize = Varint.readUnsignedVarInt(in);
        final int childrenSize = Varint.readUnsignedVarInt(in);
        final int bucketSize = Varint.readUnsignedVarInt(in);

        Set<NodeId> nonPromotable = ImmutableSet.of();
        Set<NodeId> children = ImmutableSet.of();
        Set<TreeId> buckets = ImmutableSet.of();

        if (nonPromotableSize > 0) {
            nonPromotable = new HashSet<>();
            for (int i = 0; i < nonPromotableSize; i++) {
                String name = in.readUTF();
                nonPromotable.add(new CanonicalNodeId(name));
            }
        }
        if (childrenSize > 0) {
            children = new HashSet<>();
            for (int i = 0; i < childrenSize; i++) {
                String name = in.readUTF();
                children.add(new CanonicalNodeId(name));
            }
        }
        if (bucketSize > 0) {
            buckets = new HashSet<>();
            for (int i = 0; i < bucketSize; i++) {
                final int len = Varint.readUnsignedVarInt(in);
                final byte[] bucketIndicesByDepth = new byte[len];
                in.readFully(bucketIndicesByDepth);
                buckets.add(new TreeId(bucketIndicesByDepth));
            }
        }

        DAG dag = new DAG(treeId, childCount, state, children, nonPromotable, buckets);
        return dag;
    }
}
