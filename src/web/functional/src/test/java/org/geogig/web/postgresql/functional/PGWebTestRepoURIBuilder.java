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

import java.net.URI;
import java.net.URISyntaxException;

import org.locationtech.geogig.cli.test.functional.TestRepoURIBuilder;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;

/**
 * URI builder for Postgres-backed repositories for web functional tests.
 */
public final class PGWebTestRepoURIBuilder extends TestRepoURIBuilder {

    private PGTemporaryTestConfig delegate;

    @Override
    public void before() throws Throwable {
        delegate = new PGTemporaryTestConfig("unused");
        delegate.before();
    }

    @Override
    public void after() {
        delegate.after();
    }

    @Override
    public URI newRepositoryURI(String name, Platform platform) throws URISyntaxException {
        String repoURI = delegate.newRepoURI(name);
        URI repoUri = URI.create(repoURI);
        return repoUri;
    }

    @Override
    public URI buildRootURI(Platform platform) {
        String rootURI = delegate.getRootURI();
        URI rootUri = URI.create(rootURI);
        return rootUri;
    }
}
