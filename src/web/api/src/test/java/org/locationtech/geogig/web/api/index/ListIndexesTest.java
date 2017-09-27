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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.Test;
import org.locationtech.geogig.porcelain.index.CreateQuadTree;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

import com.google.common.collect.Lists;

public class ListIndexesTest extends AbstractIndexWebOpTest {

    @Override
    protected String getRoute() {
        return "list";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return ListIndexes.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("treeName", "points");

        ListIndexes op = (ListIndexes) buildCommand(options);
        assertEquals("points", op.treeName);
    }

    @Test
    public void testListIndexes() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        geogig.command(CreateQuadTree.class).setTreeRefSpec("Points")
                .setExtraAttributes(Lists.newArrayList("sp")).call();
        geogig.command(CreateQuadTree.class).setTreeRefSpec("Lines")
                .setExtraAttributes(Lists.newArrayList("ip")).call();

        ParameterSet options = TestParams.of();

        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray indexes = response.getJsonArray("index");
        assertEquals(2, indexes.size());
        JsonObject index = indexes.getJsonObject(0);
        int pointsIndex = 1;
        int linesIndex = 0;
        if (index.getString("treeName").equals("Points")) {
            pointsIndex = 0;
            linesIndex = 1;
        }
        index = indexes.getJsonObject(pointsIndex);
        assertEquals("Points", index.getString("treeName"));
        assertEquals("geom", index.getString("attributeName"));
        assertEquals("QUADTREE", index.getString("indexType"));
        assertNotNull(index.getString("bounds"));
        JsonArray extraAttributesArr = index.getJsonArray("extraAttribute");
        assertEquals(1, extraAttributesArr.size());
        assertEquals("sp", extraAttributesArr.getString(0));

        index = indexes.getJsonObject(linesIndex);
        assertEquals("Lines", index.getString("treeName"));
        assertEquals("geom", index.getString("attributeName"));
        assertEquals("QUADTREE", index.getString("indexType"));
        assertNotNull(index.getString("bounds"));
        extraAttributesArr = index.getJsonArray("extraAttribute");
        assertEquals(1, extraAttributesArr.size());
        assertEquals("ip", extraAttributesArr.getString(0));
    }

    @Test
    public void testListIndexesTreeName() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        geogig.command(CreateQuadTree.class).setTreeRefSpec("Points")
                .setExtraAttributes(Lists.newArrayList("sp")).call();
        geogig.command(CreateQuadTree.class).setTreeRefSpec("Lines")
                .setExtraAttributes(Lists.newArrayList("ip")).call();

        ParameterSet options = TestParams.of("treeName", "Points");

        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray indexes = response.getJsonArray("index");
        assertEquals(1, indexes.size());
        JsonObject index = indexes.getJsonObject(0);
        assertEquals("Points", index.getString("treeName"));
        assertEquals("geom", index.getString("attributeName"));
        assertEquals("QUADTREE", index.getString("indexType"));
        assertNotNull(index.getString("bounds"));
        JsonArray extraAttributesArr = index.getJsonArray("extraAttribute");
        assertEquals(1, extraAttributesArr.size());
        assertEquals("sp", extraAttributesArr.getString(0));
    }

    @Test
    public void testListIndexesNonexistentTreeName() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        geogig.command(CreateQuadTree.class).setTreeRefSpec("Points")
                .setExtraAttributes(Lists.newArrayList("sp")).call();
        geogig.command(CreateQuadTree.class).setTreeRefSpec("Lines")
                .setExtraAttributes(Lists.newArrayList("ip")).call();

        ParameterSet options = TestParams.of("treeName", "nonexistent");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("The provided tree name was not found in the HEAD commit.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testListIndexesNonIndexedTreeName() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        geogig.command(CreateQuadTree.class).setTreeRefSpec("Points")
                .setExtraAttributes(Lists.newArrayList("sp")).call();
        geogig.command(CreateQuadTree.class).setTreeRefSpec("Lines")
                .setExtraAttributes(Lists.newArrayList("ip")).call();

        ParameterSet options = TestParams.of("treeName", "Polygons");

        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray indexes = response.getJsonArray("index");
        assertEquals(0, indexes.size());
    }

    @Test
    public void testListIndexesNone() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of();

        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray indexes = response.getJsonArray("index");
        assertEquals(0, indexes.size());
    }
}
