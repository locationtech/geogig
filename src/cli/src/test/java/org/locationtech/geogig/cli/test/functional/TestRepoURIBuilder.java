/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.test.functional;

import java.io.File;
import java.net.URI;

import org.locationtech.geogig.repository.Platform;

/**
 * A repository URI builder for functional tests to create repos of a specific storage backend kind.
 * <p>
 * The {@link #createDefault() default} URI builder creates {@code file://} URI's and uses whatever
 * the default file storage backend is configured by the geogig plugin system (currently Rocksdb).
 * <p>
 * A concrete subclass must implement {@link #newRepositoryURI} to return an appropriate URI for the
 * kind of storage backend it intends to run the CLI functional tests against, and to set up any
 * temporary resource needed to run a single test.
 */
public abstract class TestRepoURIBuilder {

    /**
     * Called before a test suite is run, may be used to set up temporary resources needed by the
     * storage backend, like connection pools, temporary folders, etc.
     */
    public abstract void before() throws Throwable;

    /**
     * Called once the test case finished to release any potential temporary resource created by
     * this URI builder.
     */
    public abstract void after();

    /**
     * Creates a repository URI named {@code name} for the specific kind of storage backend the CLI
     * functional tests shall be executed against.
     */
    public abstract URI newRepositoryURI(String name, Platform platform) throws Exception;

    /**
     * Gets the root URI of a given platform.
     */
    public abstract URI buildRootURI(Platform platform);

    public static TestRepoURIBuilder createDefault() {

        return new DefaultTestRepoURIBuilder();
    }

    private static final class DefaultTestRepoURIBuilder extends TestRepoURIBuilder {
        @Override
        public void before() {

        }

        @Override
        public void after() {

        }

        @Override
        public URI newRepositoryURI(String name, Platform platform) {
            final File dir = new File(platform.pwd(), name);
            // dir.mkdir();
            // platform.setWorkingDir(dir);
            return dir.toURI();
        }

        @Override
        public URI buildRootURI(Platform platform) {
            return platform.pwd().toURI();
        }
    }

}
