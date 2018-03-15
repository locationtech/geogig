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
import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.RepositoryResolverTestUtil;
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

    @Before
    @After
    public void resetDisabledResolvers() {
        // clear any disabled resolvers, so tests have expected resolvers available
        RepositoryResolverTestUtil.clearDisabledResolverList();
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

    private void verifyTestResolver(final boolean isAvailable, final URI uri) {
        if (isAvailable) {
            // assert Resolver is available for "test" scheme
            assertTrue("TestResolver should be available",
                    RepositoryResolver.resolverAvailableForURIScheme("test"));
            // assert Resolver can be looked up for URI
            assertNotNull("TestResolver should be available", RepositoryResolver.lookup(uri));
        } else {
            // assert Resolver is not available for "test" scheme
            assertFalse("TestResolver should not be available",
                    RepositoryResolver.resolverAvailableForURIScheme("test"));
            // assert Resolver can not be looked up for URI
            try {
                RepositoryResolver.lookup(uri);
                fail("TestResolver should not be available");
            } catch (IllegalArgumentException iae) {
                // expected
            } catch (Throwable t) {
                // unexpected
                fail("Unexpected error: " + t.getMessage());
            }
        }
    }

    private void verifyFileRepositoryResolver(final boolean isAvailable, final URI uri) {
        if (isAvailable) {
            // assert Resolver is available for "test" scheme
            assertTrue("FileRepositoryResolver should be available",
                    RepositoryResolver.resolverAvailableForURIScheme("file"));
            // assert Resolver can be looked up for URI
            assertNotNull("FileRepositoryResolver should be available",
                    RepositoryResolver.lookup(uri));
        } else {
            // assert Resolver is not available for "test" scheme
            assertFalse("FileRepositoryResolver should not be available",
                    RepositoryResolver.resolverAvailableForURIScheme("file"));
            // assert Resolver can not be looked up for URI
            try {
                RepositoryResolver.lookup(uri);
                fail("FileRepositoryResolver should not be available");
            } catch (IllegalArgumentException iae) {
                // expected
            } catch (Throwable t) {
                // unexpected
                fail("Unexpected error: " + t.getMessage());
            }
        }
    }

    @Test
    public void testCanHandleAndLookupWithDisabledResolvers() throws URISyntaxException {
        // test URIs for lookup
        final URI testUri = new URI("test://someRepo");
        final URI fileUri = new URI("file:/someFileRepo");

        // by default, both the TestResolver and the FileRepositoryResolver should be available
        // during tests.
        verifyTestResolver(true, testUri);
        verifyFileRepositoryResolver(true, fileUri);

        // disable the TestResolver
        RepositoryResolverTestUtil.setDisabledResolvers(Arrays.asList(
                "org.locationtech.geogig.repository.impl.RepositoryResolverTest$TestResolver"));

        // verify TestResolver is not available and can't be looked up
        verifyTestResolver(false, testUri);
        verifyFileRepositoryResolver(true, fileUri);

        // now disable the FileRepositoryResolver
        RepositoryResolverTestUtil.setDisabledResolvers(Arrays.asList(
                "org.locationtech.geogig.repository.impl.FileRepositoryResolver"));

        // Verify the TestResolver is available again
        verifyTestResolver(true, testUri);
        verifyFileRepositoryResolver(false, fileUri);

        // now disable both TestResolver and FileRepositoryResolver
        RepositoryResolverTestUtil.setDisabledResolvers(Arrays.asList(
                "org.locationtech.geogig.repository.impl.RepositoryResolverTest$TestResolver",
                "org.locationtech.geogig.repository.impl.FileRepositoryResolver"));

        // Verify neither the TestResolver nor the FileRepositoryResolver are available
        verifyTestResolver(false, testUri);
        verifyFileRepositoryResolver(false, fileUri);

    }
}
