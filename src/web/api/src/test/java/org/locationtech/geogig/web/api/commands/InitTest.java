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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.json.JsonObject;

import org.junit.Test;
import org.locationtech.geogig.web.api.RESTUtils;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;
import org.locationtech.geogig.web.api.TestRepository;
import org.locationtech.geogig.web.api.WebAPICommand;
import org.restlet.data.MediaType;
import org.restlet.data.Method;

public class InitTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "init";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Init.class;
    }

    @Override
    protected boolean requiresRepository() {
        return false;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testFailWithInitializedRepository() {
        ParameterSet options = TestParams.of();
        WebAPICommand cmd = buildCommand(options);

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Cannot run init on an already initialized repository.");
        testContext.setRequestMethod(Method.PUT);
        cmd.run(testContext.get());
    }

    @Test
    public void testInit() throws Exception {
        testContext.createUninitializedRepo();
        ParameterSet options = TestParams.of();
        WebAPICommand cmd = buildCommand(options);

        assertNull(testContext.get().getRepository());

        testContext.setRequestMethod(Method.PUT);
        cmd.run(testContext.get());

        String expectedURL = RESTUtils.buildHref(testContext.get().getBaseURL(),
                RepositoryProvider.BASE_REPOSITORY_ROUTE + "/" + TestRepository.REPO_NAME,
                MediaType.APPLICATION_JSON);

        assertNotNull(testContext.get().getRepository());
        assertTrue(testContext.get().getRepository().isOpen());
        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(
                TestData.jsonEquals(
                        TestData.toJSON("{\"success\":true, \"repo\": {\"name\": \""
                                + TestRepository.REPO_NAME + "\", \"href\": \"" + expectedURL + "\"}}"),
                response, true));

    }
}
