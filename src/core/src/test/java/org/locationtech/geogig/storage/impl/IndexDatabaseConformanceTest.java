/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.IndexDatabase.IndexTreeMapping;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapIndexDatabase;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

/**
 * Base class to check an {@link IndexDatabase}'s implementation conformance to the interface
 * contract
 */
public abstract class IndexDatabaseConformanceTest extends ObjectStoreConformanceTest {

    protected IndexDatabase indexDb;

    @Override
    protected ObjectStore createOpen() throws IOException {
        this.indexDb = createIndexDatabase(false);
        indexDb.open();
        return indexDb;
    }

    protected abstract IndexDatabase createIndexDatabase(boolean readOnly) throws IOException;

    @Test
    public void testCreateIndex() {
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("meta1", 5L);
        metadata.put("meta2", "someValue");
        String treeName = "tree";
        String attributeName = "attribute";
        IndexInfo index = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE,
                metadata);

        assertEquals(treeName, index.getTreeName());
        assertEquals(attributeName, index.getAttributeName());
        assertEquals(IndexType.QUADTREE, index.getIndexType());
        assertEquals(metadata, index.getMetadata());
        assertEquals(IndexInfo.getIndexId(treeName, attributeName), index.getId());
    }

    @Test
    public void testUpdateIndexUpdateMetadata() {
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("meta1", 5L);
        metadata.put("meta2", "someValue");
        String treeName = "tree";
        String attributeName = "attribute";
        IndexInfo index = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE,
                metadata);

        assertEquals(treeName, index.getTreeName());
        assertEquals(attributeName, index.getAttributeName());
        assertEquals(IndexType.QUADTREE, index.getIndexType());
        assertEquals(metadata, index.getMetadata());
        assertEquals(IndexInfo.getIndexId(treeName, attributeName), index.getId());

        metadata.put("meta1", 3L);
        metadata.remove("meta2");

        index = indexDb.updateIndexInfo(treeName, attributeName, IndexType.QUADTREE, metadata);

        assertEquals(treeName, index.getTreeName());
        assertEquals(attributeName, index.getAttributeName());
        assertEquals(IndexType.QUADTREE, index.getIndexType());
        assertEquals(metadata, index.getMetadata());
        assertEquals(IndexInfo.getIndexId(treeName, attributeName), index.getId());

        index = indexDb.getIndexInfo(treeName, attributeName).get();

        assertEquals(treeName, index.getTreeName());
        assertEquals(attributeName, index.getAttributeName());
        assertEquals(IndexType.QUADTREE, index.getIndexType());
        assertEquals(metadata, index.getMetadata());
        assertEquals(IndexInfo.getIndexId(treeName, attributeName), index.getId());
    }

    @Test
    public void testUpdateIndexAddMetadata() {
        String treeName = "tree";
        String attributeName = "attribute";
        IndexInfo index = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE,
                null);

        assertEquals(treeName, index.getTreeName());
        assertEquals(attributeName, index.getAttributeName());
        assertEquals(IndexType.QUADTREE, index.getIndexType());
        assertEquals(ImmutableMap.of(), index.getMetadata());
        assertEquals(IndexInfo.getIndexId(treeName, attributeName), index.getId());

        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("meta1", 5L);
        metadata.put("meta2", "someValue");

        index = indexDb.updateIndexInfo(treeName, attributeName, IndexType.QUADTREE, metadata);

        assertEquals(treeName, index.getTreeName());
        assertEquals(attributeName, index.getAttributeName());
        assertEquals(IndexType.QUADTREE, index.getIndexType());
        assertEquals(metadata, index.getMetadata());
        assertEquals(IndexInfo.getIndexId(treeName, attributeName), index.getId());

        index = indexDb.getIndexInfo(treeName, attributeName).get();

        assertEquals(treeName, index.getTreeName());
        assertEquals(attributeName, index.getAttributeName());
        assertEquals(IndexType.QUADTREE, index.getIndexType());
        assertEquals(metadata, index.getMetadata());
        assertEquals(IndexInfo.getIndexId(treeName, attributeName), index.getId());
    }

    @Test
    public void testUpdateIndexRemoveMetadata() {
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("meta1", 5L);
        metadata.put("meta2", "someValue");
        String treeName = "tree";
        String attributeName = "attribute";
        IndexInfo index = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE,
                metadata);

        assertEquals(treeName, index.getTreeName());
        assertEquals(attributeName, index.getAttributeName());
        assertEquals(IndexType.QUADTREE, index.getIndexType());
        assertEquals(metadata, index.getMetadata());
        assertEquals(IndexInfo.getIndexId(treeName, attributeName), index.getId());

        index = indexDb.updateIndexInfo(treeName, attributeName, IndexType.QUADTREE, null);

        assertEquals(treeName, index.getTreeName());
        assertEquals(attributeName, index.getAttributeName());
        assertEquals(IndexType.QUADTREE, index.getIndexType());
        assertEquals(ImmutableMap.of(), index.getMetadata());
        assertEquals(IndexInfo.getIndexId(treeName, attributeName), index.getId());

        index = indexDb.getIndexInfo(treeName, attributeName).get();

        assertEquals(treeName, index.getTreeName());
        assertEquals(attributeName, index.getAttributeName());
        assertEquals(IndexType.QUADTREE, index.getIndexType());
        assertEquals(ImmutableMap.of(), index.getMetadata());
        assertEquals(IndexInfo.getIndexId(treeName, attributeName), index.getId());
    }

    @Test
    public void testGetIndex() {
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("meta1", 5L);
        metadata.put("meta2", "someValue");
        String treeName = "tree";
        String attributeName = "attribute";
        indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE, metadata);

        indexDb.createIndexInfo("otherTree", "someAttribute", IndexType.QUADTREE, null);

        Optional<IndexInfo> indexOpt = indexDb.getIndexInfo(treeName, attributeName);
        assertTrue(indexOpt.isPresent());
        IndexInfo index = indexOpt.get();
        assertEquals(treeName, index.getTreeName());
        assertEquals(attributeName, index.getAttributeName());
        assertEquals(IndexType.QUADTREE, index.getIndexType());
        assertEquals(metadata, index.getMetadata());
        assertEquals(IndexInfo.getIndexId(treeName, attributeName), index.getId());
    }

    @Test
    public void testGetIndexesTreeName() {
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("meta1", 5L);
        metadata.put("meta2", "someValue");
        String treeName = "tree";
        String attributeName = "attribute";
        IndexInfo index1 = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE,
                metadata);

        String attributeName2 = "attribute2";
        IndexInfo index2 = indexDb.createIndexInfo(treeName, attributeName2, IndexType.QUADTREE,
                null);

        IndexInfo index3 = indexDb.createIndexInfo("otherTree", "someAttribute", IndexType.QUADTREE,
                null);

        List<IndexInfo> indexes = indexDb.getIndexInfos(treeName);
        assertEquals(2, indexes.size());

        assertTrue(indexes.contains(index1));
        assertTrue(indexes.contains(index2));
        assertFalse(indexes.contains(index3));
    }

    @Test
    public void testGetIndexes() {
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("meta1", 5L);
        metadata.put("meta2", "someValue");
        String treeName = "tree";
        String attributeName = "attribute";
        IndexInfo index1 = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE,
                metadata);

        String attributeName2 = "attribute2";
        IndexInfo index2 = indexDb.createIndexInfo(treeName, attributeName2, IndexType.QUADTREE,
                null);

        IndexInfo index3 = indexDb.createIndexInfo("otherTree", "someAttribute", IndexType.QUADTREE,
                null);

        List<IndexInfo> indexes = indexDb.getIndexInfos();
        assertEquals(3, indexes.size());
        assertTrue(indexes.contains(index1));
        assertTrue(indexes.contains(index2));
        assertTrue(indexes.contains(index3));
    }

    @Test
    public void testGetIndexNotPresent() {
        String treeName = "tree";
        String attributeName = "attribute";

        Optional<IndexInfo> indexOpt = indexDb.getIndexInfo(treeName, attributeName);
        assertFalse(indexOpt.isPresent());

        indexDb.createIndexInfo("otherTree", "someAttribute", IndexType.QUADTREE, null);

        indexOpt = indexDb.getIndexInfo(treeName, attributeName);
        assertFalse(indexOpt.isPresent());
    }

    @Test
    public void testGetIndexesNoneTreeName() {
        String treeName = "tree";

        List<IndexInfo> indexes = indexDb.getIndexInfos(treeName);
        assertTrue(indexes.isEmpty());

        indexDb.createIndexInfo("otherTree", "someAttribute", IndexType.QUADTREE, null);

        indexes = indexDb.getIndexInfos(treeName);
        assertTrue(indexes.isEmpty());
    }

    @Test
    public void testGetIndexesNone() {
        List<IndexInfo> indexes = indexDb.getIndexInfos();
        assertTrue(indexes.isEmpty());
    }

    @Test
    public void testNullMetadata() {
        String treeName = "tree";
        String attributeName = "attribute";
        IndexInfo index = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE,
                null);

        assertEquals(treeName, index.getTreeName());
        assertEquals(attributeName, index.getAttributeName());
        assertEquals(IndexType.QUADTREE, index.getIndexType());
        assertTrue(index.getMetadata().isEmpty());
        assertEquals(IndexInfo.getIndexId(treeName, attributeName), index.getId());

        Optional<IndexInfo> indexOpt = indexDb.getIndexInfo(treeName, attributeName);
        assertTrue(indexOpt.isPresent());
        index = indexOpt.get();
        assertEquals(treeName, index.getTreeName());
        assertEquals(attributeName, index.getAttributeName());
        assertEquals(IndexType.QUADTREE, index.getIndexType());
        assertTrue(index.getMetadata().isEmpty());
        assertEquals(IndexInfo.getIndexId(treeName, attributeName), index.getId());
    }

    @Test
    public void testIndexTreeMappings() {
        String treeName = "tree";
        String attributeName = "attribute";
        IndexInfo index = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE,
                null);

        ObjectId originalTreeId = RevObjectTestSupport.hashString("fake1");
        ObjectId indexedTreeId = RevObjectTestSupport.hashString("fake2");
        ObjectId notIndexedId = RevObjectTestSupport.hashString("fake3");

        indexDb.addIndexedTree(index, originalTreeId, indexedTreeId);

        Optional<ObjectId> resolvedId = indexDb.resolveIndexedTree(index, originalTreeId);
        assertTrue(resolvedId.isPresent());

        assertEquals(indexedTreeId, resolvedId.get());

        resolvedId = indexDb.resolveIndexedTree(index, notIndexedId);
        assertFalse(resolvedId.isPresent());
    }

    @Test
    public void testUpdateIndexTreeMappings() {
        String treeName = "tree";
        String attributeName = "attribute";
        IndexInfo index = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE,
                null);

        ObjectId originalTreeId = RevObjectTestSupport.hashString("fake1");
        ObjectId indexedTreeId = RevObjectTestSupport.hashString("fake2");
        ObjectId notIndexedId = RevObjectTestSupport.hashString("fake3");

        indexDb.addIndexedTree(index, originalTreeId, indexedTreeId);

        Optional<ObjectId> resolvedId = indexDb.resolveIndexedTree(index, originalTreeId);
        assertTrue(resolvedId.isPresent());

        assertEquals(indexedTreeId, resolvedId.get());

        resolvedId = indexDb.resolveIndexedTree(index, notIndexedId);
        assertFalse(resolvedId.isPresent());

        indexDb.addIndexedTree(index, originalTreeId, notIndexedId);

        resolvedId = indexDb.resolveIndexedTree(index, originalTreeId);
        assertTrue(resolvedId.isPresent());

        assertEquals(notIndexedId, resolvedId.get());

        resolvedId = indexDb.resolveIndexedTree(index, indexedTreeId);
        assertFalse(resolvedId.isPresent());
    }

    @Test
    public void testDropIndex() {
        String treeName = "tree";
        String attributeName = "attribute";
        IndexInfo index = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE,
                null);

        String attributeName2 = "attribute2";
        IndexInfo index2 = indexDb.createIndexInfo(treeName, attributeName2, IndexType.QUADTREE,
                null);

        ObjectId originalTreeId1 = RevObjectTestSupport.hashString("fake1");
        ObjectId originalTreeId2 = RevObjectTestSupport.hashString("fake2");
        ObjectId indexedTreeId1 = RevObjectTestSupport.hashString("fake3");
        ObjectId indexedTreeId2 = RevObjectTestSupport.hashString("fake4");

        indexDb.addIndexedTree(index, originalTreeId1, indexedTreeId1);
        indexDb.addIndexedTree(index, originalTreeId2, indexedTreeId2);
        indexDb.addIndexedTree(index2, originalTreeId1, indexedTreeId1);
        indexDb.addIndexedTree(index2, originalTreeId2, indexedTreeId2);

        Optional<ObjectId> resolvedId = indexDb.resolveIndexedTree(index, originalTreeId1);
        assertTrue(resolvedId.isPresent());
        assertEquals(indexedTreeId1, resolvedId.get());

        resolvedId = indexDb.resolveIndexedTree(index, originalTreeId2);
        assertTrue(resolvedId.isPresent());
        assertEquals(indexedTreeId2, resolvedId.get());

        resolvedId = indexDb.resolveIndexedTree(index2, originalTreeId1);
        assertTrue(resolvedId.isPresent());
        assertEquals(indexedTreeId1, resolvedId.get());

        resolvedId = indexDb.resolveIndexedTree(index2, originalTreeId2);
        assertTrue(resolvedId.isPresent());
        assertEquals(indexedTreeId2, resolvedId.get());

        boolean dropped = indexDb.dropIndex(index);
        assertTrue(dropped);

        resolvedId = indexDb.resolveIndexedTree(index, originalTreeId1);
        assertFalse(resolvedId.isPresent());

        resolvedId = indexDb.resolveIndexedTree(index, originalTreeId2);
        assertFalse(resolvedId.isPresent());

        resolvedId = indexDb.resolveIndexedTree(index2, originalTreeId1);
        assertTrue(resolvedId.isPresent());
        assertEquals(indexedTreeId1, resolvedId.get());

        resolvedId = indexDb.resolveIndexedTree(index2, originalTreeId2);
        assertTrue(resolvedId.isPresent());
        assertEquals(indexedTreeId2, resolvedId.get());

        Optional<IndexInfo> indexInfo = indexDb.getIndexInfo(treeName, attributeName);
        assertFalse(indexInfo.isPresent());

        indexInfo = indexDb.getIndexInfo(treeName, attributeName2);
        assertTrue(indexInfo.isPresent());

        dropped = indexDb.dropIndex(index);
        assertFalse(dropped);
    }

    @Test
    public void testClearIndex() {
        String treeName = "tree";
        String attributeName = "attribute";
        IndexInfo index = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE,
                null);

        ObjectId originalTreeId1 = RevObjectTestSupport.hashString("fake1");
        ObjectId originalTreeId2 = RevObjectTestSupport.hashString("fake2");
        ObjectId indexedTreeId1 = RevObjectTestSupport.hashString("fake3");
        ObjectId indexedTreeId2 = RevObjectTestSupport.hashString("fake4");

        indexDb.addIndexedTree(index, originalTreeId1, indexedTreeId1);
        indexDb.addIndexedTree(index, originalTreeId2, indexedTreeId2);

        Optional<ObjectId> resolvedId = indexDb.resolveIndexedTree(index, originalTreeId1);
        assertTrue(resolvedId.isPresent());

        assertEquals(indexedTreeId1, resolvedId.get());

        resolvedId = indexDb.resolveIndexedTree(index, originalTreeId2);
        assertTrue(resolvedId.isPresent());

        assertEquals(indexedTreeId2, resolvedId.get());

        indexDb.clearIndex(index);
        resolvedId = indexDb.resolveIndexedTree(index, originalTreeId1);
        assertFalse(resolvedId.isPresent());

        resolvedId = indexDb.resolveIndexedTree(index, originalTreeId2);
        assertFalse(resolvedId.isPresent());

    }

    public @Test void testCopyIndexesTo() {
        HeapIndexDatabase target = new HeapIndexDatabase();
        target.open();
        testCopyIndexesTo(target);
    }

    protected void testCopyIndexesTo(IndexDatabase target) {
        IndexInfo index1 = createIndex("Layer1", this.indexDb);
        this.indexDb.copyIndexesTo(target);
        Optional<IndexInfo> indexInfo = target.getIndexInfo(index1.getTreeName(),
                index1.getAttributeName());
        assertNotNull(indexInfo);
        assertTrue(indexInfo.isPresent());
        assertEquals(index1, indexInfo.get());

        Set<IndexTreeMapping> expectedMappings = toSet(this.indexDb.resolveIndexedTrees(index1));
        Set<IndexTreeMapping> actualMappings = toSet(target.resolveIndexedTrees(index1));
        assertEquals(expectedMappings.size(), actualMappings.size());
        assertEquals(expectedMappings, actualMappings);

        Set<ObjectId> expectedIndexTrees = verifyAllReachableTrees(this.indexDb, expectedMappings);
        Set<ObjectId> actualIndexTrees = verifyAllReachableTrees(target, actualMappings);
        assertEquals(expectedIndexTrees.size(), actualIndexTrees.size());
        assertEquals(expectedIndexTrees, actualIndexTrees);
    }

    private Set<IndexTreeMapping> toSet(AutoCloseableIterator<IndexTreeMapping> mappings) {
        try {
            return Sets.newHashSet(mappings);
        } finally {
            mappings.close();
        }
    }

    private IndexInfo createIndex(String treeName, IndexDatabase target) {

        IndexInfo index = target.createIndexInfo(treeName, "the_geom", IndexType.QUADTREE, null);

        Envelope maxBounds = new Envelope(-180, 180, -90, 90);
        Envelope bounds = new Envelope();
        RevTree tree = RevTree.EMPTY;
        for (int y = -90; y <= 90; y += 5) {
            RevTreeBuilder builder = RevTreeBuilder.quadBuilder(target, target, tree, maxBounds);
            for (int x = -180; x <= 180; x += 5) {
                bounds.init(x, x, y, y);
                String name = String.format("%d_%d", x, y);
                ObjectId oid = RevObjectTestSupport.hashString(name);
                Node node = RevObjectFactory.defaultInstance().createNode(name, oid, ObjectId.NULL,
                        TYPE.FEATURE, bounds, null);
                builder.put(node);
            }
            tree = builder.build();
            ObjectId originalTree = RevObjectTestSupport.hashString("fake" + tree.getId());
            target.addIndexedTree(index, originalTree, tree.getId());
        }
        return index;
    }

    private Set<ObjectId> verifyAllReachableTrees(IndexDatabase db,
            Set<IndexTreeMapping> mappings) {
        Set<ObjectId> allIds = new HashSet<>();
        mappings.stream().map(m -> m.indexTree)
                .forEach(id -> allIds.addAll(verifyAllReachableTrees(db, id)));
        return allIds;
    }

    public static Set<ObjectId> verifyAllReachableTrees(ObjectStore store, ObjectId treeId) {
        Set<ObjectId> allIds = new HashSet<>();
        RevTree tree = store.getTree(treeId);
        allIds.add(tree.getId());
        tree.trees()
                .forEach(node -> allIds.addAll(verifyAllReachableTrees(store, node.getObjectId())));

        tree.getBuckets()
                .forEach(b -> allIds.addAll(verifyAllReachableTrees(store, b.getObjectId())));

        return allIds;
    }
}
