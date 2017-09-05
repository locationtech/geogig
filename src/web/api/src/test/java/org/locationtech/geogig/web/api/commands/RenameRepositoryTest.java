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
import static org.locationtech.geogig.web.api.JsonUtils.jsonEquals;
import static org.locationtech.geogig.web.api.JsonUtils.toJSON;

import javax.json.JsonObject;

import org.junit.Test;
import org.locationtech.geogig.plumbing.ResolveRepositoryName;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.RESTUtils;
import org.locationtech.geogig.web.api.TestRepository;
import org.locationtech.geogig.web.api.WebAPICommand;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;

public class RenameRepositoryTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "rename";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return RenameRepository.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("name", "repoName");

        RenameRepository op = (RenameRepository) buildCommand(options);
        assertEquals("repoName", op.name);
    }

    @Test
    public void testRename() throws Exception {

        String repoName = testContext.get().getRepository().command(ResolveRepositoryName.class)
                .call();

        assertEquals(TestRepository.REPO_NAME, repoName);

        ParameterSet options = TestParams.of("name", "newRepoName");
        WebAPICommand cmd = buildCommand(options);
        testContext.setRequestMethod(RequestMethod.POST);
        cmd.run(testContext.get());

        repoName = testContext.get().getRepository().command(ResolveRepositoryName.class).call();

        assertEquals("newRepoName", repoName);

        String expectedURL = RESTUtils.buildHref(testContext.get().getBaseURL(),
                RepositoryProvider.BASE_REPOSITORY_ROUTE + "/" + "newRepoName",
                MediaType.APPLICATION_JSON);

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(jsonEquals(
                toJSON("{\"success\":true, \"repo\": {\"name\": \"newRepoName\", \"href\": \""
                        + expectedURL + "\"}}"),
                response, true));
    }

    @Test
    public void testRenameNoName() throws Exception {
        ParameterSet options = TestParams.of();
        WebAPICommand cmd = buildCommand(null);
        testContext.setRequestMethod(RequestMethod.POST);

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Required parameter 'name' was not provided.");
        cmd.setParameters(options);
    }
}
