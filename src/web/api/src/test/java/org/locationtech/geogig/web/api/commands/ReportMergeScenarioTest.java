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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

public class ReportMergeScenarioTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "reportMergeScenario";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return ReportMergeScenario.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("ourCommit", "master", "theirCommit", "branch1",
                "page", "1", "elementsPerPage", "5");

        ReportMergeScenario op = (ReportMergeScenario) buildCommand(options);
        assertEquals("master", op.ourCommit);
        assertEquals("branch1", op.theirCommit);
        assertEquals(1, op.pageNumber);
        assertEquals(5, op.elementsPerPage);
    }

    @Test
    public void testNoOurCommit() throws Exception {
        ParameterSet options = TestParams.of("theirCommit", "branch1");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("Required parameter 'ourCommit' was not provided.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testNoTheirCommit() throws Exception {
        ParameterSet options = TestParams.of("ourCommit", "branch1");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("Required parameter 'theirCommit' was not provided.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testInvalidOurCommit() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("ourCommit", "nonexistent", "theirCommit", "branch1");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("'our' commit could not be resolved to a commit object.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testInvalidTheirCommit() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("ourCommit", "master", "theirCommit", "nonexistent");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("'their' commit could not be resolved to a commit object.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testMergeScenario() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        geogig.command(CommitOp.class).setMessage("point1").call();
        testData.branch("branch1");
        testData.insert(TestData.point2);
        testData.add();
        geogig.command(CommitOp.class).setMessage("point2").call();
        testData.checkout("branch1");
        testData.insert(TestData.point3);
        testData.add();
        geogig.command(CommitOp.class).setMessage("point3").call();
        testData.checkout("master");

        ParameterSet options = TestParams.of("ourCommit", "master", "theirCommit", "branch1");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject merge = response.getJsonObject("Merge");
        JsonArray featureArray = merge.getJsonArray("Feature");
        assertEquals(1, featureArray.getValuesAs(JsonValue.class).size());
        JsonObject feature = featureArray.getJsonObject(0);
        assertEquals("ADDED", feature.getString("change"));
        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point3.getID());
        assertEquals(path, feature.getString("id"));
        JsonArray geometryArray = feature.getJsonArray("geometry");
        assertEquals(1, geometryArray.getValuesAs(JsonValue.class).size());
        String geometry = geometryArray.getString(0);
        assertEquals("POINT (10 10)", geometry);
    }

    @Test
    public void testMergeScenarioConflicts() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        geogig.command(CommitOp.class).setMessage("point1").call();
        testData.branch("branch1");
        testData.insert(TestData.point1_modified);
        testData.add();
        geogig.command(CommitOp.class).setMessage("modify point1").call();
        ObjectId point1_id = RevFeature.builder().build(TestData.point1_modified).getId();
        testData.checkout("branch1");
        testData.remove(TestData.point1);
        testData.add();
        geogig.command(CommitOp.class).setMessage("remove point1").call();
        testData.checkout("master");

        ParameterSet options = TestParams.of("ourCommit", "master", "theirCommit", "branch1");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject merge = response.getJsonObject("Merge");
        JsonArray featureArray = merge.getJsonArray("Feature");
        assertEquals(1, featureArray.getValuesAs(JsonValue.class).size());
        JsonObject feature = featureArray.getJsonObject(0);
        assertEquals("CONFLICT", feature.getString("change"));
        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        assertEquals(path, feature.getString("id"));
        JsonArray geometryArray = feature.getJsonArray("geometry");
        assertEquals(1, geometryArray.getValuesAs(JsonValue.class).size());
        String geometry = geometryArray.getString(0);
        assertEquals("POINT (0 0)", geometry);
        assertEquals(point1_id.toString(), feature.getString("ourvalue"));
        assertEquals(ObjectId.NULL.toString(), feature.getString("theirvalue"));
    }

    @Test
    public void testMergeScenarioPaging() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        geogig.command(CommitOp.class).setMessage("point1").call();
        testData.branch("branch1");
        testData.insert(TestData.point2);
        testData.add();
        geogig.command(CommitOp.class).setMessage("point2").call();
        testData.checkout("branch1");
        testData.insert(TestData.point3, TestData.line1);
        testData.add();
        geogig.command(CommitOp.class).setMessage("point3").call();
        testData.checkout("master");

        ParameterSet options = TestParams.of("ourCommit", "master", "theirCommit", "branch1",
                "elementsPerPage", "1");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject merge = response.getJsonObject("Merge");
        JsonArray featureArray = merge.getJsonArray("Feature");
        assertEquals(1, featureArray.getValuesAs(JsonValue.class).size());
        JsonObject feature = featureArray.getJsonObject(0);
        assertEquals("ADDED", feature.getString("change"));
        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point3.getID());
        assertEquals(path, feature.getString("id"));
        JsonArray geometryArray = feature.getJsonArray("geometry");
        assertEquals(1, geometryArray.getValuesAs(JsonValue.class).size());
        String geometry = geometryArray.getString(0);
        assertEquals("POINT (10 10)", geometry);
        assertTrue(merge.getBoolean("additionalChanges"));

        // Third page will have the added line feature
        options = TestParams.of("ourCommit", "master", "theirCommit", "branch1", "elementsPerPage",
                "1", "page", "1");
        buildCommand(options).run(testContext.get());

        response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        merge = response.getJsonObject("Merge");
        featureArray = merge.getJsonArray("Feature");
        assertEquals(1, featureArray.getValuesAs(JsonValue.class).size());
        feature = featureArray.getJsonObject(0);
        assertEquals("ADDED", feature.getString("change"));
        path = NodeRef.appendChild(TestData.linesType.getTypeName(), TestData.line1.getID());
        assertEquals(path, feature.getString("id"));
        geometryArray = feature.getJsonArray("geometry");
        assertEquals(1, geometryArray.getValuesAs(JsonValue.class).size());
        geometry = geometryArray.getString(0);
        assertEquals("LINESTRING (-1 -1, 1 1)", geometry);
        assertNull(merge.getJsonObject("additionalChanges"));
    }

}
