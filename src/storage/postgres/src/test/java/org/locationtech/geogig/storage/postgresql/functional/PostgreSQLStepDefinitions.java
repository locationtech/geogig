/* Copyright (c) 2014-2016 Boundless and others.
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
import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.cli.test.functional.CLIContextProvider;
import org.locationtech.geogig.cli.test.functional.TestRepoURIBuilder;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.postgresql.config.PGTemporaryTestConfig;

import cucumber.api.Scenario;
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
        contextProvider.setURIBuilder(new PGTestRepoURIBuilder());
        // don't call before, let DfaultStepDefinitions do it
        // contextProvider.before();
    }

    static final class PGTestRepoURIBuilder extends TestRepoURIBuilder {
        private List<PGTemporaryTestConfig> testConfigs = new ArrayList<>();

        public @Override void before(Scenario scenario) throws Throwable {
            // nothing to do
        }

        public @Override void after(Scenario scenario) {
            for (PGTemporaryTestConfig testConfig : testConfigs) {
                if (testConfig != null) {
                    testConfig.after();
                }
            }
            testConfigs.clear();
        }

        public @Override URI newRepositoryURI(String repoName, Platform platform)
                throws URISyntaxException {
            PGTemporaryTestConfig testConfig = PGTestUtil.newTestConfig(repoName);
            testConfig.before();

            URI repoURI = testConfig.getEnvironment().toURI();
            testConfigs.add(testConfig);
            return repoURI;
        }

        public @Override URI buildRootURI(Platform platform) {
            PGTemporaryTestConfig testConfig = testConfigs.get(testConfigs.size() - 1);
            String rootURI = testConfig.getRootURI();
            URI rootUri = URI.create(rootURI);
            return rootUri;
        }
    }
}
