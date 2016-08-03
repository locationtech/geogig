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

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;

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
        ex.expectMessage("Blame requires the path of a feature.");
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
        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONArray attributes = response.getJSONObject("Blame").getJSONArray("Attribute");
        // One entry for each attribute
        assertEquals(3, attributes.length());
        for (int i = 0; i < 3; i++) {
            JSONObject attribute = attributes.getJSONObject(i);
            JSONObject commit = attribute.getJSONObject("commit");
            assertEquals("point2, line2, poly2", commit.get("message"));
            JSONObject author = commit.getJSONObject("author");
            assertEquals("User", author.get("name"));
            assertEquals("user@example.com", author.get("email"));
        }

    }
}
