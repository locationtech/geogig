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

import javax.json.JsonObject;

import org.junit.Test;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

import com.google.common.base.Optional;

public class UpdateRefTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "updateref";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return UpdateRef.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("name", "master", "delete", "true", "newValue",
                "newRefValue");

        UpdateRef op = (UpdateRef) buildCommand(options);
        assertTrue(op.delete);
        assertEquals("master", op.name);
        assertEquals("newRefValue", op.newValue);
    }

    @Test
    public void testUpdateRefNoName() {
        ParameterSet options = TestParams.of("newValue", "newValue");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Required parameter 'name' was not provided.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testUnspecifiedUpdate() {
        ParameterSet options = TestParams.of("name", "master");

        ex.expect(CommandSpecException.class);
        ex.expectMessage(
                "Nothing specified to update with, must specify either deletion or new value to update to.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testDeleteRef() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Optional<Ref> branch1 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch1").call();

        assertTrue(branch1.isPresent());

        String refName = branch1.get().getName();
        String oldObjectId = branch1.get().getObjectId().toString();

        ParameterSet options = TestParams.of("name", "branch1", "delete", "true");
        buildCommand(options).run(testContext.get());

        branch1 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class).setName("branch1")
                .call();
        assertFalse(branch1.isPresent());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        String expected = "{\"name\":\"" + refName + "\", \"objectId\":\"" + oldObjectId + "\"}";

        assertTrue(jsonEquals(toJSON(expected), response.getJsonObject("ChangedRef"), true));
    }

    @Test
    public void testDeleteSymRef() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Optional<Ref> head = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("HEAD").call();

        assertTrue(head.isPresent());
        assertTrue(head.get() instanceof SymRef);

        String refName = head.get().getName();
        String oldObjectId = head.get().getObjectId().toString();
        String oldTarget = ((SymRef) head.get()).getTarget();

        ParameterSet options = TestParams.of("name", "HEAD", "delete", "true");
        buildCommand(options).run(testContext.get());

        head = geogig.command(org.locationtech.geogig.plumbing.RefParse.class).setName("HEAD")
                .call();
        assertFalse(head.isPresent());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        String expected = "{\"name\":\"" + refName + "\", \"objectId\":\"" + oldObjectId
                + "\", \"target\":\"" + oldTarget + "\"}";

        assertTrue(jsonEquals(toJSON(expected), response.getJsonObject("ChangedRef"), true));
    }

    @Test
    public void testUpdateInvalidRef() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("name", "nonexistent", "newValue", "newvalue");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Invalid name: nonexistent");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testUpdateRef() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref branch1 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch1").call().get();
        Ref master = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("master").call().get();

        ParameterSet options = TestParams.of("name", "branch1", "newValue",
                master.getObjectId().toString());
        buildCommand(options).run(testContext.get());

        branch1 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class).setName("branch1")
                .call().get();

        assertEquals(master.getObjectId(), branch1.getObjectId());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        String expected = "{\"name\":\"" + branch1.getName() + "\", \"objectId\":\""
                + master.getObjectId().toString() + "\"}";

        assertTrue(jsonEquals(toJSON(expected), response.getJsonObject("ChangedRef"), true));
    }

    @Test
    public void testUpdateRefInvalidTarget() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("name", "branch1", "newValue", "nonexistent");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Invalid new value: nonexistent");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testUpdateSymRef() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref branch1 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch1").call().get();
        Ref master = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("master").call().get();
        SymRef head = (SymRef) geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("HEAD").call().get();

        assertEquals(master.getName(), head.getTarget());
        assertEquals(master.getObjectId(), head.getObjectId());

        ParameterSet options = TestParams.of("name", "HEAD", "newValue", "branch1");
        buildCommand(options).run(testContext.get());

        head = (SymRef) geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("HEAD").call().get();

        assertEquals(branch1.getName(), head.getTarget());
        assertEquals(branch1.getObjectId(), head.getObjectId());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        String expected = "{\"name\":\"" + head.getName() + "\", \"objectId\":\""
                + branch1.getObjectId().toString() + "\",\"target\":\"" + branch1.getName() + "\"}";

        assertTrue(jsonEquals(toJSON(expected), response.getJsonObject("ChangedRef"), true));
    }

    @Test
    public void testUpdateSymRefInvalidTarget() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("name", "HEAD", "newValue", "nonexistent");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Invalid new target: nonexistent");
        buildCommand(options).run(testContext.get());
    }
}
