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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.ResolveTreeish;
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

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

public class UpdateIndexOpTest extends RepositoryTestCase {

    private IndexDatabase indexdb;

    private Node worldPointsLayer;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        Repository repository = getRepository();
        indexdb = repository.indexDatabase();
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

        assertNotEquals(RevTree.EMPTY_TREE_ID, worldPointsLayer.getObjectId());
    }

    private IndexInfo createIndex(@Nullable String... extraAttributes) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(IndexInfo.MD_QUAD_MAX_BOUNDS, new Envelope(-180, 180, -90, 90));
        if (extraAttributes != null && extraAttributes.length > 0) {
            metadata.put(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA, extraAttributes);
        }
        IndexInfo indexInfo;
        indexInfo = indexdb.createIndexInfo(worldPointsLayer.getName(), "geom", IndexType.QUADTREE,
                metadata);
        return indexInfo;
    }

    @Test
    public void testUpdateIndexAddAttributes() {
        createIndex();

        Index index = geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setExtraAttributes(Lists.newArrayList("x", "y"))//
                .call();

        IndexInfo indexInfo = indexdb.getIndexInfo(worldPointsLayer.getName(), "geom").get();
        assertEquals(indexInfo, index.info());
        assertEquals(worldPointsLayer.getName(), indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertEquals(2, indexInfo.getMetadata().size());
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertEquals(new Envelope(-180, 180, -90, 90),
                indexInfo.getMetadata().get(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        List<String> extraAttributes = Lists.newArrayList(
                (String[]) indexInfo.getMetadata().get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        assertEquals(2, extraAttributes.size());
        assertTrue(extraAttributes.contains("x"));
        assertTrue(extraAttributes.contains("y"));

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:" + worldPointsLayer.getName()).call().get();
        Optional<ObjectId> indexedTreeId = indexdb.resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), index.indexTreeId());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId, "x", "y");
    }

    @Test
    public void testUpdateIndexAddAttributesToExisting() {
        createIndex("x");

        Index index = geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setAttributeName("geom")//
                .setExtraAttributes(Lists.newArrayList("x", "y"))//
                .setAdd(true)//
                .call();

        IndexInfo indexInfo = indexdb.getIndexInfo(worldPointsLayer.getName(), "geom").get();
        assertEquals(indexInfo, index.info());
        assertEquals(worldPointsLayer.getName(), indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertEquals(2, indexInfo.getMetadata().size());
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertEquals(new Envelope(-180, 180, -90, 90),
                indexInfo.getMetadata().get(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        List<String> extraAttributes = Lists.newArrayList(
                (String[]) indexInfo.getMetadata().get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        assertEquals(2, extraAttributes.size());
        assertTrue(extraAttributes.contains("x"));
        assertTrue(extraAttributes.contains("y"));

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:" + worldPointsLayer.getName()).call().get();
        Optional<ObjectId> indexedTreeId = indexdb.resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), index.indexTreeId());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId, "x", "y");
    }

    @Test
    public void testUpdateIndexOverwriteExistingAttributes() {
        createIndex("x");

        Index index = geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setExtraAttributes(Lists.newArrayList("y"))//
                .setOverwrite(true)//
                .call();

        IndexInfo indexInfo = indexdb.getIndexInfo(worldPointsLayer.getName(), "geom").get();
        assertEquals(indexInfo, index.info());
        assertEquals(worldPointsLayer.getName(), indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertEquals(2, indexInfo.getMetadata().size());
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertEquals(new Envelope(-180, 180, -90, 90),
                indexInfo.getMetadata().get(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        List<String> extraAttributes = Lists.newArrayList(
                (String[]) indexInfo.getMetadata().get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        assertEquals(1, extraAttributes.size());
        assertTrue(extraAttributes.contains("y"));

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:" + worldPointsLayer.getName()).call().get();
        Optional<ObjectId> indexedTreeId = indexdb.resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), index.indexTreeId());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId, "y");
    }

    @Test
    public void testUpdateIndexRemoveExistingAttributes() {
        createIndex("x");

        Index index = geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setExtraAttributes(null)//
                .setOverwrite(true)//
                .call();

        IndexInfo indexInfo = indexdb.getIndexInfo(worldPointsLayer.getName(), "geom").get();
        assertEquals(indexInfo, index.info());
        assertEquals(worldPointsLayer.getName(), indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertEquals(1, indexInfo.getMetadata().size());
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertEquals(new Envelope(-180, 180, -90, 90),
                indexInfo.getMetadata().get(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertFalse(indexInfo.getMetadata().containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:" + worldPointsLayer.getName()).call().get();
        Optional<ObjectId> indexedTreeId = indexdb.resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), index.indexTreeId());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId);
    }

    @Test
    public void testUpdateIndexBounds() {
        IndexInfo info = createIndex("x");
        Envelope oldBounds = (Envelope) info.getMetadata().get(IndexInfo.MD_QUAD_MAX_BOUNDS);
        assertEquals(new Envelope(-180, 180, -90, 90), oldBounds);

        Envelope newBounds = new Envelope(-60, 60, -45, 45);

        Index index = geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setBounds(newBounds)//
                .call();

        IndexInfo indexInfo = indexdb.getIndexInfo(worldPointsLayer.getName(), "geom").get();
        assertEquals(indexInfo, index.info());
        assertEquals(worldPointsLayer.getName(), indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertEquals(2, indexInfo.getMetadata().size());
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertEquals(newBounds,
                indexInfo.getMetadata().get(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        List<String> extraAttributes = Lists.newArrayList(
                (String[]) indexInfo.getMetadata().get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        assertEquals(1, extraAttributes.size());
        assertTrue(extraAttributes.contains("x"));

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:" + worldPointsLayer.getName()).call().get();
        Optional<ObjectId> indexedTreeId = indexdb.resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), index.indexTreeId());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId);
    }

    @Test
    public void testUpdateIndexAttributesNoFlagSpecified() {
        createIndex("x");

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(
                "Extra attributes already exist on index, specify add or overwrite to update.");
        geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setExtraAttributes(Lists.newArrayList("y"))//
                .call();
    }

    @Test
    public void testUpdateIndexOverwriteSameAttribute() {
        createIndex("x");

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Nothing to update...");
        geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setExtraAttributes(Lists.newArrayList("x"))//
                .setOverwrite(true)//
                .call();
    }

    @Test
    public void testUpdateIndexAddSameAttribute() {
        createIndex("x", "y");

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Nothing to update...");
        geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setExtraAttributes(Lists.newArrayList("x"))//
                .setAdd(true)//
                .call();
    }

    @Test
    public void testUpdateIndexDoNothing() {
        createIndex("x");

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Nothing to update...");
        geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .call();
    }

    @Test
    public void testUpdateNoExistingIndex() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("A matching index could not be found.");
        geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setExtraAttributes(Lists.newArrayList("y"))//
                .call();
    }

    @Test
    public void testUpdateNoMatchingIndex() {
        indexdb.createIndexInfo(worldPointsLayer.getName(), "x", IndexType.QUADTREE, null);
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("A matching index could not be found.");
        geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setAttributeName("y")//
                .call();
    }

    @Test
    public void testUpdateMultipleMatchingIndexes() {
        indexdb.createIndexInfo(worldPointsLayer.getName(), "x", IndexType.QUADTREE, null);
        indexdb.createIndexInfo(worldPointsLayer.getName(), "y", IndexType.QUADTREE, null);
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(
                "Multiple indexes were found for the specified tree, please specify the attribute.");
        geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .call();
    }

    @Test
    public void testUpdateIndexNoTreeName() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Tree ref spec not provided.");
        geogig.command(UpdateIndexOp.class).call();
    }

    @Test
    public void testUpdateIndexWrongTreeName() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Can't find feature tree 'nonexistent'");
        geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec("nonexistent")//
                .call();
    }

    @Test
    public void testUpdateIndexFullHistory() {
        createIndex("x");

        Index index = geogig.command(UpdateIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setExtraAttributes(Lists.newArrayList("y"))//
                .setAdd(true)//
                .setIndexHistory(true)//
                .call();

        IndexInfo indexInfo = indexdb.getIndexInfo(worldPointsLayer.getName(), "geom").get();
        assertEquals(indexInfo, index.info());
        assertEquals(worldPointsLayer.getName(), indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertEquals(2, indexInfo.getMetadata().size());
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertEquals(new Envelope(-180, 180, -90, 90),
                indexInfo.getMetadata().get(IndexInfo.MD_QUAD_MAX_BOUNDS));
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        List<String> extraAttributes = Lists.newArrayList(
                (String[]) indexInfo.getMetadata().get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        assertEquals(2, extraAttributes.size());
        assertTrue(extraAttributes.contains("x"));
        assertTrue(extraAttributes.contains("y"));

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:" + worldPointsLayer.getName()).call().get();
        Optional<ObjectId> indexedTreeId = indexdb.resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), index.indexTreeId());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId, "x", "y");

        // make sure old commits are indexed
        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD~1:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId, "x", "y");

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD~1:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId, "x", "y");

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD~2:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId, "x", "y");

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("branch1:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId, "x", "y");
    }

    @Test
    public void testEqualIndexesWithDifferentExtraAttributesHashDifferently() {
        Index noExtraAtts = createAndBuildIndex();
        Index xExtraAtts = updateIndex(noExtraAtts.info().getTreeName(), "x");
        Index yExtraAtts = updateIndex(noExtraAtts.info().getTreeName(), "y");
        assertNotEquals(noExtraAtts, xExtraAtts);
        assertNotEquals(xExtraAtts, yExtraAtts);

        assertNotEquals(noExtraAtts.indexTreeId(), xExtraAtts.indexTreeId());
        assertNotEquals(xExtraAtts.indexTreeId(), yExtraAtts.indexTreeId());
    }

    private Index createAndBuildIndex(@Nullable String... extraAttributes) {
        IndexInfo indexInfo = createIndex(extraAttributes);
        Index index = updateIndex(indexInfo.getTreeName(), extraAttributes);
        return index;
    }

    private Index updateIndex(String treeName, @Nullable String... extraAttributes) {
        List<String> extraAtts = null;
        if (extraAttributes != null) {
            extraAtts = Lists.newArrayList(extraAttributes);
        }
        Index index = geogig.command(UpdateIndexOp.class)//
                .setOverwrite(true)//
                .setTreeRefSpec(treeName)//
                .setExtraAttributes(extraAtts)//
                .setBounds(new Envelope(-180, 180, -90, 90))//
                .call();
        return index;
    }
}
