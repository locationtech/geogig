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

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureBuilder;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;

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
        ex.expectMessage("No old ref spec");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testDiffEmptyOldRef() {
        ParameterSet options = TestParams.of("oldRefSpec", "   ", "newRefSpec", "master",
                "pathFilter", "Points", "showGeometryChanges", "true", "page", "3", "show", "10");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("No old ref spec");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testDiffNoGeometryChanges() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("oldRefSpec", "branch1~1", "newRefSpec", "branch1",
                "pathFilter", "Points", "showGeometryChanges", "false");
        buildCommand(options).run(testContext.get());

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point2.getID());

        RevFeature point2 = RevFeatureBuilder.build(TestData.point2);

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject diff = response.getJSONObject("diff");
        assertEquals("ADDED", diff.getString("changeType"));
        assertEquals(path, diff.getString("newPath"));
        assertEquals(point2.getId().toString(), diff.getString("newObjectId"));
        assertEquals("", diff.getString("path"));
        assertEquals(ObjectId.NULL.toString(), diff.getString("oldObjectId"));

    }

    @Test
    public void testDiff() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("oldRefSpec", "branch1~1", "newRefSpec", "branch1",
                "pathFilter", "Points", "showGeometryChanges", "true");
        buildCommand(options).run(testContext.get());

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point2.getID());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject feature = response.getJSONObject("Feature");
        assertEquals("ADDED", feature.getString("change"));
        assertEquals(path, feature.getString("id"));
        assertEquals("POINT (-10 -10)", feature.getString("geometry"));
        assertEquals("EPSG:4326", feature.getString("crs"));
    }

}
