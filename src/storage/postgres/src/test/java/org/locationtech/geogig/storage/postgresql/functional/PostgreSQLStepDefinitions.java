/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.functional;

import java.net.URI;
import java.net.URISyntaxException;

import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.cli.test.functional.CLIContextProvider;
import org.locationtech.geogig.cli.test.functional.TestRepoURIBuilder;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;

import com.google.common.base.Throwables;

import cucumber.runtime.java.StepDefAnnotation;

/**
 *
 */
@StepDefAnnotation
public class PostgreSQLStepDefinitions {

    private CLIContextProvider contextProvider;

    @cucumber.api.java.Before(order = 1)
    public void before() throws Throwable {
        contextProvider = CLIContextProvider.get();
        contextProvider.setURIBuilder(new TestRepoURIBuilder() {

            private PGTemporaryTestConfig testConfig;

            @Override
            public void before() throws Throwable {
                // nothing to do
            }

            @Override
            public void after() {
                if (testConfig != null) {
                    testConfig.after();
                }
            }

            @Override
            public URI newRepositoryURI(String name, Platform platform) throws URISyntaxException {
                testConfig = new PGTemporaryTestConfig(name);
                try {
                    testConfig.before();
                } catch (Throwable e) {
                    throw Throwables.propagate(e);
                }
                String repoURI = testConfig.getRepoURL();
                System.err.println("Using repoURI: " + repoURI);
                return new URI(repoURI);
            }
        });
        // don't call before, let DfaultStepDefinitions do it
        // contextProvider.before();
    }

}
