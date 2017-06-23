/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.geogig.web.postgresql.functional;

import org.geogig.web.functional.TestRepoURIBuilderProvider;
import org.locationtech.geogig.cli.test.functional.TestRepoURIBuilder;

/**
 * Step definitions to set the postgres repo uri builder.
 */
public class PGStepDefinitions {
    @cucumber.api.java.Before(order = 1)
    public void before() throws Throwable {
        TestRepoURIBuilder builder = new PGWebTestRepoURIBuilder();
        builder.before();
        TestRepoURIBuilderProvider.setURIBuilder(builder);
    }

    @cucumber.api.java.After(order = 10001)
    public void after() {
        TestRepoURIBuilderProvider.getURIBuilder().after();
        TestRepoURIBuilderProvider.setURIBuilder(null);
    }
}
