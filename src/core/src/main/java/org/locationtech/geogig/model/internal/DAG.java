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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.datastream.Varint;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

class DAG implements Cloneable, Serializable {

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
     * Direct children feature and tree nodes that can be promoted to buckets if need be, keyed by
     * featureid
     */
    private Map<String, NodeId> children;

    /**
     * Bucket tree ids. At any point in time, a DAG has either buckets or feature/tree nodes.
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

    private final transient TreeId id;

    /**
     * Not to be changed directly, but through {@link #setMutated}
     */
    private transient boolean mutated;

    @Override
    public DAG clone() {
        DAG c = new DAG(id, originalTreeId);
        if (!children.isEmpty()) {
            c.children = new HashMap<>(children);
        }
        if (!buckets.isEmpty()) {
            c.buckets = new HashSet<>(buckets);
        }
        c.state = state;
        c.setChildCount(childCount);
        c.mutated = this.mutated;
        return c;
    }

    public DAG(TreeId id) {
        this.id = id;
        this.children = new HashMap<>();
        this.originalTreeId = RevTree.EMPTY_TREE_ID;
        this.state = STATE.INITIALIZED;
    }

    DAG(final TreeId id, final ObjectId originalTreeId, long childCount, STATE state,
            Set<NodeId> children, Set<TreeId> buckets) {
        this.id = id;
        this.originalTreeId = originalTreeId;
        this.setChildCount(childCount);
        this.state = state;
        this.children = new HashMap<>(Maps.uniqueIndex(children, (n) -> n.name()));
        this.buckets = buckets;
    }

    public DAG(TreeId id, ObjectId originalTreeId) {
        this.id = id;
        this.children = new HashMap<>();
        this.originalTreeId = originalTreeId;
        this.state = STATE.INITIALIZED;
        this.children = ImmutableMap.of();
        this.buckets = ImmutableSet.of();
    }

    public TreeId getId() {
        return id;
    }

    public void setMirrored() {
        Preconditions.checkState(state == STATE.INITIALIZED);
        setMutated();
        this.state = STATE.MIRRORED;
    }

    public void setChanged() {
        Preconditions.checkState(state == STATE.MIRRORED || state == STATE.CHANGED);
        if (this.state != STATE.CHANGED) {
            setMutated();
        }
        this.state = STATE.CHANGED;
    }

    public STATE getState() {
        return this.state;
    }

    public void clearChildren() {
        if (!children.isEmpty()) {
            setMutated();
            this.children = ImmutableMap.of();
        }
    }

    public boolean addChild(NodeId nodeId) {
        if (this.children.isEmpty()) {
            this.children = new HashMap<>();
        }
        NodeId oldVal = this.children.put(nodeId.name(), nodeId);
        boolean changed = !Objects.equal(oldVal, nodeId);
        if (changed) {
            setMutated();
        }
        return changed;
    }

    public void clearBuckets() {
        if (!buckets.isEmpty()) {
            setMutated();
            this.buckets = ImmutableSet.of();
        }
    }

    public boolean addBucket(TreeId treeId) {
        if (this.buckets.isEmpty()) {
            this.buckets = new HashSet<>();
        }
        boolean changed = this.buckets.add(treeId);
        if (changed) {
            setMutated();
        }
        return changed;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DAG)) {
            return false;
        }
        DAG d = (DAG) o;
        return equal(originalTreeId, d.originalTreeId) && equal(getChildCount(), d.getChildCount())
                && equal(state, d.state) && equal(children, d.children)
                && equal(buckets, d.buckets);
    }

    @Override
    public String toString() {
        return "DAG[features: " + children + ", buckets: " + buckets + "]";
    }

    public boolean isEmpty() {
        return children.isEmpty() && buckets.isEmpty();
    }

    public long getChildCount() {
        return childCount;
    }

    public void setChildCount(long childCount) {
        if (this.childCount != childCount) {
            setMutated();
        }
        this.childCount = childCount;
    }

    public boolean removeChild(NodeId nodeId) {
        if (children.isEmpty()) {
            return false;
        }
        NodeId removed = children.remove(nodeId.name());
        boolean changed = removed != null;
        if (changed) {
            setMutated();
        }
        return changed;
    }

    public int numBuckets() {
        return buckets.size();
    }

    public int numChildren() {
        return children.size();
    }

    public void forEachBucket(Consumer<? super TreeId> action) {
        this.buckets.forEach(action);
    }

    public void forEachChild(Consumer<? super NodeId> action) {
        this.children.values().forEach(action);
    }

    boolean isMutated() {
        return mutated;
    }

    Consumer<DAG> changeListener;

    protected void setMutated() {
        this.mutated = true;
        if (changeListener != null) {
            changeListener.accept(this);
        }
    }

    void setMutated(boolean mutated) {
        this.mutated = mutated;
    }

    public static void serialize(DAG dag, DataOutput out) throws IOException {
        final ObjectId treeId = dag.originalTreeId;
        final STATE state = dag.getState();
        final long childCount = dag.getChildCount();
        final Collection<NodeId> children = dag.children.values();
        final Set<TreeId> buckets = dag.buckets;

        treeId.writeTo(out);
        out.writeByte(state.ordinal());
        Varint.writeUnsignedVarLong(childCount, out);

        Varint.writeUnsignedVarInt(children.size(), out);
        Varint.writeUnsignedVarInt(buckets.size(), out);

        for (NodeId nodeid : children) {
            NodeId.write(nodeid, out);
        }
        for (TreeId tid : buckets) {
            byte[] bucketIndicesByDepth = tid.bucketIndicesByDepth;
            Varint.writeUnsignedVarInt(bucketIndicesByDepth.length, out);
            out.write(bucketIndicesByDepth);
        }
    }

    public static DAG deserialize(TreeId id, DataInput in) throws IOException {
        final ObjectId treeId = ObjectId.readFrom(in);
        final STATE state = STATE.values()[in.readByte() & 0xFF];
        final long childCount = Varint.readUnsignedVarLong(in);

        final int childrenSize = Varint.readUnsignedVarInt(in);
        final int bucketSize = Varint.readUnsignedVarInt(in);

        Set<NodeId> children = ImmutableSet.of();
        Set<TreeId> buckets = ImmutableSet.of();

        if (childrenSize > 0) {
            children = new HashSet<>();
            for (int i = 0; i < childrenSize; i++) {
                NodeId nid = NodeId.read(in);
                children.add(nid);
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

        DAG dag = new DAG(id, treeId, childCount, state, children, buckets);
        return dag;
    }
}
