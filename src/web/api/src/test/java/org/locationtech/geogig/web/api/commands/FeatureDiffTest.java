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
import static org.locationtech.geogig.web.api.JsonUtils.jsonEquals;
import static org.locationtech.geogig.web.api.JsonUtils.toJSON;
import static org.locationtech.geogig.web.api.JsonUtils.toJSONArray;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

public class FeatureDiffTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "featurediff";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return FeatureDiff.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("path", "Points", "oldTreeish", "master~1",
                "newTreeish", "master", "all", "true");

        FeatureDiff op = (FeatureDiff) buildCommand(options);
        assertTrue(op.all);
        assertEquals("Points", op.path);
        assertEquals("master~1", op.oldTreeish);
        assertEquals("master", op.newTreeish);
    }

    @Test
    public void testNoPath() {
        ParameterSet options = TestParams.of("oldTreeish", "master~1", "newTreeish", "master",
                "all", "true");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Required parameter 'path' was not provided.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testEmptyPath() {
        ParameterSet options = TestParams.of("path", "  ", "oldTreeish", "master~1", "newTreeish",
                "master", "all", "true");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Invalid path was specified");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testFeatureDiff() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());

        testData.insert(TestData.point1);
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("Inserted point1").call();

        testData.insert(TestData.point1_modified);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("Modified point1").call();

        ParameterSet options = TestParams.of("path", path, "oldTreeish", commit1.getId().toString(),
                "newTreeish", commit2.getId().toString());
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray diffArray = response.getJsonArray("diff");
        assertEquals(1, diffArray.getValuesAs(JsonValue.class).size());
        JsonObject diff = diffArray.getJsonObject(0);
        String expected = "{\"attributename\":\"ip\", \"changetype\":\"MODIFIED\", \"oldvalue\":1000, \"newvalue\":1500}";
        assertTrue(jsonEquals(toJSON(expected), diff, false));
    }

    @Test
    public void testFeatureDiffAll() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());

        testData.insert(TestData.point1);
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("Inserted point1").call();

        testData.insert(TestData.point1_modified);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("Modified point1").call();

        ParameterSet options = TestParams.of("path", path, "oldTreeish", commit1.getId().toString(),
                "newTreeish", commit2.getId().toString(), "all", "true");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray diff = response.getJsonArray("diff");
        assertEquals(3, diff.getValuesAs(JsonValue.class).size());
        String expected = "[{\"attributename\":\"ip\", \"changetype\":\"MODIFIED\", \"oldvalue\":1000, \"newvalue\":1500},"
                + "{\"attributename\":\"sp\", \"changetype\":\"NO_CHANGE\", \"oldvalue\":\"StringProp1_1\"},"
                + "{\"attributename\":\"geom\", \"changetype\":\"NO_CHANGE\", \"oldvalue\":\"POINT (0 0)\", \"geometry\":true, \"crs\": \"EPSG:4326\"}]";
        assertTrue(jsonEquals(toJSONArray(expected), diff, false));
    }

    @Test
    public void testFeatureDiffAddedFeature() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());

        testData.insert(TestData.line1);
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("Inserted line1").call();

        testData.insert(TestData.point1);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("Inserted point1").call();

        ParameterSet options = TestParams.of("path", path, "oldTreeish", commit1.getId().toString(),
                "newTreeish", commit2.getId().toString());
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray diff = response.getJsonArray("diff");
        assertEquals(3, diff.getValuesAs(JsonValue.class).size());
        String expected = "[{\"attributename\":\"ip\", \"changetype\":\"ADDED\", \"newvalue\":1000},"
                + "{\"attributename\":\"sp\", \"changetype\":\"ADDED\", \"newvalue\":\"StringProp1_1\"},"
                + "{\"attributename\":\"geom\", \"changetype\":\"ADDED\", \"newvalue\":\"POINT (0 0)\", \"geometry\":true, \"crs\": \"EPSG:4326\"}]";
        assertTrue(jsonEquals(toJSONArray(expected), diff, false));
    }

    @Test
    public void testFeatureDiffRemovedFeature() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());

        testData.insert(TestData.point1);
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("Inserted point1").call();

        testData.remove(TestData.point1);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("Removed point1").call();

        ParameterSet options = TestParams.of("path", path, "oldTreeish", commit1.getId().toString(),
                "newTreeish", commit2.getId().toString(), "all", "true");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray diff = response.getJsonArray("diff");
        assertEquals(3, diff.getValuesAs(JsonValue.class).size());
        String expected = "[{\"attributename\":\"ip\", \"changetype\":\"REMOVED\", \"oldvalue\":1000},"
                + "{\"attributename\":\"sp\", \"changetype\":\"REMOVED\", \"oldvalue\":\"StringProp1_1\"},"
                + "{\"attributename\":\"geom\", \"changetype\":\"REMOVED\", \"oldvalue\":\"POINT (0 0)\", \"geometry\":true, \"crs\": \"EPSG:4326\"}]";
        assertTrue(jsonEquals(toJSONArray(expected), diff, false));
    }

    @Test
    public void testFeatureDiffAddedNoOldRef() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());

        testData.insert(TestData.point1);
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("Inserted point1").call();

        ParameterSet options = TestParams.of("path", path, "newTreeish",
                commit1.getId().toString());
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray diff = response.getJsonArray("diff");
        assertEquals(3, diff.getValuesAs(JsonValue.class).size());
        String expected = "[{\"attributename\":\"ip\", \"changetype\":\"ADDED\", \"newvalue\":1000},"
                + "{\"attributename\":\"sp\", \"changetype\":\"ADDED\", \"newvalue\":\"StringProp1_1\"},"
                + "{\"attributename\":\"geom\", \"changetype\":\"ADDED\", \"newvalue\":\"POINT (0 0)\", \"geometry\":true, \"crs\": \"EPSG:4326\"}]";
        assertTrue(jsonEquals(toJSONArray(expected), diff, false));
    }

}
