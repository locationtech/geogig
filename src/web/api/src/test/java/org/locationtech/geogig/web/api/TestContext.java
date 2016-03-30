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

import static org.locationtech.geogig.rest.Variants.CSV_MEDIA_TYPE;

import java.util.function.Function;

import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.rest.repository.SingleRepositoryProvider;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.resource.Representation;

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

    public void setRequestMethod(Method method) {
        ((TestCommandContext) get()).setRequestMethod(method);
    }

    public CommandResponse getCommandResponse() {
        return context.commandResponse;
    }

    public StreamResponse getStreamCommandResponse() {
        return context.streamResponse;
    }

    public Representation getRepresentation(MediaType format) {
        return context.getRepresentation(format, null);
    }

    public void reset() {
        context = new TestCommandContext(testRepo);
    }

    private static class TestCommandContext implements CommandContext {

        private TestRepository repo;

        private CommandResponse commandResponse;

        private StreamResponse streamResponse;

        private Function<MediaType, Representation> representation;

        private Method requestMethod = Method.GET;

        private RepositoryProvider repoProvider = null;

        public TestCommandContext(TestRepository testRepo) {
            Preconditions.checkNotNull(testRepo);
            this.repo = testRepo;
        }

        public void createUninitializedRepo() {
            repo.getGeogig(false);
        }

        @Override
        public GeoGIG getGeoGIG() {
            return repo.getGeogig();
        }

        public void setRequestMethod(Method method) {
            this.requestMethod = method;
        }

        public Representation getRepresentation(MediaType format, String callback) {
            if (representation != null) {
                return representation.apply(format);
            }
            if (streamResponse != null) {
                if (format != CSV_MEDIA_TYPE) {
                    throw new CommandSpecException(
                            "Unsupported Media Type: This response is only compatible with text/csv.");
                }
                return new StreamWriterRepresentation(format, streamResponse);
            }
            if (format != MediaType.APPLICATION_JSON && format != MediaType.APPLICATION_XML) {
                throw new CommandSpecException(
                        "Unsupported Media Type: This response is only compatible with application/json and application/xml.");
            }
            return new CommandResponseJettisonRepresentation(format, commandResponse, callback);
        }

        @Override
        public void setResponseContent(CommandResponse responseContent) {
            this.commandResponse = responseContent;
            this.streamResponse = null;
            this.representation = null;
        }

        @Override
        public void setResponseContent(StreamResponse responseContent) {
            this.streamResponse = responseContent;
            this.commandResponse = null;
            this.representation = null;
        }

        @Override
        public String getBaseURL() {
            return "/geogig";
        }

        @Override
        public void setResponse(Function<MediaType, Representation> representation) {
            this.representation = representation;
            this.commandResponse = null;
            this.streamResponse = null;
        }

        @Override
        public Method getMethod() {
            return requestMethod;
        }

        @Override
        public RepositoryProvider getRepositoryProvider() {
            if (repoProvider == null) {
                repoProvider = new SingleRepositoryProvider(repo.getGeogig());
            }
            return repoProvider;
        }

    }
}
