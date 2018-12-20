/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.porcelain.index.CreateQuadTree;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

public class UpdateIndexTest extends AbstractIndexWebOpTest {

    @Override
    protected String getRoute() {
        return "update";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return UpdateIndex.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("treeRefSpec", "points", "geometryAttributeName",
                "the_geom", "indexHistory", "true", "extraAttributes", "sp", "extraAttributes",
                "ip", "add", "true", "overwrite", "true");

        UpdateIndex op = (UpdateIndex) buildCommand(options);
        assertEquals("points", op.treeRefSpec);
        assertEquals("the_geom", op.geometryAttributeName);
        assertTrue(op.indexHistory);
        assertTrue(op.overwrite);
        assertTrue(op.add);
        assertTrue(op.extraAttributes.contains("sp"));
        assertTrue(op.extraAttributes.contains("ip"));
        assertEquals(2, op.extraAttributes.size());
    }

    @Test
    public void testUpdateIndex() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        geogig.command(CreateQuadTree.class).setTreeRefSpec("Points").call();

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:Points").call().get();

        ParameterSet options = TestParams.of("treeRefSpec", "Points", "extraAttributes", "sp");

        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject index = response.getJsonObject("index");
        assertEquals("Points", index.getString("treeName"));
        assertEquals("geom", index.getString("attributeName"));
        assertEquals("QUADTREE", index.getString("indexType"));
        assertNotNull(index.getString("bounds"));
        JsonArray extraAttributesArr = index.getJsonArray("extraAttribute");
        assertEquals(1, extraAttributesArr.size());
        assertEquals("sp", extraAttributesArr.getString(0));
        ObjectId treeId = ObjectId.valueOf(response.getString("indexedTreeId"));

