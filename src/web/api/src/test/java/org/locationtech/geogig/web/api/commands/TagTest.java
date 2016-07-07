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

import static org.junit.Assert.assertTrue;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.porcelain.TagCreateOp;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;
import org.skyscreamer.jsonassert.JSONAssert;

public class TagTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "tag";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Tag.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("list", "true");

        Tag op = (Tag) buildCommand(options);
        assertTrue(op.list);
    }

    @Test
    public void testTagNoList() {
        ParameterSet options = TestParams.of();

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Only listing tags is supported at this time.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testListTags() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref master = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref branch1 = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName("branch1").call().get();

        geogig.command(TagCreateOp.class).setCommitId(branch1.getObjectId()).setName("Branch1Tag")
                .call();
        geogig.command(TagCreateOp.class).setCommitId(master.getObjectId()).setName("MasterTag")
                .call();

        ParameterSet options = TestParams.of("list", "true");
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        String expectedTags = "[{'name':'Branch1Tag'},{'name':'MasterTag'}]";
        JSONAssert.assertEquals(expectedTags, response.getJSONArray("Tag").toString(), true);
    }
}
