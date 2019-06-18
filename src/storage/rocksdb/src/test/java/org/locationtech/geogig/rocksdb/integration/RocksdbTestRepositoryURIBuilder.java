/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb.integration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.function.Function;

import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import lombok.NonNull;

public class RocksdbTestRepositoryURIBuilder extends ExternalResource
        implements Function<String, URI> {

    private TemporaryFolder tmp = new TemporaryFolder();

    public @Override void before() throws IOException {
        tmp.create();
    }

    public @Override void after() {
        tmp.delete();
    }

    public @Override URI apply(@NonNull String repositoryName) {
        return new File(tmp.getRoot(), repositoryName).toURI();
    }
}
