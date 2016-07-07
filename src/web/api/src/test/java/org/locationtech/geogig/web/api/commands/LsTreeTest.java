/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;
import org.skyscreamer.jsonassert.JSONAssert;

public class LsTreeTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "ls-tree";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return LsTree.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("showTree", "true", "onlyTree", "true", "verbose",
                "true", "recursive", "true", "path", "some/path");

        LsTree op = (LsTree) buildCommand(options);
        assertEquals("some/path", op.ref);
        assertTrue(op.includeTrees);
        assertTrue(op.onlyTrees);
        assertTrue(op.verbose);
        assertTrue(op.recursive);
    }

    @Test
    public void testRecursiveIncludeTrees() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("showTree", "true", "recursive", "true");
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONArray nodes = response.getJSONArray("node");
        String expected = "[{'path':'Points'},{'path':'Points/1'},{'path':'Points/2'},{'path':'Points/3'},"
                + "{'path':'Lines'},{'path':'Lines/1'},{'path':'Lines/2'},{'path':'Lines/3'},"
                + "{'path':'Polygons'},{'path':'Polygons/1'},{'path':'Polygons/2'},{'path':'Polygons/3'}]";
        JSONAssert.assertEquals(expected, nodes.toString(), false);
    }

    @Test
    public void testRecursiveOnlyTrees() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("onlyTree", "true", "recursive", "true");
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONArray nodes = response.getJSONArray("node");
        String expected = "[{'path':'Points'},{'path':'Lines'},{'path':'Polygons'}]";
        JSONAssert.assertEquals(expected, nodes.toString(), false);
    }

    @Test
    public void testRecursiveOnlyFeatures() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("recursive", "true");
        buildCommand(options).run(testContext.get());
        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONArray nodes = response.getJSONArray("node");
        String expected = "[{'path':'Points/1'},{'path':'Points/2'},{'path':'Points/3'},"
                + "{'path':'Lines/1'},{'path':'Lines/2'},{'path':'Lines/3'},"
                + "{'path':'Polygons/1'},{'path':'Polygons/2'},{'path':'Polygons/3'}]";
        JSONAssert.assertEquals(expected, nodes.toString(), false);
    }

    @Test
    public void testOnlyTrees() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("onlyTree", "true");
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONArray nodes = response.getJSONArray("node");
        String expected = "[{'path':'Points'},{'path':'Lines'},{'path':'Polygons'}]";
        JSONAssert.assertEquals(expected, nodes.toString(), false);
    }

    @Test
    public void testLsTree() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of();
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONArray nodes = response.getJSONArray("node");
        String expected = "[{'path':'Points'},{'path':'Lines'},{'path':'Polygons'}]";
        JSONAssert.assertEquals(expected, nodes.toString(), false);
    }

    @Test
    public void testPath() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("showTree", "true", "recursive", "true", "path",
                TestData.pointsType.getTypeName());
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONArray nodes = response.getJSONArray("node");
        String expected = "[{'path':1},{'path':2},{'path':3}]";
        JSONAssert.assertEquals(expected, nodes.toString(), false);
    }
}
