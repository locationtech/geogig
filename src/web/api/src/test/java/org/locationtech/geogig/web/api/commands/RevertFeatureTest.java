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

import java.util.UUID;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

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
        String oldCommitId = RevObjectTestSupport.hashString("old").toString();
        String newCommitId = RevObjectTestSupport.hashString("new").toString();
        ParameterSet options = TestParams.of("authorName", "Tester", "authorEmail",
                "tester@example.com", "commitMessage", "someCommitMessage", "mergeMessage",
                "someMergeMessage", "oldCommitId", oldCommitId, "newCommitId", newCommitId, "path",
                "some/path", "transactionId",
                UUID.randomUUID().toString());

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
        Repository geogig = testContext.get().getRepository();
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

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
    }

    @Test
    public void testRevertAddedFeature() throws Exception {
        Repository geogig = testContext.get().getRepository();
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

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
    }

    @Test
    public void testRevertRemovedFeature() throws Exception {
        Repository geogig = testContext.get().getRepository();
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

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
    }

    @Test
    public void testRevertNonExistentFeature() throws Exception {
        Repository geogig = testContext.get().getRepository();
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
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        ObjectId point1_id = RevFeature.builder().build(TestData.point1).getId();
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

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject merge = response.getJsonObject("Merge");
        assertEquals(commit3.getId().toString(), merge.getString("ours"));
        assertEquals(commit2.getId().toString(), merge.getString("ancestor"));
        assertEquals(1, merge.getInt("conflicts"));
        JsonArray featureArray = merge.getJsonArray("Feature");
        assertEquals(1, featureArray.getValuesAs(JsonValue.class).size());
        JsonObject feature = featureArray.getJsonObject(0);
        assertEquals("CONFLICT", feature.getString("change"));
        assertEquals(path, feature.getString("id"));
        JsonArray geometryArray = feature.getJsonArray("geometry");
        assertEquals(1, geometryArray.getValuesAs(JsonValue.class).size());
        String geometry = geometryArray.getString(0);
        assertEquals("POINT (0 0)", geometry);
        assertEquals(ObjectId.NULL.toString(), feature.getString("ourvalue"));
        assertEquals(point1_id.toString(), feature.getString("theirvalue"));
    }

}
