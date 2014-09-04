/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data;

import static org.locationtech.geogig.geotools.data.GeoGigDataStoreFactory.CREATE;
import static org.locationtech.geogig.geotools.data.GeoGigDataStoreFactory.REPOSITORY;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
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

    private File repoDirectory;

    @Override
    protected void setUpInternal() throws Exception {
        factory = new GeoGigDataStoreFactory();
        repoDirectory = geogig.getPlatform().pwd();
    }

    @Test
    public void testFactorySpi() {
        Iterator<GeoGigDataStoreFactory> filtered = Iterators.filter(
                DataStoreFinder.getAvailableDataStores(), GeoGigDataStoreFactory.class);
        assertTrue(filtered.hasNext());
        assertTrue(filtered.next() instanceof GeoGigDataStoreFactory);
    }

    @Test
    public void testDataStoreFinder() throws Exception {
        Map<String, ? extends Serializable> params;
        DataStore dataStore;

        params = ImmutableMap.of();
        dataStore = DataStoreFinder.getDataStore(params);
        assertNull(dataStore);

        params = ImmutableMap.of(REPOSITORY.key, repoDirectory);
        dataStore = DataStoreFinder.getDataStore(params);
        assertNotNull(dataStore);
        assertTrue(dataStore instanceof GeoGigDataStore);
    }

    @Test
    public void testCanProcess() {
        final File workingDir = repositoryTempFolder.getRoot();

        Map<String, Serializable> params = ImmutableMap.of();
        assertFalse(factory.canProcess(params));
        params = ImmutableMap.of(REPOSITORY.key,
                (Serializable) (workingDir.getName() + "/shouldntExist"));
        assertFalse(factory.canProcess(params));

        params = ImmutableMap.of(REPOSITORY.key, (Serializable) workingDir.getAbsolutePath());
        assertTrue(factory.canProcess(params));

        params = ImmutableMap.of(REPOSITORY.key, (Serializable) repoDirectory.getPath());
        assertTrue(factory.canProcess(params));

        params = ImmutableMap.of(REPOSITORY.key, (Serializable) repoDirectory.getAbsolutePath());
        assertTrue(factory.canProcess(params));

        params = ImmutableMap.of(REPOSITORY.key, (Serializable) repoDirectory);
        assertTrue(factory.canProcess(params));
    }

    @Test
    public void testCreateDataStoreNonExistentDirectory() {
        Map<String, Serializable> params;

        File root = repositoryTempFolder.getRoot();
        params = ImmutableMap
                .of(REPOSITORY.key, (Serializable) (root.getName() + "/shouldntExist"));
        try {
            factory.createDataStore(params);
            fail("Expectd IOE on non existing directory");
        } catch (IOException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testCreateDataStoreNotARepositoryDir() {
        Map<String, Serializable> params;

        File f = repositoryTempFolder.newFolder("someDir");
        params = ImmutableMap.of(REPOSITORY.key, (Serializable) f);
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

        params = ImmutableMap.of(REPOSITORY.key, (Serializable) repoDirectory.getAbsolutePath());

        GeoGigDataStore store = factory.createDataStore(params);
        assertNotNull(store);

    }

    @Test
    public void testCreateNewDataStore() throws IOException {
        Map<String, Serializable> params;

        String newRepoDir = repositoryTempFolder.newFolder("datastore").getAbsolutePath();

        params = ImmutableMap.of(REPOSITORY.key, (Serializable) newRepoDir);

        GeoGigDataStore store = factory.createNewDataStore(params);
        assertNotNull(store);

    }

    @Test
    public void testCreateOption() throws Exception {
        String newRepoDir = repositoryTempFolder.newFolder("datastore").getAbsolutePath();

        Map<String, Serializable> params = ImmutableMap.of(REPOSITORY.key,
                (Serializable) newRepoDir, CREATE.key, true);

        assertTrue(factory.canProcess(params));
        GeoGigDataStore store = factory.createDataStore(params);
        assertNotNull(store);
    }

    @Test
    public void testCreateOptionDirectoryExists() throws Exception {
        File newRepoDir = repositoryTempFolder.newFolder("datastore");

        Map<String, Serializable> params = ImmutableMap.of(REPOSITORY.key,
                (Serializable) newRepoDir, CREATE.key, true);
        assertTrue(factory.canProcess(params));
        GeoGigDataStore store = factory.createDataStore(params);
        assertNotNull(store);
    }
}
