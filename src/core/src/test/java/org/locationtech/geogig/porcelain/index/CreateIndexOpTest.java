/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.porcelain.index;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.index.BuildIndexOp;
import org.locationtech.geogig.plumbing.index.IndexTestSupport;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.RemoveOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.locationtech.jts.geom.Envelope;

public class CreateIndexOpTest extends RepositoryTestCase {

    private IndexDatabase indexdb;

    private Node worldPointsLayer;

    private RevTree worldPointsTree;

    protected @Override void setUpInternal() throws Exception {
        Repository repository = getRepository();
        indexdb = repository.context().indexDatabase();
        worldPointsLayer = IndexTestSupport.createWorldPointsLayer(repository).getNode();
        super.add();
        super.commit("created world points layer");
        String fid1 = IndexTestSupport.getPointFid(5, 10);
        repository.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid1)).call();
        repository.command(BranchCreateOp.class).setName("branch1").call();
        super.add();
        super.commit("deleted 5, 10");
        String fid2 = IndexTestSupport.getPointFid(35, -40);
        repository.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid2)).call();
        super.add();
        super.commit("deleted 35, -40");
        repository.command(CheckoutOp.class).setSource("branch1").call();
        String fid3 = IndexTestSupport.getPointFid(-10, 65);
        repository.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid3)).call();
        super.add();
        super.commit("deleted -10, 65");
        repository.command(CheckoutOp.class).setSource("master").call();

        this.worldPointsTree = repository.context().objectDatabase()
                .getTree(worldPointsLayer.getObjectId());

        assertNotEquals(RevTree.EMPTY_TREE_ID, worldPointsLayer.getObjectId());
    }

    @Test
    public void testCreateIndex() {
        Envelope bounds = new Envelope(-180, 180, -90, 90);
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put(IndexInfo.MD_QUAD_MAX_BOUNDS, bounds);
        Index index = repo.command(CreateIndexOp.class)//
                .setTreeName(worldPointsLayer.getName())//
                .setCanonicalTypeTree(worldPointsTree)//
                .setFeatureTypeId(worldPointsLayer.getMetadataId().get())//
                .setAttributeName("geom")//
                .setIndexType(IndexType.QUADTREE)//
                .setMetadata(metadata)//
                .call();

        IndexInfo indexInfo = indexdb.getIndexInfo(worldPointsLayer.getName(), "geom").get();
        assertEquals(indexInfo, index.info());
        assertEquals(worldPointsLayer.getName(), indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertEquals(1, indexInfo.getMetadata().size());
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertEquals(bounds, indexInfo.getMetadata().get(IndexInfo.MD_QUAD_MAX_BOUNDS));

        Optional<ObjectId> indexedTreeId = indexdb.resolveIndexedTree(indexInfo,
                worldPointsTree.getId());
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), index.indexTreeId());

        IndexTestSupport.verifyIndex(repo.context(), index.indexTreeId(), worldPointsTree.getId());
    }

    public @Test void testAbortsCleanly() {

        RuntimeException expected = new RuntimeException("expected");

        BuildIndexOp failingOp = mock(BuildIndexOp.class);
        when(failingOp.setIndex(any(IndexInfo.class))).thenThrow(expected);

        Envelope bounds = new Envelope(-180, 180, -90, 90);
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put(IndexInfo.MD_QUAD_MAX_BOUNDS, bounds);

        final String treeName = worldPointsLayer.getName();
        final String attributeName = "geom";
        try {
            CreateIndexOp createIndex = repo.command(CreateIndexOp.class);
            createIndex = spy(createIndex);
            doReturn(failingOp).when(createIndex).command(eq(BuildIndexOp.class));

            createIndex.setTreeName(treeName)//
                    .setCanonicalTypeTree(worldPointsTree)//
                    .setFeatureTypeId(worldPointsLayer.getMetadataId().get())//
                    .setAttributeName(attributeName)//
                    .setIndexType(IndexType.QUADTREE)//
                    .setMetadata(metadata)//
                    .call();
        } catch (Exception e) {
            assertSame(expected, e);
        }

        assertFalse(indexdb.getIndexInfo(treeName, attributeName).isPresent());
    }

    @Test
    public void testCreateIndexMetadata() {
        Envelope bounds = new Envelope(-180, 180, -90, 90);
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put(IndexInfo.MD_QUAD_MAX_BOUNDS, bounds);
        metadata.put("SomeKey", "SomeValue");
        Index index = repo.command(CreateIndexOp.class)//
                .setTreeName(worldPointsLayer.getName())//
                .setCanonicalTypeTree(worldPointsTree)//
                .setFeatureTypeId(worldPointsLayer.getMetadataId().get())//
                .setAttributeName("geom")//
                .setIndexType(IndexType.QUADTREE)//
                .setMetadata(metadata)//
                .call();

        IndexInfo indexInfo = indexdb.getIndexInfo(worldPointsLayer.getName(), "geom").get();
        assertEquals(indexInfo, index.info());
        assertEquals(worldPointsLayer.getName(), indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertEquals(2, indexInfo.getMetadata().size());
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertEquals(bounds, indexInfo.getMetadata().get(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertTrue(indexInfo.getMetadata().containsKey("SomeKey"));
        assertEquals("SomeValue", indexInfo.getMetadata().get("SomeKey"));

        Optional<ObjectId> indexedTreeId = indexdb.resolveIndexedTree(indexInfo,
                worldPointsTree.getId());
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), index.indexTreeId());

        IndexTestSupport.verifyIndex(repo.context(), index.indexTreeId(), worldPointsTree.getId());
    }

    @Test
    public void testCreateIndexFullHistory() {
        Envelope bounds = new Envelope(-180, 180, -90, 90);
        String[] extraAttributes = new String[] { "x" };
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put(IndexInfo.MD_QUAD_MAX_BOUNDS, bounds);
        metadata.put(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA, extraAttributes);
        Index index = repo.command(CreateIndexOp.class)//
                .setTreeName(worldPointsLayer.getName())//
                .setCanonicalTypeTree(worldPointsTree)//
                .setFeatureTypeId(worldPointsLayer.getMetadataId().get())//
                .setAttributeName("geom")//
                .setIndexType(IndexType.QUADTREE)//
                .setMetadata(metadata)//
                .setIndexHistory(true)//
                .call();

        IndexInfo indexInfo = indexdb.getIndexInfo(worldPointsLayer.getName(), "geom").get();
        assertEquals(indexInfo, index.info());
        assertEquals(worldPointsLayer.getName(), indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertEquals(2, indexInfo.getMetadata().size());
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertEquals(bounds, indexInfo.getMetadata().get(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        assertEquals(extraAttributes,
                indexInfo.getMetadata().get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));

        Optional<ObjectId> indexedTreeId = indexdb.resolveIndexedTree(indexInfo,
                worldPointsTree.getId());
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), index.indexTreeId());

        IndexTestSupport.verifyIndex(repo.context(), index.indexTreeId(), worldPointsTree.getId(),
                "x");

        // make sure old commits are indexed
        ObjectId canonicalFeatureTreeId = repo.command(ResolveTreeish.class)
                .setTreeish("HEAD~1:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), indexedTreeId.get(), canonicalFeatureTreeId,
                "x");

        canonicalFeatureTreeId = repo.command(ResolveTreeish.class)
                .setTreeish("HEAD~1:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), indexedTreeId.get(), canonicalFeatureTreeId,
                "x");

        canonicalFeatureTreeId = repo.command(ResolveTreeish.class)
                .setTreeish("HEAD~2:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), indexedTreeId.get(), canonicalFeatureTreeId,
                "x");

        canonicalFeatureTreeId = repo.command(ResolveTreeish.class)
                .setTreeish("branch1:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), indexedTreeId.get(), canonicalFeatureTreeId,
                "x");
    }

    @Test
    public void testCreateIndexNoTreeName() {
        Exception e = assertThrows(IllegalArgumentException.class, repo.command(CreateIndexOp.class)//
                .setCanonicalTypeTree(worldPointsTree)//
                .setFeatureTypeId(worldPointsLayer.getMetadataId().get())//
                .setAttributeName("geom")//
                .setIndexType(IndexType.QUADTREE)//
        ::call);
        assertThat(e.getMessage(), containsString("treeName not provided"));
    }

    @Test
    public void testCreateIndexNoCanonicalTypeTree() {
        Exception e = assertThrows(IllegalArgumentException.class, repo.command(CreateIndexOp.class)//
                .setTreeName(worldPointsLayer.getName())//
                .setFeatureTypeId(worldPointsLayer.getMetadataId().get())//
                .setAttributeName("geom")//
                .setIndexType(IndexType.QUADTREE)//
        ::call);
        assertThat(e.getMessage(), containsString("canonicalTypeTree not provided"));
    }

    @Test
    public void testCreateIndexNoFeatureTypeId() {
        Exception e = assertThrows(IllegalArgumentException.class, repo.command(CreateIndexOp.class)//
                .setTreeName(worldPointsLayer.getName())//
                .setCanonicalTypeTree(worldPointsTree)//
                .setAttributeName("geom")//
                .setIndexType(IndexType.QUADTREE)//
        ::call);
        assertThat(e.getMessage(), containsString("featureTypeId not provided"));
    }

    @Test
    public void testCreateIndexNoAttributeName() {
        Exception e = assertThrows(IllegalArgumentException.class, repo.command(CreateIndexOp.class)//
                .setTreeName(worldPointsLayer.getName())//
                .setCanonicalTypeTree(worldPointsTree)//
                .setFeatureTypeId(worldPointsLayer.getMetadataId().get())//
                .setIndexType(IndexType.QUADTREE)//
        ::call);
        assertThat(e.getMessage(), containsString("attributeName not provided"));
    }

    @Test
    public void testCreateIndexNoIndexType() {
        Exception e = assertThrows(IllegalArgumentException.class, repo.command(CreateIndexOp.class)//
                .setTreeName(worldPointsLayer.getName())//
                .setCanonicalTypeTree(worldPointsTree)//
                .setFeatureTypeId(worldPointsLayer.getMetadataId().get())//
                .setAttributeName("geom")//
        ::call);
        assertThat(e.getMessage(), containsString("indexType not provided"));
    }
}
