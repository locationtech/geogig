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

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class DAG implements Cloneable, Serializable {

    private static final long serialVersionUID = 1L;

    public static enum STATE {
        /**
         * The DAG is just created and has not been mirrored to the original RevTree
         */
        INITIALIZED,
        /**
         * The DAG structure mirrors the original RevTree
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
    private ObjectId originalTreeId;

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
        c.init(this);
        return c;
    }

    public void init(DAG from) {
        this.originalTreeId = from.originalTreeId;
        this.setTotalChildCount(from.childCount);
        this.state = from.state;
        this.children = new HashMap<>(from.children);
        this.buckets = new HashSet<>(from.buckets);
        this.mutated = from.mutated;
    }

    public DAG(TreeId id) {
        this.id = id;
        this.children = ImmutableMap.of();
        this.originalTreeId = RevTree.EMPTY_TREE_ID;
        this.state = STATE.INITIALIZED;
    }

    public DAG(final TreeId id, final ObjectId originalTreeId, long childCount, STATE state,
            Map<String, NodeId> children, Set<TreeId> buckets) {
        this.id = id;
        this.originalTreeId = originalTreeId;
        this.setTotalChildCount(childCount);
        this.state = state;
        this.children = children;
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

    public void reset(ObjectId originalTreeId) {
        clearChildren();
        clearBuckets();
        this.childCount = 0L;
        this.originalTreeId = originalTreeId;
        this.state = STATE.INITIALIZED;
        this.mutated = true;
    }

    public TreeId getId() {
        return id;
    }

    public ObjectId originalTreeId() {
        return originalTreeId;
    }

    public void setOriginalTreeId(ObjectId treeId) {
        if (!treeId.equals(originalTreeId)) {
            this.originalTreeId = treeId;
            setMutated();
        }
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
        return equal(originalTreeId, d.originalTreeId)
                && equal(getTotalChildCount(), d.getTotalChildCount()) && equal(state, d.state)
                && equal(children, d.children) && equal(buckets, d.buckets);
    }

    @Override
    public String toString() {
        String revTreeId = originalTreeId.equals(RevTree.EMPTY_TREE_ID) ? "EMPTY"
                : originalTreeId.toString().substring(0, 8);
        return String.format(
                "DAG[id:%s, orig:%s, status: %s, size: %,d, children: %,d, buckets: %,d)[children: %s, buckets: %s]",
                id, revTreeId, state, childCount, children.size(), buckets.size(), children,
                buckets);
    }

    public boolean isEmpty() {
        return children.isEmpty() && buckets.isEmpty();
    }

    public long getTotalChildCount() {
        return childCount;
    }

    public void setTotalChildCount(long childCount) {
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

    public List<TreeId> bucketList() {
        return Lists.newArrayList(buckets);
    }

    public List<NodeId> childrenList() {
        return Lists.newArrayList(children.values());
    }

    public void removeBucket(TreeId id) {
        buckets.remove(id);
    }

    public boolean containsBucket(TreeId bucketId) {
        boolean contains = this.buckets.contains(bucketId);
        return contains;
    }

    public boolean containsNode(NodeId node) {
        return children.containsKey(node.name());
    }

    public void setInitialized() {
        this.state = STATE.INITIALIZED;
    }
}
