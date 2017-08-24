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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.locationtech.geogig.cli.test.functional.CLITestContextBuilder;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.spring.config.GeoGigWebAPISpringConfig;
import org.locationtech.geogig.test.TestPlatform;
import org.locationtech.geogig.web.api.WebAPICommand;
import org.restlet.data.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;

import com.google.common.base.Optional;

@ContextConfiguration(classes = GeoGigWebAPISpringConfig.class)
@WebAppConfiguration
@RunWith(SpringRunner.class)
public class InitTest extends AbstractWebOpTest {

    @Autowired
    private WebApplicationContext springContext;

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
        testContext.setRequestMethod(RequestMethod.PUT);
        cmd.run(testContext.get());
    }

    @Test
    public void testInit() throws Exception {
        TemporaryFolder tmp = new TemporaryFolder();
        tmp.create();
        // assert Autowired Spring Context is not null
        assertNotNull(springContext);
        // get a request builder
        MockHttpServletRequestBuilder initRequest =
                MockMvcRequestBuilders.put("/repos/testRepo/init.json");
        // need to embed a RepsoitoryResolver into the request
        initRequest.requestAttr(RepositoryProvider.KEY,
                new TestRepositoryProvider(tmp.newFolder()));
        // get a Mock MVC based on the autowired context
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(springContext).build();
        // send the Init request, and verify the Status is 201 Created
        mvc.perform(initRequest).andExpect(status().isCreated()).andExpect(content().contentType(
                MediaType.APPLICATION_JSON)).andExpect(content().string(
                        "{\"response\":{\"success\":true,\"repo\":{\"name\":\"testRepo\",\"href\":\"http://localhost/repos/testRepo.json\"}}}"));
        tmp.delete();
    }

    private static class TestRepositoryProvider implements RepositoryProvider {

        private Repository repository;
        private final File rootDirectory;

        private TestRepositoryProvider(File rootDirectory) {
            this.rootDirectory = rootDirectory;
        }

        @Override
        public Optional<Repository> getGeogig(Request request) {
            throw new UnsupportedOperationException("Unnecessary for this test.");
        }

        @Override
        public Optional<Repository> getGeogig(String repositoryName) {
            throw new UnsupportedOperationException("Unnecessary for this test.");
        }

        @Override
        public boolean hasGeoGig(String repositoryName) {
            return repository != null;
        }

        @Override
        public Repository createGeogig(String repositoryName, Map<String, String> parameters) {
            if (repository != null) {
                throw new RuntimeException("Repository already exists");
            }
            // ignore the parameters for now, just create a new repo with the name
            RepositoryResolver resolver = RepositoryResolver.lookup(rootDirectory.toURI());
            URI repoURI = resolver.buildRepoURI(rootDirectory.toURI(), repositoryName);
            Hints hints = new Hints();
            hints.set(Hints.REPOSITORY_URL, repoURI.toString());
            hints.set(Hints.REPOSITORY_NAME, repositoryName);
            GlobalContextBuilder.builder(new CLITestContextBuilder(new TestPlatform(rootDirectory)));
            repository = GlobalContextBuilder.builder().build(hints).repository();
            return repository;
        }

        @Override
        public void delete(Request request) {
            throw new UnsupportedOperationException("Unnecessary for this test.");
        }

        @Override
        public void invalidate(String repoName) {
            throw new UnsupportedOperationException("Unnecessary for this test.");
        }

        @Override
        public Iterator<String> findRepositories() {
            throw new UnsupportedOperationException("Unnecessary for this test.");
        }
    }
}
