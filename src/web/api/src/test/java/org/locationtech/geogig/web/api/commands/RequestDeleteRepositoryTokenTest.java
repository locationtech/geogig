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

import javax.json.JsonObject;

import org.junit.Test;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.WebAPICommand;

public class RequestDeleteRepositoryTokenTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "delete";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return RequestDeleteRepositoryToken.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testRequestToken() throws Exception {
        ParameterSet options = TestParams.of();
        WebAPICommand cmd = buildCommand(options);
        cmd.run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        String token = response.getString("token");
        assertTrue(token.length() > 0);
    }
}
