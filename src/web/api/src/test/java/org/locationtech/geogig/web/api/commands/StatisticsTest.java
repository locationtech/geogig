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

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;

public class StatisticsTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "statistics";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Statistics.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("since", "master~1", "branch", "master", "path",
                "some/path");

        Statistics op = (Statistics) buildCommand(options);
        assertEquals("some/path", op.path);
        assertEquals("master~1", op.since);
        assertEquals("master", op.until);
    }

    @Test
    public void testStatistics() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        RevCommit firstCommit = geogig.command(CommitOp.class)
                .setAuthor("Author1", "author1@example.com").setMessage("point1").call();
        testData.insert(TestData.point2);
        testData.add();
        geogig.command(CommitOp.class).setAuthor("Author2", "author2@example.com")
                .setMessage("point2").call();
        testData.insert(TestData.line1);
        testData.add();
        geogig.command(CommitOp.class).setAuthor("Author3", "author3@example.com")
                .setMessage("line1").call();
        testData.insert(TestData.point3);
        testData.add();
        RevCommit lastCommit = geogig.command(CommitOp.class)
                .setAuthor("Author1", "author1@example.com").setMessage("point3").call();

        ParameterSet options = TestParams.of("branch", "master", "path",
                TestData.pointsType.getTypeName());
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject statistics = response.getJSONObject("Statistics");
        assertEquals(firstCommit.getId().toString(),
                statistics.getJSONObject("firstCommit").getString("id"));
        assertEquals(lastCommit.getId().toString(),
                statistics.getJSONObject("latestCommit").getString("id"));
        assertEquals(3, statistics.getInt("totalCommits"));
        JSONObject authors = statistics.getJSONObject("Authors");
        assertEquals(2, authors.getInt("totalAuthors"));
        String expectedAuthors = "[{'name':'Author1','email':'author1@example.com'},"
                + "{'name':'Author2','email':'author2@example.com'}]";
        assertTrue(TestData.jsonEquals(TestData.toJSONArray(expectedAuthors),
                authors.getJSONArray("Author"), false));
    }

    @Test
    public void testStatisticsSince() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        geogig.command(CommitOp.class).setCommitterTimestamp(1000L)
                .setAuthor("Author1", "author1@example.com").setMessage("point1").call();
        testData.insert(TestData.point2);
        testData.add();
        RevCommit firstCommit = geogig.command(CommitOp.class).setCommitterTimestamp(2000L)
                .setAuthor("Author2", "author2@example.com").setMessage("point2").call();
        testData.insert(TestData.line1);
        testData.add();
        geogig.command(CommitOp.class).setCommitterTimestamp(3000L)
                .setAuthor("Author3", "author3@example.com").setMessage("line1").call();
        testData.insert(TestData.point3);
        testData.add();
        RevCommit lastCommit = geogig.command(CommitOp.class).setCommitterTimestamp(4000L)
                .setAuthor("Author2", "author2_alternate@example.com").setMessage("point3").call();

        ParameterSet options = TestParams.of("branch", "master", "since", "1500", "path",
                TestData.pointsType.getTypeName());
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject statistics = response.getJSONObject("Statistics");
        assertEquals(firstCommit.getId().toString(),
                statistics.getJSONObject("firstCommit").getString("id"));
        assertEquals(lastCommit.getId().toString(),
                statistics.getJSONObject("latestCommit").getString("id"));
        assertEquals(2, statistics.getInt("totalCommits"));
        JSONObject authors = statistics.getJSONObject("Authors");
        assertEquals(2, authors.getInt("totalAuthors"));
        String expectedAuthors = "[{'name':'Author2','email':'author2@example.com'},"
                + "{'name':'Author2','email':'author2_alternate@example.com'}]";
        assertTrue(TestData.jsonEquals(TestData.toJSONArray(expectedAuthors),
                authors.getJSONArray("Author"), false));
    }

    @Test
    public void testStatisticsRootPath() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        RevCommit firstCommit = geogig.command(CommitOp.class)
                .setAuthor("Author1", "author1@example.com").setMessage("point1").call();
        testData.insert(TestData.point2);
        testData.add();
        geogig.command(CommitOp.class).setAuthor("Author2", "author2@example.com")
                .setMessage("point2").call();
        testData.insert(TestData.line1);
        testData.add();
        geogig.command(CommitOp.class).setAuthor("Author3", "author3@example.com")
                .setMessage("line1").call();
        testData.insert(TestData.point3);
        testData.add();
        RevCommit lastCommit = geogig.command(CommitOp.class)
                .setAuthor("Author1", "author1@example.com").setMessage("point3").call();

        ParameterSet options = TestParams.of("since", "");
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject statistics = response.getJSONObject("Statistics");
        assertEquals(firstCommit.getId().toString(),
                statistics.getJSONObject("firstCommit").getString("id"));
        assertEquals(lastCommit.getId().toString(),
                statistics.getJSONObject("latestCommit").getString("id"));
        assertEquals(4, statistics.getInt("totalCommits"));
        JSONObject authors = statistics.getJSONObject("Authors");
        assertEquals(3, authors.getInt("totalAuthors"));
        String expectedAuthors = "[{'name':'Author1','email':'author1@example.com'},"
                + "{'name':'Author2','email':'author2@example.com'},"
                + "{'name':'Author3','email':'author3@example.com'}]";
        assertTrue(TestData.jsonEquals(TestData.toJSONArray(expectedAuthors),
                authors.getJSONArray("Author"), false));
        JSONObject featureTypes = statistics.getJSONObject("FeatureTypes");
        assertEquals(2, featureTypes.getInt("totalFeatureTypes"));
        assertEquals(4, featureTypes.getInt("totalFeatures"));
        String expectedFeatureTypes = "[{'name':'" + TestData.pointsType.getTypeName()
                + "','numFeatures':3}," + "{'name':'" + TestData.linesType.getTypeName()
                + "','numFeatures':1}]";

        assertTrue(TestData.jsonEquals(TestData.toJSONArray(expectedFeatureTypes),
                featureTypes.getJSONArray("FeatureType"), false));
    }
}
