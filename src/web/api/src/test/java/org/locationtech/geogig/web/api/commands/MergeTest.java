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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

public class MergeTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "merge";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Merge.class;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("noCommit", "true", "commit", "master", "authorName",
                "Tester", "authorEmail", "tester@example.com", "transactionId",
                UUID.randomUUID().toString());

        Merge op = (Merge) buildCommand(options);
        assertEquals("master", op.commit);
        assertEquals("Tester", op.authorName.get());
        assertEquals("tester@example.com", op.authorEmail.get());
        assertTrue(op.noCommit);
    }

    @Test
    public void testMergeNoCommit() throws Exception {
        Repository geogig = testContext.get().getRepository();
        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString());
        ex.expect(CommandSpecException.class);
        ex.expectMessage("Required parameter 'commit' was not provided.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testMergeNoHead() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        geogig.command(UpdateSymRef.class).setDelete(true).setName(Ref.HEAD).call();
        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("commit", "branch1", "transactionId",
                transaction.getTransactionId().toString());
        ex.expect(CommandSpecException.class);
        ex.expectMessage("Repository has no HEAD, can't merge.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testMergeInvalidCommit() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("commit", "nonexistent", "transactionId",
                transaction.getTransactionId().toString());
        ex.expect(CommandSpecException.class);
        ex.expectMessage("Couldn't resolve 'nonexistent' to a commit.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testMerge() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        RevCommit ancestor = geogig.command(CommitOp.class).setMessage("point1").call();
        testData.branch("branch1");
        testData.insert(TestData.point2);
        testData.add();
        RevCommit ours = geogig.command(CommitOp.class).setMessage("point2").call();
        testData.checkout("branch1");
        testData.insert(TestData.point3);
        testData.add();
        RevCommit theirs = geogig.command(CommitOp.class).setMessage("point3").call();
        testData.checkout("master");

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("commit", "branch1", "authorName", "Tester",
                "authorEmail", "tester@example.com", "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        assertTrue(transaction.command(FindTreeChild.class).setChildPath(path).call().isPresent());

        path = NodeRef.appendChild(TestData.pointsType.getTypeName(), TestData.point2.getID());
        assertTrue(transaction.command(FindTreeChild.class).setChildPath(path).call()
                .isPresent());

        path = NodeRef.appendChild(TestData.pointsType.getTypeName(), TestData.point3.getID());
        assertTrue(transaction.command(FindTreeChild.class).setChildPath(path).call()
                .isPresent());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject merge = response.getJsonObject("Merge");
        assertEquals(ours.getId().toString(), merge.getString("ours"));
        assertEquals(ancestor.getId().toString(), merge.getString("ancestor"));
        assertEquals(theirs.getId().toString(), merge.getString("theirs"));
        assertNotNull(merge.getString("mergedCommit"));
    }

    @Test
    public void testMergeConflict() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        RevCommit ancestor = geogig.command(CommitOp.class).setMessage("point1").call();
        testData.branch("branch1");
        testData.insert(TestData.point1_modified);
        testData.add();
        RevCommit ours = geogig.command(CommitOp.class).setMessage("modify point1").call();
        ObjectId point1_id = RevFeature.builder().build(TestData.point1_modified).getId();
        testData.checkout("branch1");
        testData.remove(TestData.point1);
        testData.add();
        RevCommit theirs = geogig.command(CommitOp.class).setMessage("remove point1").call();
        testData.checkout("master");

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("commit", "branch1", "authorName", "Tester",
                "authorEmail", "tester@example.com", "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject merge = response.getJsonObject("Merge");
        assertEquals(ours.getId().toString(), merge.getString("ours"));
        assertEquals(ancestor.getId().toString(), merge.getString("ancestor"));
        assertEquals(theirs.getId().toString(), merge.getString("theirs"));
        assertEquals(1, merge.getInt("conflicts"));
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

    // This scenario sets up 3 features on the master branch, creates a "branch1" branch from the
    // initial commit, then adds a 4th feature to both branches, edits feature 1 differently on each
    // branch, and deletes feature 2 on master, and deletes feature 3 on branch1 and finally tries
    // to merge "branch1" onto "master. It should produce a merge conflict, and the deleted features
    // shouldn't cause the conlfict report to throw a NullPointerException.
    @Test
    public void testMergeConflict2() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        // add 3 polys to "master"
        testData.checkout("master");
        testData.insert(TestData.poly1, TestData.poly2, TestData.poly3);
        testData.add();
        RevCommit ancestor = geogig.command(CommitOp.class).setMessage("initial commit").call();
        // create "branch1"
        testData.branch("branch1");
        // add a feature, modifiy a feature and delete a feature
        testData.insert(TestData.poly1_modified1, TestData.poly4);
        testData.remove(TestData.poly2);
        testData.add();
        // commit the change to "master"
        RevCommit masterCommit = geogig.command(CommitOp.class).setMessage("master edit").call();
        // get the feature ID for poly1
        ObjectId oursPoly1Id = RevFeature.builder().build(TestData.poly1_modified1).getId();
        // checkout "branch1"
        testData.checkout("branch1");
        // add a feature, modify the same feature above, but differently, and delete a different
        // feature
        testData.insert(TestData.poly1_modified2, TestData.poly4);
        testData.remove(TestData.poly3);
        testData.add();
        // commit the change to "branch1"
        RevCommit branch1Commit = geogig.command(CommitOp.class).setMessage("branch1 edit").call();
        // get the feature ID for poly1
        ObjectId theirsPoly1Id = RevFeature.builder().build(TestData.poly1_modified2).getId();
        // checkout "master"
        testData.checkout("master");
        // now build the Merge command
        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("commit", "branch1", "authorName", "Tester",
                "authorEmail", "tester@example.com", "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject merge = response.getJsonObject("Merge");
        assertEquals(masterCommit.getId().toString(), merge.getString("ours"));
        assertEquals(ancestor.getId().toString(), merge.getString("ancestor"));
        assertEquals(branch1Commit.getId().toString(), merge.getString("theirs"));
        assertEquals(1, merge.getInt("conflicts"));
        JsonArray featureArray = merge.getJsonArray("Feature");
        assertEquals(1, featureArray.getValuesAs(JsonValue.class).size());
        JsonObject feature = featureArray.getJsonObject(0);
        assertEquals("CONFLICT", feature.getString("change"));
        String path = NodeRef.appendChild(TestData.polysType.getTypeName(),
                TestData.poly1.getID());
        assertEquals(path, feature.getString("id"));
        JsonArray geometryArray = feature.getJsonArray("geometry");
        assertEquals(1, geometryArray.getValuesAs(JsonValue.class).size());
        String geometry = geometryArray.getString(0);
        assertEquals("POLYGON ((-4 -4, -4 4, 4 4, 4 -4, -4 -4))", geometry);
        assertEquals(oursPoly1Id.toString(), feature.getString("ourvalue"));
        assertEquals(theirsPoly1Id.toString(), feature.getString("theirvalue"));
    }
}
