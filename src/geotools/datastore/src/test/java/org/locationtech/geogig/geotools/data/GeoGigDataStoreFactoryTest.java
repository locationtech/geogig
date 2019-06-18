/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data;

import static org.locationtech.geogig.geotools.data.GeoGigDataStoreFactory.REPOSITORY;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.junit.Test;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;

public class GeoGigDataStoreFactoryTest extends RepositoryTestCase {

    private GeoGigDataStoreFactory factory;

    private URI repoURI;

    protected @Override void setUpInternal() throws Exception {
        repoURI = testRepository.getRepoURI();
        factory = new GeoGigDataStoreFactory();
    }

    @Test
    public void testFactorySpi() {
        Iterator<GeoGigDataStoreFactory> filtered = Iterators
                .filter(DataStoreFinder.getAvailableDataStores(), GeoGigDataStoreFactory.class);
        assertTrue(filtered.hasNext());
        assertTrue(filtered.next() instanceof GeoGigDataStoreFactory);
    }

    @Test
    public void testDataStoreFinder() throws Exception {
        Map<String, ? extends Serializable> params;
        DataStore dataStore;

        params = Collections.emptyMap();
        dataStore = DataStoreFinder.getDataStore(params);
        assertNull(dataStore);

        params = ImmutableMap.of(REPOSITORY.key, repoURI);
        dataStore = DataStoreFinder.getDataStore(params);
        assertNotNull(dataStore);
        assertTrue(dataStore instanceof GeoGigDataStore);
    }

    @Test
    public void testCanProcess() {
        Map<String, Serializable> params = Collections.emptyMap();
        assertFalse(factory.canProcess(params));

        assertTrue(factory.canProcess(ImmutableMap.of(REPOSITORY.key, repoURI)));
        assertTrue(factory.canProcess(ImmutableMap.of(REPOSITORY.key, repoURI.toString())));

        assertFalse(
                factory.canProcess(ImmutableMap.of(REPOSITORY.key, "invalidschema://somerepo")));
    }

    @Test
    public void testCreateDataStoreNonExistentRepository() throws IOException {
        Map<String, Serializable> params;
        params = ImmutableMap.of(REPOSITORY.key, repoURI.resolve("nonexistent"));
        try {
            factory.createDataStore(params);
            fail("Expectd IOE on non existing repository");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not a geogig repository"));
        }
    }

    @Test
    public void testCreateDataStore() throws IOException {
        Map<String, Serializable> params;

        params = ImmutableMap.of(REPOSITORY.key, repoURI);

        GeoGigDataStore store = factory.createDataStore(params);
        assertNotNull(store);
        store.dispose();
    }

    @Test
    public void testCreateNewDataStore() throws IOException {
        Map<String, Serializable> params;
        params = ImmutableMap.of(REPOSITORY.key, repoURI.resolve("newdatastore"));

        GeoGigDataStore store = factory.createNewDataStore(params);
        assertNotNull(store);
        store.dispose();
    }
}
