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
import static org.junit.Assert.assertTrue;

import javax.json.JsonObject;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;

public class CreateIndexTest extends AbstractIndexWebOpTest {

    @Override
    protected String getRoute() {
        return "create";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return CreateIndex.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("treeRefSpec", "points", "geometryAttributeName",
                "the_geom", "indexHistory", "true", "extraAttributes", "sp", "extraAttributes",
                "ip", "bounds", "-90,-90,90,90");

        CreateIndex op = (CreateIndex) buildCommand(options);
        assertEquals("points", op.treeRefSpec);
        assertEquals("the_geom", op.geometryAttributeName);
        assertTrue(op.indexHistory);
        assertTrue(op.extraAttributes.contains("sp"));
        assertTrue(op.extraAttributes.contains("ip"));
        assertEquals(2, op.extraAttributes.size());
        assertEquals("-90,-90,90,90", op.bbox);
    }

    @Test
    public void testCreateIndex() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:Points").call().get();

        ParameterSet options = TestParams.of("treeRefSpec", "Points", "extraAttributes", "sp");

        buildCommand(options).run(testContext.get());

        Envelope expectedBounds = new Envelope(-90, 90, -180, 180);

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
    public void testCreateIndexWithBounds() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:Points").call().get();

        ParameterSet options = TestParams.of("treeRefSpec", "Points", "extraAttributes", "sp",
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
    public void testCreateIndexFullHistory() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:Points").call().get();

        ParameterSet options = TestParams.of("treeRefSpec", "Points", "indexHistory", "true",
                "extraAttributes", "sp");

        buildCommand(options).run(testContext.get());

        Envelope expectedBounds = new Envelope(-90, 90, -180, 180);

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
    public void testCreateIndexThatExists() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        geogig.indexDatabase().createIndexInfo("Points", "geom", IndexType.QUADTREE, null);

        ParameterSet options = TestParams.of("treeRefSpec", "Points");

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("An index has already been created on that tree and attribute.");
        buildCommand(options).run(testContext.get());
    }
}
