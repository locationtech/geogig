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

import javax.json.JsonObject;

import org.junit.Test;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

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
        ex.expectMessage("Required parameter 'name' was not provided.");
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
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref branch1 = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch1").call().get();

        ParameterSet options = TestParams.of("name", "branch1");
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject ref = response.getJsonObject("Ref");
        assertEquals(branch1.getName(), ref.getString("name"));
        assertEquals(branch1.getObjectId().toString(), ref.getString("objectId"));
    }
}
