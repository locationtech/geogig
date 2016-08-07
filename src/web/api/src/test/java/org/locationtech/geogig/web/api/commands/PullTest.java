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

import java.net.URI;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeatureBuilder;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.porcelain.CloneOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.RemoteAddOp;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestContext;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;
import org.skyscreamer.jsonassert.JSONAssert;

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

        ParameterSet options = TestParams.of("remoteName", "origin", "ref", "master");
        buildCommand(options).run(testContext.get());

        Ref master = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref branch1 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/branch1").call().get();
        Ref branch2 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/branch2").call().get();

        assertEquals(remoteMaster.getObjectId(), master.getObjectId());
        assertEquals(remoteBranch1.getObjectId(), branch1.getObjectId());
        assertEquals(remoteBranch2.getObjectId(), branch2.getObjectId());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject pull = response.getJSONObject("Pull");
        JSONObject remoteObject = pull.getJSONObject("Fetch").getJSONObject("Remote");
        assertEquals(remote.getFetchURL(), remoteObject.getString("remoteURL"));
        JSONArray branch = remoteObject.getJSONArray("Branch");
        String expected = "[{'changeType':'ADDED_REF','name':'branch1','newValue':'"
                + branch1.getObjectId().toString() + "'},"
                + "{'changeType':'ADDED_REF','name':'branch2','newValue':'"
                + branch2.getObjectId().toString() + "'},"
                + "{'changeType':'ADDED_REF','name':'master','newValue':'"
                + master.getObjectId().toString() + "'}]";
        JSONAssert.assertEquals(expected, branch.toString(), false);
        assertEquals(remote.getFetchURL(), pull.getString("Remote"));
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

        ParameterSet options = TestParams.of("remoteName", "origin", "ref", "branch1:newbranch");
        buildCommand(options).run(testContext.get());

        Ref master = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/master").call().get();
        Ref branch1 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/branch1").call().get();
        Ref branch2 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/branch2").call().get();
        Ref newbranch = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("newbranch").call().get();

        assertEquals(remoteMaster.getObjectId(), master.getObjectId());
        assertEquals(remoteBranch1.getObjectId(), branch1.getObjectId());
        assertEquals(remoteBranch2.getObjectId(), branch2.getObjectId());
        assertEquals(remoteBranch1.getObjectId(), newbranch.getObjectId());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject pull = response.getJSONObject("Pull");
        JSONObject remoteObject = pull.getJSONObject("Fetch").getJSONObject("Remote");
        assertEquals(remote.getFetchURL(), remoteObject.getString("remoteURL"));
        JSONArray branch = remoteObject.getJSONArray("Branch");
        String expected = "[{'changeType':'ADDED_REF','name':'branch1','newValue':'"
                + branch1.getObjectId().toString() + "'},"
                + "{'changeType':'ADDED_REF','name':'branch2','newValue':'"
                + branch2.getObjectId().toString() + "'},"
                + "{'changeType':'ADDED_REF','name':'master','newValue':'"
                + master.getObjectId().toString() + "'}]";
        JSONAssert.assertEquals(expected, branch.toString(), false);
        assertEquals(remote.getFetchURL(), pull.getString("Remote"));
        assertEquals("newbranch", pull.getString("Ref"));
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

        geogig.command(CloneOp.class).setRepositoryURL(remoteURI.toURL().toString()).call();

        ParameterSet options = TestParams.of("remoteName", "origin", "ref", "master");
        buildCommand(options).run(testContext.get());

        Ref master = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/master").call().get();
        Ref branch1 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/branch1").call().get();
        Ref branch2 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("refs/remotes/origin/branch2").call().get();

        assertEquals(remoteMaster.getObjectId(), master.getObjectId());
        assertEquals(remoteBranch1.getObjectId(), branch1.getObjectId());
        assertEquals(remoteBranch2.getObjectId(), branch2.getObjectId());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject pull = response.getJSONObject("Pull");
        String expected = "{'Fetch':''}";
        JSONAssert.assertEquals(expected, pull.toString(), false);
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
        remoteGeogig.command(CloneOp.class).setDepth(1)
                .setRepositoryURL(originalURI.toURL().toString()).call();

        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        URI remoteURI = remoteGeogig.command(ResolveGeogigURI.class).call().get();

        geogig.command(RemoteAddOp.class).setName("origin").setURL(remoteURI.toURL().toString())
                .call();

        ParameterSet options = TestParams.of("remoteName", "origin", "ref", "master");

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
        geogig.command(CloneOp.class).setRepositoryURL(remoteURI.toURL().toString()).call();

        remoteTestData.insert(TestData.point1_modified);
        remoteTestData.add();
        RevCommit theirs = remoteGeogig.command(CommitOp.class).setMessage("modify point1").call();
        ObjectId point1_id = RevFeatureBuilder.build(TestData.point1_modified).getId();

        testData.remove(TestData.point1);
        testData.add();
        RevCommit ours = geogig.command(CommitOp.class).setMessage("remove point1").call();

        ParameterSet options = TestParams.of("remoteName", "origin", "ref", "master");
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject merge = response.getJSONObject("Merge");
        assertEquals(ours.getId().toString(), merge.getString("ours"));
        assertEquals(ancestor.getId().toString(), merge.getString("ancestor"));
        assertEquals(theirs.getId().toString(), merge.getString("theirs"));
        assertEquals(1, merge.getInt("conflicts"));
        JSONObject feature = merge.getJSONObject("Feature");
        assertEquals("CONFLICT", feature.get("change"));
        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        assertEquals(path, feature.get("id"));
        assertEquals("POINT (0 0)", feature.get("geometry"));
        assertEquals(point1_id.toString(), feature.get("theirvalue"));
        assertEquals(ObjectId.NULL.toString(), feature.get("ourvalue"));
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

        URI remoteURI = remoteGeogig.command(ResolveGeogigURI.class).call().get();
        geogig.command(CloneOp.class).setRepositoryURL(remoteURI.toURL().toString()).call();

        remoteTestData.insert(TestData.point1_modified);
        remoteTestData.add();
        RevCommit theirs = remoteGeogig.command(CommitOp.class).setMessage("modify point1").call();
        ObjectId point1_id = RevFeatureBuilder.build(TestData.point1_modified).getId();

        testData.remove(TestData.point1);
        testData.add();
        RevCommit ours = geogig.command(CommitOp.class).setMessage("remove point1").call();
        testData.branchAndCheckout("branch1");

        ParameterSet options = TestParams.of("remoteName", "origin", "ref", "master:branch1");
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject merge = response.getJSONObject("Merge");
        assertEquals(ours.getId().toString(), merge.getString("ours"));
        assertEquals(ancestor.getId().toString(), merge.getString("ancestor"));
        assertEquals(theirs.getId().toString(), merge.getString("theirs"));
        assertEquals(1, merge.getInt("conflicts"));
        JSONObject feature = merge.getJSONObject("Feature");
        assertEquals("CONFLICT", feature.get("change"));
        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        assertEquals(path, feature.get("id"));
        assertEquals("POINT (0 0)", feature.get("geometry"));
        assertEquals(point1_id.toString(), feature.get("theirvalue"));
        assertEquals(ObjectId.NULL.toString(), feature.get("ourvalue"));
    }
}
