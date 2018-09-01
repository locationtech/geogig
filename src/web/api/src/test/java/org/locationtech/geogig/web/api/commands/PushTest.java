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

import java.net.URI;

import javax.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.remotes.RemoteAddOp;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestContext;

public class PushTest extends AbstractWebOpTest {

    @Rule
    public TestContext remoteTestContext = new TestContext();

    @Rule
    public TestContext originalTestContext = new TestContext();

    @Override
    protected String getRoute() {
        return "push";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Push.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("all", "true", "ref", "master", "remoteName",
                "origin");

        Push op = (Push) buildCommand(options);
        assertEquals("master", op.refSpec);
        assertEquals("origin", op.remoteName);
        assertTrue(op.pushAll);
    }

    @Test
    public void testPush() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref master = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        Repository remoteGeogig = remoteTestContext.get().getRepository();
        TestData remoteTestData = new TestData(remoteGeogig);
        remoteTestData.init();
        remoteTestData.checkout("master");

        Ref remoteMaster = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        URI remoteURI = remoteGeogig.command(ResolveGeogigURI.class).call().get();

        geogig.command(RemoteAddOp.class).setName("origin").setURL(remoteURI.toURL().toString())
                .call();

        ParameterSet options = TestParams.of("remoteName", "origin", "ref", "master");
        buildCommand(options).run(testContext.get());

        remoteMaster = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        assertEquals(master.getObjectId(), remoteMaster.getObjectId());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals("Success", response.getString("Push"));
        assertTrue(response.getBoolean("dataPushed"));
    }

    @Test
    public void testPushAll() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref master = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref branch1 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch1").call().get();
        Ref branch2 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch2").call().get();

        Repository remoteGeogig = remoteTestContext.get().getRepository();
        TestData remoteTestData = new TestData(remoteGeogig);
        remoteTestData.init();
        remoteTestData.checkout("master");

        URI remoteURI = remoteGeogig.command(ResolveGeogigURI.class).call().get();

        geogig.command(RemoteAddOp.class).setName("origin").setURL(remoteURI.toURL().toString())
                .call();

        ParameterSet options = TestParams.of("remoteName", "origin", "all", "true");
        buildCommand(options).run(testContext.get());

        Ref remoteMaster = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref remoteBranch1 = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch1").call().get();
        Ref remoteBranch2 = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch2").call().get();

        assertEquals(master.getObjectId(), remoteMaster.getObjectId());
        assertEquals(branch1.getObjectId(), remoteBranch1.getObjectId());
        assertEquals(branch2.getObjectId(), remoteBranch2.getObjectId());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals("Success", response.getString("Push"));
        assertTrue(response.getBoolean("dataPushed"));
    }

    @Test
    public void testPushNoUpdates() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref master = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref branch1 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch1").call().get();
        Ref branch2 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch2").call().get();

        Repository remoteGeogig = remoteTestContext.get().getRepository();
        TestData remoteTestData = new TestData(remoteGeogig);
        remoteTestData.init();
        remoteTestData.checkout("master");

        URI remoteURI = remoteGeogig.command(ResolveGeogigURI.class).call().get();

        geogig.command(RemoteAddOp.class).setName("origin").setURL(remoteURI.toURL().toString())
                .call();

        URI localURI = geogig.command(ResolveGeogigURI.class).call().get();

        remoteGeogig.command(CloneOp.class).setRemoteURI(localURI).call();

        ParameterSet options = TestParams.of("remoteName", "origin", "all", "true");
        buildCommand(options).run(testContext.get());

        Ref remoteMaster = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref remoteBranch1 = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch1").call().get();
        Ref remoteBranch2 = remoteGeogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch2").call().get();

        assertEquals(master.getObjectId(), remoteMaster.getObjectId());
        assertEquals(branch1.getObjectId(), remoteBranch1.getObjectId());
        assertEquals(branch2.getObjectId(), remoteBranch2.getObjectId());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals("Success", response.getString("Push"));
        assertFalse(response.getBoolean("dataPushed"));
    }

    @Test
    public void testPushRemoteHasChanges() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Repository remoteGeogig = remoteTestContext.get().getRepository();
        TestData remoteTestData = new TestData(remoteGeogig);
        remoteTestData.init();
        remoteTestData.checkout("master");

        URI remoteURI = remoteGeogig.command(ResolveGeogigURI.class).call().get();

        geogig.command(RemoteAddOp.class).setName("origin").setURL(remoteURI.toURL().toString())
                .call();

        URI localURI = geogig.command(ResolveGeogigURI.class).call().get();

        remoteGeogig.command(CloneOp.class).setRemoteURI(localURI).call();

        remoteTestData.remove(TestData.point1);
        remoteTestData.add();
        remoteTestData.commit("Removed point1");

        ParameterSet options = TestParams.of("remoteName", "origin", "all", "true");

        ex.expect(CommandSpecException.class);
        ex.expectMessage(
                "Push failed: The remote repository has changes that would be lost in the event of a push.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testPushTooShallow() throws Exception {
        Repository originalGeogig = originalTestContext.get().getRepository();
        TestData originalTestData = new TestData(originalGeogig);
        originalTestData.init();

        URI originalURI = originalGeogig.command(ResolveGeogigURI.class).call().get();

        // remote repo is full clone
        Repository remoteGeogig = remoteTestContext.get().getRepository();
        remoteGeogig.command(CloneOp.class).setRemoteURI(originalURI).call();

        URI remoteURI = remoteGeogig.command(ResolveGeogigURI.class).call().get();

        // Add data to original repo
        originalTestData.loadDefaultData();

        // Local repo is shallow
        Repository geogig = testContext.get().getRepository();
        geogig.command(CloneOp.class).setDepth(1).setRemoteURI(originalURI).call();

        geogig.command(RemoteAddOp.class).setName("remote1").setURL(remoteURI.toURL().toString())
                .call();

        // Push from shallow to full clone
        ParameterSet options = TestParams.of("remoteName", "remote1", "ref", "master");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Push failed: There is not enough local history to complete the push.");
        buildCommand(options).run(testContext.get());
    }
}
