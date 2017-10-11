/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.web.api.JsonUtils.jsonEquals;
import static org.locationtech.geogig.web.api.JsonUtils.toJSONArray;

import java.net.URI;
import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestContext;
import org.locationtech.geogig.web.api.WebAPICommand;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class BranchTest extends AbstractWebOpTest {

    @Rule
    public TestContext remoteTestContext = new TestContext();

    @Override
    protected String getRoute() {
        return "branch";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Branch.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("list", "true", "remotes", "true", "branchName",
                "testbranch", "force", "true", "autoCheckout", "true", "orphan", "true", "source",
                "COMMIT_X");

        Branch op = (Branch) buildCommand(options);

        assertTrue(op.list);
        assertTrue(op.remotes);
        assertEquals("testbranch", op.branchName);
        assertEquals("COMMIT_X", op.source);

        // NOTE these options are disabled (not being set), amend the test case if enabled
        assertFalse(op.autoCheckout);
        assertFalse(op.force);
        assertFalse(op.orphan);
    }

    @Test
    public void createBranchEmptyHistory() {
        ParameterSet options = TestParams.of("branchName", "newBranch");
        Branch cmd = (Branch) buildCommand(options);
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("HEAD has no commits");
        cmd.run(testContext.get());
    }

    @Test
    public void createBranch() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        // have a commit to allow creating branch
        RevCommit commit = geogig.command(CommitOp.class).setAllowEmpty(true)
                .setMessage("initial commit").call();

        ParameterSet options = TestParams.of("branchName", "newBranch");
        WebAPICommand cmd = buildCommand(options);
        cmd.run(testContext.get());

        JsonObject obj = getJSONResponse().getJsonObject("response");
        assertTrue(obj.getBoolean("success"));
        JsonObject branchResponse = obj.getJsonObject("BranchCreated");
        assertNotNull(branchResponse);
        assertEquals("newBranch", branchResponse.getString("name"));
        assertEquals(commit.getId().toString(), branchResponse.getString("source"));

        ImmutableList<Ref> branchRefs = geogig.command(BranchListOp.class).call();
        assertEquals(2, branchRefs.size());
        List<String> branchNames = Lists.transform(branchRefs, (r) -> r.getName());
        assertTrue(branchNames.toString(), branchNames.contains("refs/heads/newBranch"));
    }

    @Test
    public void listBranches() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("list", "true");
        WebAPICommand cmd = buildCommand(options);
        cmd.run(testContext.get());

        JsonObject obj = getJSONResponse().getJsonObject("response");
        assertTrue(obj.getBoolean("success"));
        JsonArray localBranches = obj.getJsonObject("Local").getJsonArray("Branch");
        String expected = "[{\"name\":\"branch1\"},{\"name\":\"branch2\"},{\"name\":\"master\"}]";
        assertTrue(jsonEquals(toJSONArray(expected), localBranches, true));
        JsonObject remote = obj.getJsonObject("Remote");
        JsonArray remoteBranches = remote.getJsonArray("Branch");
        assertTrue(remoteBranches.getValuesAs(JsonValue.class).isEmpty());
    }

    @Test
    public void listBranchesIncludingRemoteBranches() throws Exception {
        Repository remoteGeogig = remoteTestContext.get().getRepository();
        TestData remoteTestData = new TestData(remoteGeogig);
        remoteTestData.init();
        remoteTestData.loadDefaultData();

        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        URI remoteURI = remoteGeogig.command(ResolveGeogigURI.class).call().get();

        geogig.command(CloneOp.class).setRemoteURI(remoteURI).call();

        ParameterSet options = TestParams.of("list", "true", "remotes", "true");
        WebAPICommand cmd = buildCommand(options);
        cmd.run(testContext.get());

        JsonObject obj = getJSONResponse().getJsonObject("response");
        assertTrue(obj.getBoolean("success"));
        JsonArray localBranches = obj.getJsonObject("Local").getJsonArray("Branch");
        assertEquals(3, localBranches.getValuesAs(JsonValue.class).size());
        String expected = "[{\"name\":\"branch1\"},{\"name\":\"branch2\"},{\"name\":\"master\"}]";
        assertTrue(jsonEquals(toJSONArray(expected), localBranches, true));

        expected = "[{\"remoteName\":\"origin\",\"name\":\"branch1\"},{\"remoteName\":\"origin\",\"name\":\"branch2\"},{\"remoteName\":\"origin\",\"name\":\"master\"}]";
        JsonArray remoteBranches = obj.getJsonObject("Remote").getJsonArray("Branch");
        assertEquals(3, remoteBranches.getValuesAs(JsonValue.class).size());
        assertTrue(jsonEquals(toJSONArray(expected), remoteBranches, true));
    }

    @Test
    public void testNothingToDo() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of();

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Nothing to do.");
        buildCommand(options).run(testContext.get());
    }
}
