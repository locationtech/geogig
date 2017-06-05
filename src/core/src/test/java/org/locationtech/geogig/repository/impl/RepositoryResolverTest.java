/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.junit.Test;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.storage.ConfigDatabase;

public class RepositoryResolverTest {

    public static class TestResolver extends RepositoryResolver {

        @Override
        public boolean canHandle(URI repoURI) {
            String scheme = repoURI.getScheme();
            return canHandleURIScheme(scheme);
        }

        @Override
        public boolean canHandleURIScheme(String scheme) {
            return "test".equals(scheme);
        }

        @Override
        public boolean repoExists(URI repoURI) {
            return false;
        }

        @Override
        public String getName(URI repoURI) {
            return "test";
        }

        @Override
        public void initialize(URI repoURI, Context repoContext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConfigDatabase getConfigDatabase(URI repoURI, Context repoContext,
                boolean globalOnly) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Repository open(URI repositoryLocation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean delete(URI repositoryLocation) throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI buildRepoURI(URI rootRepoURI, String repoId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> listRepoNamesUnderRootURI(URI rootRepoURI) {
            throw new UnsupportedOperationException();
        }

    }

    @Test
    public void testLookup() throws URISyntaxException {
        URI uri = new URI("test://somerepo");
        RepositoryResolver initializer = RepositoryResolver.lookup(uri);
        assertNotNull(initializer);
        assertTrue(initializer instanceof TestResolver);
    }

    @Test
    public void testCanHandleURIScheme() {
        boolean canHandleScheme = RepositoryResolver.resolverAvailableForURIScheme("test");
        assertTrue(canHandleScheme);
        canHandleScheme = RepositoryResolver.resolverAvailableForURIScheme("unknown");
        assertFalse(canHandleScheme);
    }

}
