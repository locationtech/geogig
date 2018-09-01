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

import java.net.URI;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.remotes.RemoteAddOp;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestContext;

public class FetchTest extends AbstractWebOpTest {

    @Rule
    public TestContext remoteTestContext = new TestContext();

    @Rule
    public TestContext originalTestContext = new TestContext();

    @Override
    protected String getRoute() {
        return "fetch";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Fetch.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("prune", "true", "all", "true", "remote", "origin");

        Fetch op = (Fetch) buildCommand(options);
        assertEquals("origin", op.remote);
        assertTrue(op.prune);
        assertTrue(op.fetchAll);
    }

    @Test
    public void testFetch() throws Exception {
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

        ParameterSet options = TestParams.of("remote", "origin");
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

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject remoteObject = response.getJsonObject("Fetch").getJsonObject("Remote");
        assertEquals(remote.getFetchURL(), remoteObject.getString("remoteURL"));
        JsonArray branch = remoteObject.getJsonArray("Branch");
        String expected = "[{\"changeType\":\"ADDED_REF\",\"name\":\"branch1\",\"newValue\":\""
                + branch1.getObjectId().toString() + "\"},"
                + "{\"changeType\":\"ADDED_REF\",\"name\":\"branch2\",\"newValue\":\""
                + branch2.getObjectId().toString() + "\"},"
                + "{\"changeType\":\"ADDED_REF\",\"name\":\"master\",\"newValue\":\""
                + master.getObjectId().toString() + "\"}]";
        System.err.println(expected);
        System.err.println(branch);
        assertTrue(jsonEquals(toJSONArray(expected), branch, false));
    }

    @Test
    public void testFetchAll() throws Exception {
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

        ParameterSet options = TestParams.of("all", "true");
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

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject remoteObject = response.getJsonObject("Fetch").getJsonObject("Remote");
        assertEquals(remote.getFetchURL(), remoteObject.getString("remoteURL"));
        JsonArray branch = remoteObject.getJsonArray("Branch");
        String expected = "[{\"changeType\":\"ADDED_REF\",\"name\":\"branch1\",\"newValue\":\""
                + branch1.getObjectId().toString() + "\"},"
                + "{\"changeType\":\"ADDED_REF\",\"name\":\"branch2\",\"newValue\":\""
                + branch2.getObjectId().toString() + "\"},"
                + "{\"changeType\":\"ADDED_REF\",\"name\":\"master\",\"newValue\":\""
                + master.getObjectId().toString() + "\"}]";
        assertTrue(jsonEquals(toJSONArray(expected), branch, false));
    }

    @Test
    public void testFetchNoneSpecified() throws Exception {
        ParameterSet options = TestParams.of();

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Nothing specified to fetch from.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testFetchFromTooShallowClone() throws Exception {
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

        ParameterSet options = TestParams.of("remote", "origin");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Unable to fetch, the remote history is shallow.");
        buildCommand(options).run(testContext.get());
    }
}
