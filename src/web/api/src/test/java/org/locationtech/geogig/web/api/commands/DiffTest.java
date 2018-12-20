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
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

public class DiffTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "diff";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Diff.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("oldRefSpec", "master~1", "newRefSpec", "master",
                "pathFilter", "Points", "showGeometryChanges", "true", "page", "3", "show", "10");

        Diff op = (Diff) buildCommand(options);
        assertTrue(op.showGeometryChanges);
        assertEquals("master~1", op.oldRefSpec);
        assertEquals("master", op.newRefSpec);
        assertEquals("Points", op.pathFilter);
        assertEquals(3, op.page);
        assertEquals(10, op.elementsPerPage);
    }

    @Test
    public void testDiffNoOldRef() {
        ParameterSet options = TestParams.of("newRefSpec", "master", "pathFilter", "Points",
                "showGeometryChanges", "true", "page", "3", "show", "10");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Required parameter 'oldRefSpec' was not provided.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testDiffEmptyOldRef() {
        ParameterSet options = TestParams.of("oldRefSpec", "   ", "newRefSpec", "master",
                "pathFilter", "Points", "showGeometryChanges", "true", "page", "3", "show", "10");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Invalid old ref spec");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testDiffNoGeometryChanges() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("oldRefSpec", "branch1~1", "newRefSpec", "branch1",
                "pathFilter", "Points", "showGeometryChanges", "false");
        buildCommand(options).run(testContext.get());

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point2.getID());

        RevFeature point2 = RevFeature.builder().build(TestData.point2);

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray diffArray = response.getJsonArray("diff");
        assertEquals(1, diffArray.getValuesAs(JsonValue.class).size());
        JsonObject diff = diffArray.getJsonObject(0);
        assertEquals("ADDED", diff.getString("changeType"));
        assertEquals(path, diff.getString("newPath"));
        assertEquals(point2.getId().toString(), diff.getString("newObjectId"));
        assertEquals("", diff.getString("path"));
        assertEquals(ObjectId.NULL.toString(), diff.getString("oldObjectId"));

    }

    @Test
    public void testDiff() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("oldRefSpec", "branch1~1", "newRefSpec", "branch1",
                "pathFilter", "Points", "showGeometryChanges", "true");
        buildCommand(options).run(testContext.get());

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point2.getID());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonArray featureArray = response.getJsonArray("Feature");
        assertEquals(1, featureArray.getValuesAs(JsonValue.class).size());
        JsonObject feature = featureArray.getJsonObject(0);
        assertEquals("ADDED", feature.getString("change"));
        assertEquals(path, feature.getString("id"));
        JsonArray geometryArray = feature.getJsonArray("geometry");
        assertEquals(1, geometryArray.getValuesAs(JsonValue.class).size());
        String geometry = geometryArray.getString(0);
        assertEquals("POINT (-10 -10)", geometry);
        assertEquals("EPSG:4326", feature.getString("crs"));
    }

}
