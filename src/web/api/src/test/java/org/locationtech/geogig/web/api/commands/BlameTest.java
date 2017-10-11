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

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

public class BlameTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "blame";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Blame.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("commit", "master", "path", "some.path");

        Blame op = (Blame) buildCommand(options);
        assertEquals("master", op.branchOrCommit);
        assertEquals("some.path", op.path);
    }

    @Test
    public void testInvalidBranch() throws Exception {
        TestData testData = new TestData(testContext.get().getRepository());
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("commit", "nonexistentBranch", "path", "some.path");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Could not resolve branch or commit");
        buildCommand(options).run(testContext.get());
    }
    
    @Test
    public void testBlameNoPath() throws Exception {
        TestData testData = new TestData(testContext.get().getRepository());
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("commit", "branch1");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Required parameter 'path' was not provided.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testBlameNonexistentFeature() throws Exception {
        TestData testData = new TestData(testContext.get().getRepository());
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("commit", "branch1", "path", "nonexistent.feature");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("The supplied path does not exist");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testBlameNotAFeature() throws Exception {
        TestData testData = new TestData(testContext.get().getRepository());
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("commit", "branch1", "path",
                TestData.pointsType.getTypeName());
        ex.expect(CommandSpecException.class);
        ex.expectMessage("The supplied path does not resolve to a feature");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testBlame() throws Exception {
        TestData testData = new TestData(testContext.get().getRepository());
        testData.init("User", "user@example.com");
        testData.loadDefaultData();

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point2.getID());
        ParameterSet options = TestParams.of("commit", "branch1", "path", path);
        buildCommand(options).run(testContext.get());
        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray attributes = response.getJsonObject("Blame").getJsonArray("Attribute");
        // One entry for each attribute
        assertEquals(3, attributes.getValuesAs(JsonValue.class).size());
        for (int i = 0; i < 3; i++) {
            JsonObject attribute = attributes.getJsonObject(i);
            JsonObject commit = attribute.getJsonObject("commit");
            assertEquals("point2, line2, poly2", commit.getString("message"));
            JsonObject author = commit.getJsonObject("author");
            assertEquals("User", author.getString("name"));
            assertEquals("user@example.com", author.getString("email"));
        }

    }
}
