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
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeatureBuilder;
import org.locationtech.geogig.api.plumbing.TransactionBegin;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.LogOp;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;

public class RevertFeatureTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "revertfeature";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return RevertFeature.class;
    }

    @Test
    public void testBuildParameters() {
        ObjectId oldCommitId = ObjectId.forString("old");
        ObjectId newCommitId = ObjectId.forString("new");
        ParameterSet options = TestParams.of("authorName", "Tester", "authorEmail",
                "tester@example.com", "commitMessage", "someCommitMessage", "mergeMessage",
                "someMergeMessage", "oldCommitId", oldCommitId.toString(), "newCommitId",
                newCommitId.toString(), "path", "some/path");

        RevertFeature op = (RevertFeature) buildCommand(options);
        assertEquals("some/path", op.featurePath);
        assertEquals(oldCommitId, op.oldCommitId);
        assertEquals(newCommitId, op.newCommitId);
        assertEquals("someMergeMessage", op.mergeMessage.get());
        assertEquals("someCommitMessage", op.commitMessage.get());
        assertEquals("tester@example.com", op.authorEmail.get());
        assertEquals("Tester", op.authorName.get());
    }

    @Test
    public void testRevertFeature() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("point1").call();
        testData.insert(TestData.point1_modified);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("modify point1").call();

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("oldCommitId", commit1.getId().toString(),
                "newCommitId", commit2.getId().toString(), "path", path, "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        RevCommit lastCommit = transaction.command(LogOp.class).setLimit(1).call().next();

        assertEquals("Reverted changes made to " + path + " at " + commit2.getId().toString(),
                lastCommit.getMessage());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
    }

    @Test
    public void testRevertAddedFeature() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("point1")
                .call();
        testData.insert(TestData.line1);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("line1")
                .call();

        String path = NodeRef.appendChild(TestData.linesType.getTypeName(),
                TestData.line1.getID());

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("oldCommitId", commit1.getId().toString(),
                "newCommitId", commit2.getId().toString(), "path", path, "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        RevCommit lastCommit = transaction.command(LogOp.class).setLimit(1).call().next();

        assertEquals("Reverted changes made to " + path + " at " + commit2.getId().toString(),
                lastCommit.getMessage());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
    }

    @Test
    public void testRevertRemovedFeature() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("point1").call();
        testData.remove(TestData.point1);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("removed point1").call();

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("oldCommitId", commit1.getId().toString(),
                "newCommitId", commit2.getId().toString(), "path", path, "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        RevCommit lastCommit = transaction.command(LogOp.class).setLimit(1).call().next();

        assertEquals("Reverted changes made to " + path + " at " + commit2.getId().toString(),
                lastCommit.getMessage());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
    }

    @Test
    public void testRevertNonExistentFeature() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("point1").call();
        testData.insert(TestData.line1);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("line1").call();

        String path = "Nonexistent/Feature";

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("oldCommitId", commit1.getId().toString(),
                "newCommitId", commit2.getId().toString(), "path", path, "transactionId",
                transaction.getTransactionId().toString());

        ex.expect(CommandSpecException.class);
        ex.expectMessage("The feature was not found in either commit tree.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testRevertFeatureConflict() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        ObjectId point1_id = RevFeatureBuilder.build(TestData.point1).getId();
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("point1").call();
        testData.insert(TestData.point1_modified);
        testData.add();
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("modify point1").call();
        testData.remove(TestData.point1_modified);
        testData.add();
        RevCommit commit3 = geogig.command(CommitOp.class).setMessage("remove point1").call();

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("oldCommitId", commit1.getId().toString(),
                "newCommitId", commit2.getId().toString(), "path", path, "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject merge = response.getJSONObject("Merge");
        assertEquals(commit3.getId().toString(), merge.getString("ours"));
        assertEquals(commit2.getId().toString(), merge.getString("ancestor"));
        assertEquals(1, merge.getInt("conflicts"));
        JSONObject feature = merge.getJSONObject("Feature");
        assertEquals("CONFLICT", feature.get("change"));
        assertEquals(path, feature.get("id"));
        assertEquals("POINT (0 0)", feature.get("geometry"));
        assertEquals(ObjectId.NULL.toString(), feature.get("ourvalue"));
        assertEquals(point1_id.toString(), feature.get("theirvalue"));
    }

}