        IndexInfo indexInfo = geogig.indexDatabase().getIndexInfo("Points", "geom").get();
        assertEquals("Points", indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        String[] extraAttributes = (String[]) indexInfo.getMetadata()
                .get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
        assertEquals(1, extraAttributes.length);
        assertEquals("sp", extraAttributes[0]);

        Optional<ObjectId> indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), treeId);
    }

    @Test
    public void testUpdateIndexFullHistory() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        geogig.command(CreateQuadTree.class).setTreeRefSpec("Points").call();

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:Points").call().get();

        ParameterSet options = TestParams.of("treeRefSpec", "Points", "indexHistory", "true",
                "extraAttributes", "sp");

        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject index = response.getJsonObject("index");
        assertEquals("Points", index.getString("treeName"));
        assertEquals("geom", index.getString("attributeName"));
        assertEquals("QUADTREE", index.getString("indexType"));
        assertNotNull(index.getString("bounds"));
        JsonArray extraAttributesArr = index.getJsonArray("extraAttribute");
        assertEquals(1, extraAttributesArr.size());
        assertEquals("sp", extraAttributesArr.getString(0));
        ObjectId treeId = ObjectId.valueOf(response.getString("indexedTreeId"));

        IndexInfo indexInfo = geogig.indexDatabase().getIndexInfo("Points", "geom").get();
        assertEquals("Points", indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        String[] extraAttributes = (String[]) indexInfo.getMetadata()
                .get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
        assertEquals(1, extraAttributes.length);
        assertEquals("sp", extraAttributes[0]);

        Optional<ObjectId> indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), treeId);

        // make sure old commits are indexed
        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class).setTreeish("HEAD~1:Points")
                .call().get();
        indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class).setTreeish("branch1:Points")
                .call().get();
        indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class).setTreeish("branch2:Points")
                .call().get();
        indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());
    }

    @Test
    public void testUpdateIndexBounds() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        geogig.command(CreateQuadTree.class).setTreeRefSpec("Points").call();

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:Points").call().get();

        ParameterSet options = TestParams.of("treeRefSpec", "Points", "indexHistory", "true",
                "bounds", "-60,-45,60,45");

        buildCommand(options).run(testContext.get());

        Envelope expectedBounds = new Envelope(-60, 60, -45, 45);

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject index = response.getJsonObject("index");
        assertEquals("Points", index.getString("treeName"));
        assertEquals("geom", index.getString("attributeName"));
        assertEquals("QUADTREE", index.getString("indexType"));
        assertEquals(expectedBounds.toString(), index.getString("bounds"));
        ObjectId treeId = ObjectId.valueOf(response.getString("indexedTreeId"));

        IndexInfo indexInfo = geogig.indexDatabase().getIndexInfo("Points", "geom").get();
        assertEquals("Points", indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertFalse(indexInfo.getMetadata().containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));

        Optional<ObjectId> indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), treeId);

        // make sure old commits are indexed
        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class).setTreeish("HEAD~1:Points")
                .call().get();
        indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class).setTreeish("branch1:Points")
                .call().get();
        indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class).setTreeish("branch2:Points")
                .call().get();
        indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());
    }

    @Test
    public void testUpdateIndexExistingAttributes() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        geogig.command(CreateQuadTree.class).setTreeRefSpec("Points")
                .setExtraAttributes(Lists.newArrayList("sp")).call();

        ParameterSet options = TestParams.of("treeRefSpec", "Points", "extraAttributes", "ip");

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage(
                "Extra attributes already exist on index, specify add or overwrite to update.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testUpdateNonexistentIndex() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("treeRefSpec", "Points", "extraAttributes", "ip");

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("A matching index could not be found.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testUpdateIndexWrongAttribute() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        geogig.command(CreateQuadTree.class).setTreeRefSpec("Points")
                .setExtraAttributes(Lists.newArrayList("sp")).call();

        ParameterSet options = TestParams.of("treeRefSpec", "Points", "geometryAttributeName", "sp",
                "extraAttributes", "ip");

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("A matching index could not be found.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testUpdateIndexNothingToChange() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        geogig.command(CreateQuadTree.class).setTreeRefSpec("Points")
                .setExtraAttributes(Lists.newArrayList("sp")).call();

        ParameterSet options = TestParams.of("treeRefSpec", "Points");

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("Nothing to update...");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testUpdateIndexExistingAttributesAdd() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:Points").call().get();

        geogig.command(CreateQuadTree.class).setTreeRefSpec("Points")
                .setExtraAttributes(Lists.newArrayList("sp")).call();

        ParameterSet options = TestParams.of("treeRefSpec", "Points", "extraAttributes", "ip",
                "add", "true");

        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject index = response.getJsonObject("index");
        assertEquals("Points", index.getString("treeName"));
        assertEquals("geom", index.getString("attributeName"));
        assertEquals("QUADTREE", index.getString("indexType"));
        assertNotNull(index.getString("bounds"));
        JsonArray extraAttributesArr = index.getJsonArray("extraAttribute");
        assertEquals(2, extraAttributesArr.size());
        assertEquals("sp", extraAttributesArr.getString(0));
        assertEquals("ip", extraAttributesArr.getString(1));
        ObjectId treeId = ObjectId.valueOf(response.getString("indexedTreeId"));

        IndexInfo indexInfo = geogig.indexDatabase().getIndexInfo("Points", "geom").get();
        assertEquals("Points", indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        List<String> extraAttributes = Lists.newArrayList(
                (String[]) indexInfo.getMetadata().get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        assertEquals(2, extraAttributes.size());
        assertTrue(extraAttributes.contains("sp"));
        assertTrue(extraAttributes.contains("ip"));

        Optional<ObjectId> indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), treeId);
    }

    @Test
    public void testUpdateIndexExistingAttributesOverwrite() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:Points").call().get();

        geogig.command(CreateQuadTree.class).setTreeRefSpec("Points")
                .setExtraAttributes(Lists.newArrayList("sp")).call();

        ParameterSet options = TestParams.of("treeRefSpec", "Points", "extraAttributes", "ip",
                "overwrite", "true");

        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject index = response.getJsonObject("index");
        assertEquals("Points", index.getString("treeName"));
        assertEquals("geom", index.getString("attributeName"));
        assertEquals("QUADTREE", index.getString("indexType"));
        assertNotNull(index.getString("bounds"));
        JsonArray extraAttributesArr = index.getJsonArray("extraAttribute");
        assertEquals(1, extraAttributesArr.size());
        assertEquals("ip", extraAttributesArr.getString(0));
        ObjectId treeId = ObjectId.valueOf(response.getString("indexedTreeId"));

        IndexInfo indexInfo = geogig.indexDatabase().getIndexInfo("Points", "geom").get();
        assertEquals("Points", indexInfo.getTreeName());
        assertEquals("geom", indexInfo.getAttributeName());
        assertEquals(IndexType.QUADTREE, indexInfo.getIndexType());
        assertTrue(indexInfo.getMetadata().containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
        String[] extraAttributes = (String[]) indexInfo.getMetadata()
                .get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
        assertEquals(1, extraAttributes.length);
        assertEquals("ip", extraAttributes[0]);

        Optional<ObjectId> indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        assertEquals(indexedTreeId.get(), treeId);
    }
}
