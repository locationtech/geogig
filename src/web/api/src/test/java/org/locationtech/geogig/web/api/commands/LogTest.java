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

import java.io.StringWriter;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.Variants;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.spring.dto.LegacyResponse;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

public class LogTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "log";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Log.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        String timestamp1 = Long
                .toString(testContext.get().getRepository().platform().currentTimeMillis());
        String timestamp2 = Long
                .toString(testContext.get().getRepository().platform().currentTimeMillis() / 2);
        ParameterSet options = TestParams.of("limit", "100", "offset", "10", "path", "the/path",
                "since", "master~1", "until", "master", "sinceTime", timestamp1, "untilTime",
                timestamp2, "page", "3", "show", "11", "firstParentOnly", "true", "countChanges",
                "true", "returnRange", "true", "summary", "true");

        Log op = (Log) buildCommand(options);
        assertEquals(100, op.limit.intValue());
        assertEquals(10, op.skip.intValue());
        assertEquals("the/path", op.paths.get(0));
        assertEquals("master~1", op.since);
        assertEquals("master", op.until);
        assertEquals(timestamp1, op.sinceTime);
        assertEquals(timestamp2, op.untilTime);
        assertEquals(3, op.page);
        assertEquals(11, op.elementsPerPage);
        assertTrue(op.firstParentOnly);
        assertTrue(op.countChanges);
        assertTrue(op.returnRange);
        assertTrue(op.summary);
    }

    @Test
    public void testLog() throws Exception {
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

        ParameterSet options = TestParams.of();
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
    public void testLogRange() throws Exception {
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
        RevCommit commit3 = geogig.command(CommitOp.class).setMessage("point3, line3, poly23")
                .call();
        testData.checkout("master");
        MergeReport report = geogig.command(MergeOp.class).setNoFastForward(true)
                .setMessage("merge branch branch1").addCommit(commit2.getId()).call();
        RevCommit commit4 = report.getMergeCommit();
        geogig.command(MergeOp.class).setNoFastForward(true).setMessage("merge branch branch2")
                .addCommit(commit3.getId()).call();

        ParameterSet options = TestParams.of("since", commit1.getId().toString(), "until",
                commit4.getId().toString(), "firstParentOnly", "false");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray commits = response.getJsonArray("commit");
        assertEquals(2, commits.getValuesAs(JsonValue.class).size());
        StringBuilder expectedCommits = new StringBuilder("[");
        expectedCommits.append("{\"id\": \"" + commit2.getId().toString() + "\"},");
        expectedCommits.append("{\"id\": \"" + commit4.getId().toString() + "\"}]");
        assertTrue(jsonEquals(toJSONArray(expectedCommits.toString()), commits, false));
    }

    @Test
    public void testLogTimestampRange() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.insert(TestData.point1, TestData.line1, TestData.poly1);
        testData.add();
        geogig.command(CommitOp.class).setMessage("point1, line1, poly1")
                .setCommitterTimestamp(Long.valueOf(1000)).call();
        testData.insert(TestData.point2, TestData.line2, TestData.poly2);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("point2, line2, poly2")
                .setCommitterTimestamp(Long.valueOf(2000)).call();
        testData.insert(TestData.point3, TestData.line3, TestData.poly3);
        testData.add();
        RevCommit commit3 = geogig.command(CommitOp.class).setMessage("point3, line3, poly3")
                .setCommitterTimestamp(Long.valueOf(3000)).call();

        ParameterSet options = TestParams.of("sinceTime", Long.toString(2000), "untilTime",
                Long.toString(3000), "firstParentOnly", "false");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray commits = response.getJsonArray("commit");
        assertEquals(2, commits.getValuesAs(JsonValue.class).size());
        StringBuilder expectedCommits = new StringBuilder("[");
        expectedCommits.append("{\"id\": \"" + commit2.getId().toString() + "\"},");
        expectedCommits.append("{\"id\": \"" + commit3.getId().toString() + "\"}]");
        assertTrue(jsonEquals(toJSONArray(expectedCommits.toString()), commits, false));
    }

    @Test
    public void testLogPath() throws Exception {
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
        testData.insert(TestData.line2, TestData.poly2);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("line2, poly2").call();
        testData.checkout("branch2");
        testData.insert(TestData.point3, TestData.line3, TestData.poly3);
        testData.add();
        RevCommit commit3 = geogig.command(CommitOp.class).setMessage("point3, line3, poly3")
                .call();
        testData.checkout("master");
        MergeReport report = geogig.command(MergeOp.class).setNoFastForward(true)
                .setMessage("merge branch branch1").addCommit(commit2.getId()).call();
        report = geogig.command(MergeOp.class).setNoFastForward(true)
                .setMessage("merge branch branch2").addCommit(commit3.getId()).call();
        RevCommit commit5 = report.getMergeCommit();

        ParameterSet options = TestParams.of("path", "Points");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray commits = response.getJsonArray("commit");
        assertEquals(3, commits.getValuesAs(JsonValue.class).size());
        StringBuilder expectedCommits = new StringBuilder("[");
        expectedCommits.append("{\"id\": \"" + commit1.getId().toString() + "\"},");
        expectedCommits.append("{\"id\": \"" + commit3.getId().toString() + "\"},");
        expectedCommits.append("{\"id\": \"" + commit5.getId().toString() + "\"}]");
        assertTrue(jsonEquals(toJSONArray(expectedCommits.toString()), commits, false));
    }

    @Test
    public void testCountChanges() throws Exception {
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
        testData.insert(TestData.point2, TestData.poly2);
        testData.insert(TestData.point1_modified);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class)
                .setMessage("point2, poly2, modified point1").call();
        testData.checkout("branch2");
        testData.insert(TestData.point3);
        testData.remove(TestData.line1);
        testData.add();
        RevCommit commit3 = geogig.command(CommitOp.class).setMessage("+point3 -line1").call();
        testData.checkout("master");
        MergeReport report = geogig.command(MergeOp.class).setNoFastForward(true)
                .setMessage("merge branch branch1").addCommit(commit2.getId()).call();
        RevCommit commit4 = report.getMergeCommit();
        report = geogig.command(MergeOp.class).setNoFastForward(true)
                .setMessage("merge branch branch2").addCommit(commit3.getId()).call();
        RevCommit commit5 = report.getMergeCommit();

        ParameterSet options = TestParams.of("countChanges", "true");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray commits = response.getJsonArray("commit");
        assertEquals(5, commits.getValuesAs(JsonValue.class).size());
        StringBuilder expectedCommits = new StringBuilder("[");
        expectedCommits.append("{\"id\": \"" + commit1.getId().toString()
                + "\", \"adds\": 3, \"removes\": 0, \"modifies\": 0},");
        expectedCommits.append("{\"id\": \"" + commit2.getId().toString()
                + "\", \"adds\": 2, \"removes\": 0, \"modifies\": 1},");
        expectedCommits.append("{\"id\": \"" + commit3.getId().toString()
                + "\", \"adds\": 1, \"removes\": 1, \"modifies\": 0},");
        expectedCommits.append("{\"id\": \"" + commit4.getId().toString()
                + "\", \"adds\": 2, \"removes\": 0, \"modifies\": 1},");
        expectedCommits.append("{\"id\": \"" + commit5.getId().toString()
                + "\", \"adds\": 1, \"removes\": 1, \"modifies\": 0}]");
        assertTrue(jsonEquals(toJSONArray(expectedCommits.toString()), commits, false));
    }

    @Test
    public void testSummary() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");
        testData.insert(TestData.point1, TestData.line1, TestData.poly1);
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("point1 line1 poly1").call();
        testData.branch("branch1");
        testData.branch("branch2");
        testData.checkout("branch1");
        testData.insert(TestData.point2, TestData.poly2);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("point2 poly2").call();
        testData.checkout("branch2");
        testData.insert(TestData.point3);
        testData.remove(TestData.line1);
        testData.add();
        RevCommit commit3 = geogig.command(CommitOp.class).setMessage("+point3 -line1").call();
        testData.checkout("master");
        geogig.command(MergeOp.class).setNoFastForward(true).setMessage("merge branch branch1")
                .addCommit(commit2.getId()).call();
        geogig.command(MergeOp.class).setNoFastForward(true).setMessage("merge branch branch2")
                .addCommit(commit3.getId()).call();

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        ParameterSet options = TestParams.of("path", path, "summary", "true");
        buildCommand(options).run(testContext.get());
        LegacyResponse response = testContext.getCommandResponse();

        StringWriter writer = new StringWriter();
        response.encode(writer, Variants.CSV_MEDIA_TYPE, "/geogig");

        String content = writer.toString();
        String[] lines = content.split("\n");

        assertEquals(2, lines.length);
        String[] values = lines[1].split(",");
        assertEquals("ADDED", values[0]);
        assertEquals(path, values[1]);
        assertEquals(commit1.getId().toString(), values[2]);
        assertEquals(ObjectId.NULL.toString(), values[3]);
        assertEquals(commit1.getAuthor().getName().get(), values[4]);
        assertEquals(commit1.getAuthor().getEmail().get(), values[5]);
        assertEquals(commit1.getCommitter().getName().get(), values[7]);
        assertEquals(commit1.getCommitter().getEmail().get(), values[8]);
        assertEquals(commit1.getMessage(), stripQuotes(values[10]));
        assertEquals(TestData.point1.getAttribute(0).toString(), stripQuotes(values[11]));
        assertEquals(TestData.point1.getAttribute(1).toString(), stripQuotes(values[12]));
        assertEquals(TestData.point1.getDefaultGeometry().toString(), stripQuotes(values[13]));
    }

    @Test
    public void testSummaryNoPath() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("summary", "true");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("You must specify a feature type path when getting a summary.");
        buildCommand(options).run(testContext.get());
    }

    private String stripQuotes(String input) {
        if (input.charAt(0) == '\"' && input.charAt(input.length() - 1) == '\"') {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }
}
