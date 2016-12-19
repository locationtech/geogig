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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.junit.Test;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.locationtech.geogig.test.integration.TestContextBuilder;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;

public class GeoGigDataStoreFactoryTest extends RepositoryTestCase {

    private GeoGigDataStoreFactory factory;

    private File repoDirectory;

    @Override
    protected Context createInjector() {
        Platform platform = createPlatform();
        GlobalContextBuilder.builder(new TestContextBuilder(platform));
        URI uri = repositoryDirectory.getAbsoluteFile().toURI();
        Hints hints = new Hints().uri(uri).platform(platform);
        return GlobalContextBuilder.builder().build(hints);
    }

    @Override
    protected void setUpInternal() throws Exception {
        factory = new GeoGigDataStoreFactory();
        repoDirectory = super.repositoryDirectory;
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
        final File workingDir = repoDirectory;

        Map<String, Serializable> params = ImmutableMap.of();
        assertFalse(factory.canProcess(params));
        params = ImmutableMap.of(REPOSITORY.key,
                (Serializable) (workingDir.getName() + "/testCanProcess"));
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
        params = ImmutableMap.of(REPOSITORY.key,
                (Serializable) (root.getName() + "/testCreateDataStoreNonExistentDirectory"));
        try {
            factory.createDataStore(params);
            fail("Expectd IOE on non existing directory");
        } catch (IOException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testCreateDataStoreNotARepositoryDir() throws IOException {
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
        store.dispose();
    }

    @Test
    public void testCreateNewDataStore() throws IOException {
        Map<String, Serializable> params;

        String newRepoDir = repositoryTempFolder.newFolder("datastore").getAbsolutePath();

        params = ImmutableMap.of(REPOSITORY.key, (Serializable) newRepoDir);

        GeoGigDataStore store = factory.createNewDataStore(params);
        assertNotNull(store);
        store.dispose();
    }
}
