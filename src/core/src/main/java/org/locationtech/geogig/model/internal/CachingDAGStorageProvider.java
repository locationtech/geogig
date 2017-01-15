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

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

final class CachingDAGStorageProvider implements DAGStorageProvider {

    private static final Logger LOG = LoggerFactory.getLogger(CachingDAGStorageProvider.class);

    /**
     * How many {@link Node}s to hold in memory before switching to the {@link #disk() persistent
     * store}
     */
    private static final int NODE_SWAP_THRESHOLD = 100_000;

    /**
     * DAGs up to this depth will be held in the heap store, past this depth in the temporary
     * persistent store
     */
    private final int HEAP_DEPTH_THRESHOLD = 3;

    private ObjectStore source;

    private TreeCache treeCache;

    private HeapDAGStorageProvider heap;

    private RocksdbDAGStorageProvider disk;

    private DAGStorageProvider nodeStore;

    private Lock swapLock = new ReentrantLock();

    private final Predicate<TreeId> heapTrees, diskTrees;

    CachingDAGStorageProvider(ObjectStore source) {
        this.source = source;
        this.treeCache = new TreeCache(source);
        heap = new HeapDAGStorageProvider(this.source, this.treeCache);
        nodeStore = heap;

        heapTrees = (id) -> id.depthLength() <= HEAP_DEPTH_THRESHOLD;
        diskTrees = (id) -> id.depthLength() > HEAP_DEPTH_THRESHOLD;
    }

    private DAGStorageProvider disk() {
        if (disk == null) {
            disk = new RocksdbDAGStorageProvider(this.source, this.treeCache);
        }
        return disk;
    }

    private DAGStorageProvider store(TreeId key) {
        if (heapTrees.apply(key)) {
            return heap();
        }
        return disk();
    }

    private HeapDAGStorageProvider heap() {
        return heap;
    }

    @Override
    public TreeCache getTreeCache() {
        return treeCache;
    }

    @Override
    public DAG getOrCreateTree(TreeId treeId, ObjectId originalTreeId) {
        return store(treeId).getOrCreateTree(treeId, originalTreeId);
    }

    @Override
    public Map<TreeId, DAG> getTrees(Set<TreeId> ids) {
        Map<TreeId, DAG> cached = heap.getTrees(Sets.filter(ids, heapTrees));
        Map<TreeId, DAG> res = cached;
        if (disk != null && cached.size() < ids.size()) {
            Map<TreeId, DAG> stored = disk.getTrees(Sets.filter(ids, diskTrees));
            res.putAll(stored);
        }
        return res;
    }

    @Override
    public void save(Map<TreeId, DAG> dags) {
        Map<TreeId, DAG> cached = Maps.filterKeys(dags, heapTrees);
        heap().save(cached);
        if (cached.size() < dags.size()) {
            disk().save(Maps.filterKeys(dags, diskTrees));
        }
    }

    @Override
    public Node getNode(NodeId nodeId) {
        return nodeStore.getNode(nodeId);
    }

    @Override
    public Map<NodeId, Node> getNodes(Set<NodeId> nodeIds) {
        return nodeStore.getNodes(nodeIds);
    }

    @Override
    public void saveNode(NodeId nodeId, Node node) {
        nodeStore.saveNode(nodeId, node);
        swapNodeStore();
    }

    @Override
    public void saveNodes(Map<NodeId, DAGNode> nodeMappings) {
        nodeStore.saveNodes(nodeMappings);
        swapNodeStore();
    }

    @Override
    public void dispose() {
        heap.dispose();
        if (disk != null) {
            disk.dispose();
        }
        heap = null;
        nodeStore = null;
        disk = null;
        treeCache = null;
        source = null;
    }

    @Override
    public RevTree getTree(ObjectId treeId) {
        return treeCache.getTree(treeId);
    }

    private void swapNodeStore() {
        if (nodeStore != heap) {
            return;
        }
        if (heap.nodeCount() < NODE_SWAP_THRESHOLD) {
            return;
        }
        swapLock.lock();
        try {
            if (nodeStore != heap) {
                // already swapped
                return;
            }
            LOG.debug("Switching to on disk mutable tree storage, reached threshold of {} nodes...",
                    heap.nodeCount());
            Preconditions.checkState(nodeStore == heap);

            Map<NodeId, DAGNode> nodes = heap().nodes;

            DAGStorageProvider largeStore = disk();
            try {
                largeStore.saveNodes(nodes);
                nodes.clear();
            } catch (RuntimeException e) {
                largeStore.dispose();
                throw e;
            }
            this.nodeStore = largeStore;
        } finally {
            swapLock.unlock();
        }
    }
}
