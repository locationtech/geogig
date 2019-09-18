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
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryFinder;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.RepositoryResolverTestUtil;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.memory.MemoryRepositoryResolver;

import lombok.NonNull;

public class RepositoryResolverTest {

    public static class TestResolver implements RepositoryResolver {

        public @Override boolean canHandle(URI repoURI) {
            String scheme = repoURI.getScheme();
            return canHandleURIScheme(scheme);
        }

        public @Override URI getRootURI(@NonNull URI repoURI) {
            throw new UnsupportedOperationException();
        }

        public @Override boolean canHandleURIScheme(String scheme) {
            return "test".equals(scheme);
        }

        public @Override boolean repoExists(URI repoURI) {
            return false;
        }

        public @Override String getName(URI repoURI) {
            return "test";
        }

        public @Override void initialize(URI repoURI) {
            throw new UnsupportedOperationException();
        }

        public @Override ConfigDatabase resolveConfigDatabase(URI repoURI, Context repoContext,
                boolean globalOnly) {
            throw new UnsupportedOperationException();
        }

        public @Override Repository open(@NonNull URI repositoryURI) {
            throw new UnsupportedOperationException();
        }

        public @Override Repository open(URI repositoryLocation, Hints hints) {
            throw new UnsupportedOperationException();
        }

        public @Override boolean delete(URI repositoryLocation) throws Exception {
            throw new UnsupportedOperationException();
        }

        public @Override URI buildRepoURI(URI rootRepoURI, String repoId) {
            throw new UnsupportedOperationException();
        }

        public @Override List<String> listRepoNamesUnderRootURI(URI rootRepoURI) {
            throw new UnsupportedOperationException();
        }

        public @Override ObjectDatabase resolveObjectDatabase(@NonNull URI repoURI, Hints hints) {
            throw new UnsupportedOperationException();
        }

        public @Override IndexDatabase resolveIndexDatabase(@NonNull URI repoURI, Hints hints) {
            throw new UnsupportedOperationException();
        }

        public @Override RefDatabase resolveRefDatabase(@NonNull URI repoURI, Hints hints) {
            throw new UnsupportedOperationException();
        }

        public @Override ConflictsDatabase resolveConflictsDatabase(@NonNull URI repoURI,
                Hints hints) {
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
        RepositoryResolver initializer = RepositoryFinder.INSTANCE.lookup(uri);
        assertNotNull(initializer);
        assertTrue(initializer instanceof TestResolver);
    }

    @Test
    public void testCanHandleURIScheme() {
        boolean canHandleScheme = RepositoryFinder.INSTANCE.resolverAvailableForURIScheme("test");
        assertTrue(canHandleScheme);
        canHandleScheme = RepositoryFinder.INSTANCE.resolverAvailableForURIScheme("unknown");
        assertFalse(canHandleScheme);
    }

    private void verifyTestResolver(final boolean isAvailable, final URI uri) {
        if (isAvailable) {
            // assert Resolver is available for "test" scheme
            assertTrue("TestResolver should be available",
                    RepositoryFinder.INSTANCE.resolverAvailableForURIScheme("test"));
            // assert Resolver can be looked up for URI
            assertNotNull("TestResolver should be available",
                    RepositoryFinder.INSTANCE.lookup(uri));
        } else {
            // assert Resolver is not available for "test" scheme
            assertFalse("TestResolver should not be available",
                    RepositoryFinder.INSTANCE.resolverAvailableForURIScheme("test"));
            // assert Resolver can not be looked up for URI
            try {
                RepositoryFinder.INSTANCE.lookup(uri);
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
            assertTrue("MemoryRepositoryResolver should be available",
                    RepositoryFinder.INSTANCE.resolverAvailableForURIScheme("memory"));
            // assert Resolver can be looked up for URI
            assertNotNull("MemoryRepositoryResolver should be available",
                    RepositoryFinder.INSTANCE.lookup(uri));
        } else {
            // assert Resolver is not available for "test" scheme
            assertFalse("MemoryRepositoryResolver should not be available",
                    RepositoryFinder.INSTANCE.resolverAvailableForURIScheme("memory"));
            // assert Resolver can not be looked up for URI
            try {
                RepositoryFinder.INSTANCE.lookup(uri);
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
        final URI fileUri = new URI("memory://someFileRepo");

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
        RepositoryResolverTestUtil
                .setDisabledResolvers(Arrays.asList(MemoryRepositoryResolver.class.getName()));

        // Verify the TestResolver is available again
        verifyTestResolver(true, testUri);
        verifyFileRepositoryResolver(false, fileUri);

        // now disable both TestResolver and FileRepositoryResolver
        RepositoryResolverTestUtil.setDisabledResolvers(Arrays.asList(
                "org.locationtech.geogig.repository.impl.RepositoryResolverTest$TestResolver",
                MemoryRepositoryResolver.class.getName()));

        // Verify neither the TestResolver nor the FileRepositoryResolver are available
        verifyTestResolver(false, testUri);
        verifyFileRepositoryResolver(false, fileUri);

    }
}
