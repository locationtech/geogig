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
import org.locationtech.geogig.api.GeoGIG;

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

    public CommandResponse getCommandResponse() {
        return context.commandResponse;
    }

    public StreamResponse getStreamCommandResponse() {
        return context.streamResponse;
    }

    private static class TestCommandContext implements CommandContext {

        private TestRepository repo;

        private CommandResponse commandResponse;

        private StreamResponse streamResponse;

        public TestCommandContext(TestRepository testRepo) {
            Preconditions.checkNotNull(testRepo);
            this.repo = testRepo;
        }

        @Override
        public GeoGIG getGeoGIG() {
            return repo.getGeogig();
        }

        @Override
        public void setResponseContent(CommandResponse responseContent) {
            this.commandResponse = responseContent;
            this.streamResponse = null;
        }

        @Override
        public void setResponseContent(StreamResponse responseContent) {
            this.streamResponse = responseContent;
            this.commandResponse = null;
        }

    }
}
