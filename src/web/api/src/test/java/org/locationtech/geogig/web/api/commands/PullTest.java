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
import static org.locationtech.geogig.web.api.JsonUtils.jsonEquals;
import static org.locationtech.geogig.web.api.JsonUtils.toJSON;
import static org.locationtech.geogig.web.api.JsonUtils.toJSONArray;

import java.net.URI;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.plumbing.remotes.RemoteAddOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.test.TestSupport;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestContext;

import com.google.common.base.Optional;

public class PullTest extends AbstractWebOpTest {

    @Rule
    public TestContext remoteTestContext = new TestContext();

    @Rule
    public TestContext originalTestContext = new TestContext();

    @Override
    protected String getRoute() {
        return "pull";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Pull.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("all", "true", "ref", "master", "remoteName", "origin",
                "authorName", "Tester", "authorEmail", "tester@example.com");

        Pull op = (Pull) buildCommand(options);
        assertEquals("master", op.refSpec);
        assertEquals("origin", op.remoteName);
        assertEquals("Tester", op.authorName.get());
        assertEquals("tester@example.com", op.authorEmail.get());
        assertTrue(op.fetchAll);
    }

    @Test
    public void testPull() throws Exception {
        Repository remoteGeogig = remoteTestContext.get().getRepository();
        TestData remoteTestData = new TestData(remoteGeogig);
        remoteTestData.init();
        remoteTestData.loadDefaultData();

        Ref remoteMaster = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref remoteBranch1 = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch1").call().get();
        Ref remoteBranch2 = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch2").call().get();

        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        URI remoteURI = remoteGeogig.command(ResolveGeogigURI.class).call().get();

        Remote remote = geogig.command(RemoteAddOp.class).setName("origin")
                .setURL(remoteURI.toURL().toString()).call();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString(), "remoteName", "origin", "ref", "master");
        buildCommand(options).run(testContext.get());

        Ref master = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Optional<Ref> branch1 = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/branch1").call();
        Optional<Ref> branch2 = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/branch2").call();

        assertEquals(remoteMaster.getObjectId(), master.getObjectId());
        assertFalse(branch1.isPresent());
        assertFalse(branch2.isPresent());

        JsonObject response = getJSONResponse().getJsonObject("response");
        TestSupport.verifyRepositoryContents(geogig);

        assertTrue(response.getBoolean("success"));
        JsonObject pull = response.getJsonObject("Pull");
        JsonObject remoteObject = pull.getJsonObject("Fetch").getJsonObject("Remote");
        assertEquals(remote.getFetchURL(), remoteObject.getString("remoteURL"));
        JsonArray branch = remoteObject.getJsonArray("Branch");

        String expected = "[{\"changeType\":\"ADDED_REF\",\"name\":\"master\",\"newValue\":\""
                + master.getObjectId().toString() + "\"}]";

