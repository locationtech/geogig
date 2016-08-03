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

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.plumbing.ResolveRepositoryName;
import org.locationtech.geogig.rest.repository.RESTUtils;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestParams;
import org.locationtech.geogig.web.api.TestRepository;
import org.locationtech.geogig.web.api.WebAPICommand;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.skyscreamer.jsonassert.JSONAssert;

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

        String repoName = testContext.get().getRepository().command(ResolveRepositoryName.class).call();

        assertEquals(TestRepository.REPO_NAME, repoName);

        ParameterSet options = TestParams.of("name", "newRepoName");
        WebAPICommand cmd = buildCommand(options);
        testContext.setRequestMethod(Method.POST);
        cmd.run(testContext.get());

        repoName = testContext.get().getRepository().command(ResolveRepositoryName.class).call();

        assertEquals("newRepoName", repoName);

        String expectedURL = RESTUtils.buildHref(testContext.get().getBaseURL(),
                RepositoryProvider.BASE_REPOSITORY_ROUTE + "/" + "newRepoName",
                MediaType.APPLICATION_JSON);

        JSONObject response = getJSONResponse().getJSONObject("response");
        JSONAssert.assertEquals(
                "{'success':true, 'repo': {'name': 'newRepoName', 'href': '" + expectedURL + "'}}",
                response.toString(), true);
    }

    @Test
    public void testRenameNoName() throws Exception {
        ParameterSet options = TestParams.of();
        WebAPICommand cmd = buildCommand(options);
        testContext.setRequestMethod(Method.POST);

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("You must specify a new name for the repository.");
        cmd.run(testContext.get());
    }
}
