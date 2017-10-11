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
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;

public class RebuildGraphTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "rebuildgraph";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return RebuildGraph.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("quiet", "true");

        RebuildGraph op = (RebuildGraph) buildCommand(options);
        assertTrue(op.quiet);
    }

    @Test
    public void testRebuildGraph() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1, TestData.line1, TestData.poly1);
        testData.add();
        geogig.command(CommitOp.class).setMessage("point1, line1, poly1").call();
        testData.branch("branch1");
        testData.branch("branch2");
        testData.checkout("branch1");
        testData.insert(TestData.point2, TestData.line2, TestData.poly2);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("point2, line2, poly2")
                .call();
        testData.checkout("branch2");
        testData.insert(TestData.point3, TestData.line3, TestData.poly3);
        testData.add();
        RevCommit commit3 = geogig.command(CommitOp.class).setMessage("point3, line3, poly3")
                .call();
        testData.checkout("master");
        MergeReport report = geogig.command(MergeOp.class).setNoFastForward(true)
                .setMessage("merge branch branch1").addCommit(commit2.getId()).call();
        RevCommit commit4 = report.getMergeCommit();
        report = geogig.command(MergeOp.class).setNoFastForward(true)
                .setMessage("merge branch branch2").addCommit(commit3.getId()).call();
        RevCommit commit5 = report.getMergeCommit();

        geogig.graphDatabase().truncate();

        ParameterSet options = TestParams.of();
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject rebuildGraph = response.getJsonObject("RebuildGraph");
        assertEquals(4, rebuildGraph.getInt("updatedGraphElements"));
        JsonArray updatedObjects = rebuildGraph.getJsonArray("UpdatedObject");
        assertEquals(4, updatedObjects.getValuesAs(JsonValue.class).size());
        StringBuilder expectedCommits = new StringBuilder("[");
        // The root commit will not get reported because it will be added to the graph when commit2
        // is processed because it is a parent of commit2. It wont be flagged as updated because
        // when it processes the root commit, it has no parents and thus nothing to update.
        expectedCommits.append("{\"ref\": \"" + commit2.getId().toString() + "\"},");
        expectedCommits.append("{\"ref\": \"" + commit3.getId().toString() + "\"},");
        expectedCommits.append("{\"ref\": \"" + commit4.getId().toString() + "\"},");
        expectedCommits.append("{\"ref\": \"" + commit5.getId().toString() + "\"}]");
        assertTrue(jsonEquals(toJSONArray(expectedCommits.toString()), updatedObjects, false));
    }

    @Test
    public void testRebuildGraphQuiet() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1, TestData.line1, TestData.poly1);
        testData.add();
        geogig.command(CommitOp.class).setMessage("point1, line1, poly1").call();
        testData.branch("branch1");
        testData.branch("branch2");
        testData.checkout("branch1");
        testData.insert(TestData.point2, TestData.line2, TestData.poly2);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("point2, line2, poly2")
                .call();
        testData.checkout("branch2");
        testData.insert(TestData.point3, TestData.line3, TestData.poly3);
        testData.add();
        RevCommit commit3 = geogig.command(CommitOp.class).setMessage("point3, line3, poly3")
                .call();
        testData.checkout("master");
        geogig.command(MergeOp.class).setNoFastForward(true).setMessage("merge branch branch1")
                .addCommit(commit2.getId()).call();
        geogig.command(MergeOp.class).setNoFastForward(true).setMessage("merge branch branch2")
                .addCommit(commit3.getId()).call();

        geogig.graphDatabase().truncate();

        ParameterSet options = TestParams.of("quiet", "true");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject rebuildGraph = response.getJsonObject("RebuildGraph");
        String expectedResponse = "{\"updatedGraphElements\":4}";
        assertTrue(jsonEquals(toJSON(expectedResponse), rebuildGraph, false));
    }
}