        assertTrue(jsonEquals(toJSONArray(expected), branch, false));
        assertEquals(remote.getName(), pull.getString("Remote"));
        assertEquals("master", pull.getString("Ref"));
        assertEquals(9, pull.getInt("Added"));
        assertEquals(0, pull.getInt("Removed"));
        assertEquals(0, pull.getInt("Modified"));
    }

    @Test
    public void testPullNewBranch() throws Exception {
        Repository remoteGeogig = remoteTestContext.get().getRepository();
        TestData remoteTestData = new TestData(remoteGeogig);
        remoteTestData.init();
        remoteTestData.loadDefaultData();

        Ref remoteMaster = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref remoteBranch1 = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch1").call().get();
        Ref remoteBranch2 = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch2").call().get();

        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        URI remoteURI = remoteGeogig.command(ResolveGeogigURI.class).call().get();

        Remote remote = geogig.command(RemoteAddOp.class).setName("origin")
                .setURL(remoteURI.toURL().toString()).call();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString(), "remoteName", "origin", "ref",
                "branch1:newbranch");
        buildCommand(options).run(testContext.get());

        Optional<Ref> master = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/master").call();
        Optional<Ref> branch1 = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/branch1").call();
        Optional<Ref> branch2 = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/branch2").call();
        Optional<Ref> newbranch = transaction
                .command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/newbranch").call();
        assertFalse(master.isPresent());
        assertFalse(branch1.isPresent());
        assertFalse(branch2.isPresent());
        assertTrue(newbranch.isPresent());
        assertEquals(remoteBranch1.getObjectId(), newbranch.get().getObjectId());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject pull = response.getJsonObject("Pull");
        JsonObject remoteObject = pull.getJsonObject("Fetch").getJsonObject("Remote");
        assertEquals(remote.getFetchURL(), remoteObject.getString("remoteURL"));
        JsonArray branch = remoteObject.getJsonArray("Branch");
        String expected = "[{\"changeType\":\"ADDED_REF\",\"name\":\"newbranch\",\"newValue\":\""
                + newbranch.get().getObjectId() + "\"}]";
        assertTrue(jsonEquals(toJSONArray(expected), branch, false));
        assertEquals("origin", pull.getString("Remote"));
        assertEquals("master", pull.getString("Ref"));// pulls always onto the current branch
        assertEquals(6, pull.getInt("Added"));
        assertEquals(0, pull.getInt("Removed"));
        assertEquals(0, pull.getInt("Modified"));
    }

    @Test
    public void testPullNoUpdates() throws Exception {
        Repository remoteGeogig = remoteTestContext.get().getRepository();
        TestData remoteTestData = new TestData(remoteGeogig);
        remoteTestData.init();
        remoteTestData.loadDefaultData();

        Ref remoteMaster = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref remoteBranch1 = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch1").call().get();
        Ref remoteBranch2 = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch2").call().get();

        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        URI remoteURI = remoteGeogig.command(ResolveGeogigURI.class).call().get();

        geogig.command(CloneOp.class).setRemoteURI(remoteURI).call();

        TestSupport.verifyRepositoryContents(geogig);

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString(), "remoteName", "origin", "ref", "master");
        buildCommand(options).run(testContext.get());

        Ref master = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/master").call().get();
        Ref branch1 = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/branch1").call().get();
        Ref branch2 = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/branch2").call().get();

        assertEquals(remoteMaster.getObjectId(), master.getObjectId());
        assertEquals(remoteBranch1.getObjectId(), branch1.getObjectId());
        assertEquals(remoteBranch2.getObjectId(), branch2.getObjectId());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject pull = response.getJsonObject("Pull");
        JsonObject fetchObj = pull.getJsonObject("Fetch");

        assertTrue(jsonEquals(toJSON("{}"), fetchObj, false));
    }

    @Test
    public void testPullFromTooShallowClone() throws Exception {
        Repository originalGeogig = originalTestContext.get().getRepository();
        TestData originalTestData = new TestData(originalGeogig);
        originalTestData.init();
        originalTestData.loadDefaultData();

        URI originalURI = originalGeogig.command(ResolveGeogigURI.class).call().get();

        // Set up the shallow clone
        Repository remoteGeogig = remoteTestContext.get().getRepository();
        remoteGeogig.command(CloneOp.class).setDepth(1).setRemoteURI(originalURI).call();

        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        URI remoteURI = remoteGeogig.command(ResolveGeogigURI.class).call().get();

        geogig.command(RemoteAddOp.class).setName("origin").setURL(remoteURI.toURL().toString())
                .call();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString(), "remoteName", "origin", "ref", "master");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Unable to pull, the remote history is shallow.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testPullConflicts() throws Exception {
        Repository remoteGeogig = remoteTestContext.get().getRepository();
        TestData remoteTestData = new TestData(remoteGeogig);
        remoteTestData.init();

        remoteTestData.checkout("master");
        remoteTestData.insert(TestData.point1);
        remoteTestData.add();
        RevCommit ancestor = remoteGeogig.command(CommitOp.class).setMessage("point1").call();

        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        URI remoteURI = remoteGeogig.command(ResolveGeogigURI.class).call().get();
        geogig.command(CloneOp.class).setRemoteURI(remoteURI).call();
        TestSupport.verifySameContents(remoteGeogig, geogig);

        remoteTestData.insert(TestData.point1_modified);
        remoteTestData.add();
        RevCommit theirs = remoteGeogig.command(CommitOp.class).setMessage("modify point1").call();
        ObjectId point1_id = RevFeature.builder().build(TestData.point1_modified).getId();

        testData.remove(TestData.point1);
        testData.add();
        RevCommit ours = geogig.command(CommitOp.class).setMessage("remove point1").call();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString(), "remoteName", "origin", "ref", "master");
        buildCommand(options).run(testContext.get());
        TestSupport.verifyRepositoryContents(geogig);

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
        assertEquals(point1_id.toString(), feature.getString("theirvalue"));
        assertEquals(ObjectId.NULL.toString(), feature.getString("ourvalue"));
    }

    @Test
    public void testPullConflictsDifferentBranch() throws Exception {
        Repository remoteGeogig = remoteTestContext.get().getRepository();
        TestData remoteTestData = new TestData(remoteGeogig);
        remoteTestData.init();

        remoteTestData.checkout("master");
        remoteTestData.insert(TestData.point1);
        remoteTestData.add();
        RevCommit ancestor = remoteGeogig.command(CommitOp.class).setMessage("point1").call();

        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        geogig.command(CloneOp.class)//
                .setRemoteURI(remoteGeogig.getLocation())//
                .setCloneURI(geogig.getLocation())//
                .call();

        TestSupport.verifySameContents(remoteGeogig, geogig);

        remoteTestData.insert(TestData.point1_modified);
        remoteTestData.add();
        RevCommit theirs = remoteGeogig.command(CommitOp.class).setMessage("modify point1").call();
        ObjectId point1_id = RevFeature.builder().build(TestData.point1_modified).getId();

        testData.remove(TestData.point1);
        testData.add();
        RevCommit ours = geogig.command(CommitOp.class).setMessage("remove point1").call();
        testData.branchAndCheckout("branch1");
        assertTrue(geogig.command(FindTreeChild.class).setChildPath("Points").call().isPresent());

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString(), "remoteName", "origin", "ref", "master");
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
        assertEquals(point1_id.toString(), feature.getString("theirvalue"));
        assertEquals(ObjectId.NULL.toString(), feature.getString("ourvalue"));
    }
}
