package org.locationtech.geogig.repository;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Test;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.storage.ConfigDatabase;

public class RepositoryInitializerTest {

    public static class TestInitializer extends RepositoryInitializer {

        @Override
        public boolean canHandle(URI repoURI) {
            String scheme = repoURI.getScheme();
            return scheme != null && scheme.equals("test");
        }

        @Override
        public boolean repoExists(URI repoURI) {
            return false;
        }

        @Override
        public void initialize(URI repoURI, Context repoContext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConfigDatabase getConfigDatabase(URI repoURI, Context repoContext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Repository open(URI repositoryLocation) {
            throw new UnsupportedOperationException();
        }

    }

    @Test
    public void testLookup() throws URISyntaxException {
        URI uri = new URI("test://somerepo");
        RepositoryInitializer initializer = RepositoryInitializer.lookup(uri);
        assertNotNull(initializer);
        assertTrue(initializer instanceof TestInitializer);
    }

}
