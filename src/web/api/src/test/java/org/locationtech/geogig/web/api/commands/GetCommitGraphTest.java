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
import static org.locationtech.geogig.web.api.JsonUtils.toJSONArray;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.Test;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

public class GetCommitGraphTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "getCommitGraph";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return GetCommitGraph.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        String testId = RevObjectTestSupport.hashString("objectid").toString();
        ParameterSet options = TestParams.of("depth", "5", "commitId", testId, "page", "3", "show",
                "11");

        GetCommitGraph op = (GetCommitGraph) buildCommand(options);
        assertEquals(5, op.depth);
        assertEquals(testId, op.commitId);
        assertEquals(3, op.page);
        assertEquals(11, op.elementsPerPage);
    }

    @Test
    public void testNoCommitId() {
        ParameterSet options = TestParams.of("depth", "5", "page", "3", "show", "11");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Required parameter 'commitId' was not provided.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testGetCommitGraph() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1, TestData.line1, TestData.poly1);
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("point1, line1, poly1")
                .call();
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

        ParameterSet options = TestParams.of("commitId", commit5.getId().toString());
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray commits = response.getJsonArray("commit");
        assertEquals(5, commits.getValuesAs(JsonValue.class).size());
        StringBuilder expectedCommits = new StringBuilder("[");
        expectedCommits.append("{\"id\": \"" + commit1.getId().toString() + "\"},");
        expectedCommits.append("{\"id\": \"" + commit2.getId().toString() + "\"},");
        expectedCommits.append("{\"id\": \"" + commit3.getId().toString() + "\"},");
        expectedCommits.append("{\"id\": \"" + commit4.getId().toString() + "\"},");
        expectedCommits.append("{\"id\": \"" + commit5.getId().toString() + "\"}]");
        assertTrue(jsonEquals(toJSONArray(expectedCommits.toString()), commits, false));
    }

    @Test
    public void testGetCommitGraphDepthLimit() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.addAndCommit("point1, line1, poly1", TestData.point1, TestData.line1,
                TestData.poly1);
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

        ParameterSet options = TestParams.of("depth", "2", "commitId", commit5.getId().toString());
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray commits = response.getJsonArray("commit");
        assertEquals(3, commits.getValuesAs(JsonValue.class).size());
        StringBuilder expectedCommits = new StringBuilder("[");
        expectedCommits.append("{\"id\": \"" + commit3.getId().toString() + "\"},");
        expectedCommits.append("{\"id\": \"" + commit4.getId().toString() + "\"},");
        expectedCommits.append("{\"id\": \"" + commit5.getId().toString() + "\"}]");
        assertTrue(jsonEquals(toJSONArray(expectedCommits.toString()), commits, false));

    }
}
