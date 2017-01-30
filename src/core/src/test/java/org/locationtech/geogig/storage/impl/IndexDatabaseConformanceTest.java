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
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

/**
 * Base class to check an {@link IndexDatabase}'s implementation conformance to the interface
 * contract
 */
public abstract class IndexDatabaseConformanceTest extends ObjectStoreConformanceTest {

    protected IndexDatabase indexDb;

    @Override
    protected ObjectStore createOpen(Platform platform, Hints hints) {
        this.indexDb = createIndexDatabase(platform, hints);
        indexDb.open();
        return indexDb;
    }

    protected abstract IndexDatabase createIndexDatabase(Platform platform, Hints hints);

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
        IndexInfo index = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE, null);

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
        IndexInfo index = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE, null);

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
        IndexInfo index = indexDb.createIndexInfo(treeName, attributeName, IndexType.QUADTREE, null);

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

}
