/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.rest.repository.SingleRepositoryProvider;
import org.locationtech.geogig.spring.dto.LegacyResponse;
import org.springframework.web.bind.annotation.RequestMethod;

import com.google.common.base.Preconditions;

/**
 * JUnit {@link Rule} to set up a temporary web API {@link CommandContext}.
 * <p>
 * The temporary repository is lazily built at {@link #get()}
 */
public class TestContext extends ExternalResource {

    private TestRepository testRepo;

    private TestCommandContext context;

    @Override
    protected void before() throws Throwable {
        testRepo = new TestRepository();
        testRepo.before();
        context = new TestCommandContext(testRepo);
    }

    @Override
    protected void after() {
        try {
            testRepo.after();
        } finally {
            testRepo = null;
            context = null;
        }
    }

    public CommandContext get() {
        Preconditions.checkState(context != null, "context not initialized");
        return context;
    }

    public void createUninitializedRepo() {
        ((TestCommandContext) get()).createUninitializedRepo();
    }

    public void setRequestMethod(RequestMethod method) {
        ((TestCommandContext) get()).setRequestMethod(method);
    }

    public LegacyResponse getCommandResponse() {
        return context.commandResponse;
    }

    public StreamResponse getStreamCommandResponse() {
        return context.streamResponse;
    }

    public void reset() {
        context = new TestCommandContext(testRepo);
    }

    private static class TestCommandContext implements CommandContext {

        private TestRepository repo;

        private LegacyResponse commandResponse;

        private StreamResponse streamResponse;

        private RequestMethod requestMethod = RequestMethod.GET;

        private RepositoryProvider repoProvider = null;

        public TestCommandContext(TestRepository testRepo) {
            Preconditions.checkNotNull(testRepo);
            this.repo = testRepo;
        }

        public void createUninitializedRepo() {
            repo.getGeogig(false);
        }

        @Override
        public Repository getRepository() {
            return repo.getGeogig().getRepository();
        }

        public void setRequestMethod(RequestMethod method) {
            this.requestMethod = method;
        }

        @Override
        public void setResponseContent(LegacyResponse responseContent) {
            this.commandResponse = responseContent;
            this.streamResponse = null;
        }

        @Override
        public void setResponseContent(StreamResponse responseContent) {
            this.streamResponse = responseContent;
            this.commandResponse = null;
        }

        @Override
        public String getBaseURL() {
            return "/geogig";
        }

        // @Override
        // public void setResponse(Function<MediaType, Representation> representation) {
        // this.representation = representation;
        // this.commandResponse = null;
        // this.streamResponse = null;
        // }

        @Override
        public RequestMethod getMethod() {
            return requestMethod;
        }

        @Override
        public RepositoryProvider getRepositoryProvider() {
            if (repoProvider == null) {
                repoProvider = new SingleRepositoryProvider(repo.getGeogig().getRepository());
            }
            return repoProvider;
        }

    }
}
