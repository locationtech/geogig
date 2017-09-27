/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors: Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.json.JsonObject;

import org.junit.Test;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;

public class VersionTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "version";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Version.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testVersion() throws Exception {
        ParameterSet options = TestParams.of();
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertNotNull(response.getString("ProjectVersion"));
        assertNotNull(response.getString("BuildTime"));
        assertNotNull(response.getString("BuildUserName"));
        assertNotNull(response.getString("BuildUserEmail"));
        assertNotNull(response.getString("GitBranch"));
        assertNotNull(response.getString("GitCommitID"));
        assertNotNull(response.getString("GitCommitTime"));
        assertNotNull(response.getString("GitCommitAuthorName"));
        assertNotNull(response.getString("GitCommitAuthorEmail"));
        assertNotNull(response.getString("GitCommitMessage"));
    }
}
