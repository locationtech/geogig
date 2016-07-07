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
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;

public class RefParseTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "refparse";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return RefParse.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("name", "refName");

        RefParse op = (RefParse) buildCommand(options);
        assertEquals("refName", op.refSpec);
    }

    @Test
    public void testRequireName() {
        ParameterSet options = TestParams.of();

        ex.expect(CommandSpecException.class);
        ex.expectMessage("No name was given.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testUnresolvedName() {
        ParameterSet options = TestParams.of("name", "doesNotExist");

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Unable to parse the provided name.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testRefParse() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref branch1 = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName("branch1").call().get();

        ParameterSet options = TestParams.of("name", "branch1");
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject ref = response.getJSONObject("Ref");
        assertEquals(branch1.getName(), ref.getString("name"));
        assertEquals(branch1.getObjectId().toString(), ref.getString("objectId"));
    }
}
