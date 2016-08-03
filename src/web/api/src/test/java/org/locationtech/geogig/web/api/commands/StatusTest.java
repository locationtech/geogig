/*
 * Copyright (c) 2016 Boundless and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors: Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureBuilder;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;
import org.skyscreamer.jsonassert.JSONAssert;

public class StatusTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "status";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Status.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("limit", "8", "offset", "4");

        Status op = (Status) buildCommand(options);
        assertEquals(8, op.limit);
        assertEquals(4, op.offset);
    }

    @Test
    public void testStatus() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        RevFeature point1 = RevFeatureBuilder.build(TestData.point1);
        String point1_path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        RevFeature point1_modified = RevFeatureBuilder.build(TestData.point1_modified);
        RevFeature point2 = RevFeatureBuilder.build(TestData.point2);
        String point2_path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point2.getID());
        RevFeature point3 = RevFeatureBuilder.build(TestData.point3);
        String point3_path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point3.getID());

        testData.checkout("master");
        testData.addAndCommit("Initial commit", TestData.point1, TestData.point2);
        testData.insert(TestData.point3);
        testData.add();
        testData.insert(TestData.point1_modified);
        testData.remove(TestData.point2);

        ParameterSet options = TestParams.of();
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject staged = response.getJSONObject("staged");
        String expectedStaged = "{'changeType':'ADDED','newPath':'" + point3_path
                + "','newObjectId':'" + point3.getId().toString() + "','path':'','oldObjectId':'"
                + ObjectId.NULL.toString() + "'}";
        JSONAssert.assertEquals(expectedStaged, staged.toString(), false);

        JSONArray unstaged = response.getJSONArray("unstaged");
        String expectedUnstaged = "[{'changeType':'MODIFIED','newPath':'" + point1_path
                + "','newObjectId':'" + point1_modified.getId().toString() + "','path':'"
                + point1_path + "','oldObjectId':'" + point1.getId().toString()
                + "'},{'changeType':'REMOVED','path':'" + point2_path + "','oldObjectId':'"
                + point2.getId().toString() + "','newPath':'','newObjectId':'"
                + ObjectId.NULL.toString() + "'}]";
        JSONAssert.assertEquals(expectedUnstaged, unstaged.toString(), false);
    }

}
