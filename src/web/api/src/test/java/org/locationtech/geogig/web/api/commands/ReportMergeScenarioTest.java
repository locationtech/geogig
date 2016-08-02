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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureBuilder;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;

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
        ex.expectMessage("No 'our' commit was specified.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testNoTheirCommit() throws Exception {
        ParameterSet options = TestParams.of("ourCommit", "branch1");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("No 'their' commit was specified.");
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

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject merge = response.getJSONObject("Merge");
        System.out.println(response);
        JSONObject feature = merge.getJSONObject("Feature");
        assertEquals("ADDED", feature.get("change"));
        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point3.getID());
        assertEquals(path, feature.get("id"));
        assertEquals("POINT (10 10)", feature.get("geometry"));
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
        ObjectId point1_id = RevFeatureBuilder.build(TestData.point1_modified).getId();
        testData.checkout("branch1");
        testData.remove(TestData.point1);
        testData.add();
        geogig.command(CommitOp.class).setMessage("remove point1").call();
        testData.checkout("master");

        ParameterSet options = TestParams.of("ourCommit", "master", "theirCommit", "branch1");
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject merge = response.getJSONObject("Merge");
        JSONObject feature = merge.getJSONObject("Feature");
        assertEquals("CONFLICT", feature.get("change"));
        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        assertEquals(path, feature.get("id"));
        assertEquals("POINT (0 0)", feature.get("geometry"));
        assertEquals(point1_id.toString(), feature.get("ourvalue"));
        assertEquals(ObjectId.NULL.toString(), feature.get("theirvalue"));
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

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject merge = response.getJSONObject("Merge");
        JSONObject feature = merge.getJSONObject("Feature");
        assertEquals("ADDED", feature.get("change"));
        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point3.getID());
        assertEquals(path, feature.get("id"));
        assertEquals("POINT (10 10)", feature.get("geometry"));
        assertTrue(merge.getBoolean("additionalChanges"));

        // The response doesn't write out trees, so the second page will have no features
        options = TestParams.of("ourCommit", "master", "theirCommit", "branch1", "elementsPerPage",
                "1", "page", "1");
        buildCommand(options).run(testContext.get());

        response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        merge = response.getJSONObject("Merge");
        assertTrue(merge.getBoolean("additionalChanges"));

        // Third page will have the added line feature
        options = TestParams.of("ourCommit", "master", "theirCommit", "branch1", "elementsPerPage",
                "1", "page", "2");
        buildCommand(options).run(testContext.get());

        response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        merge = response.getJSONObject("Merge");
        feature = merge.getJSONObject("Feature");
        assertEquals("ADDED", feature.get("change"));
        path = NodeRef.appendChild(TestData.linesType.getTypeName(), TestData.line1.getID());
        assertEquals(path, feature.get("id"));
        assertEquals("LINESTRING (-1 -1, 1 1)", feature.get("geometry"));
        assertFalse(merge.has("additionalChanges"));
    }

}
