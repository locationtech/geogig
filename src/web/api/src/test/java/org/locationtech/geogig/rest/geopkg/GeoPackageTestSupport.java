/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geopkg;

import static com.google.common.base.Preconditions.*;
import static org.locationtech.geogig.web.api.TestData.line1;
import static org.locationtech.geogig.web.api.TestData.line2;
import static org.locationtech.geogig.web.api.TestData.line3;
import static org.locationtech.geogig.web.api.TestData.linesType;
import static org.locationtech.geogig.web.api.TestData.point1;
import static org.locationtech.geogig.web.api.TestData.point2;
import static org.locationtech.geogig.web.api.TestData.point3;
import static org.locationtech.geogig.web.api.TestData.pointsType;
import static org.locationtech.geogig.web.api.TestData.poly1;
import static org.locationtech.geogig.web.api.TestData.poly2;
import static org.locationtech.geogig.web.api.TestData.poly3;
import static org.locationtech.geogig.web.api.TestData.polysType;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;

import org.geotools.data.DataStore;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.locationtech.geogig.web.api.TestData;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * @see TestData
 */
public class GeoPackageTestSupport {

    private File tmpFolder;

    public GeoPackageTestSupport() {
        this(new File(System.getProperty("java.io.tmpdir")));
    }

    public GeoPackageTestSupport(final File tmpFolder) {
        checkNotNull(tmpFolder);
        checkArgument(tmpFolder.exists() && tmpFolder.isDirectory() && tmpFolder.canWrite());
        this.tmpFolder = tmpFolder;
    }

    private File newFile() throws IOException {
        File dbFile = File.createTempFile("geogig_geopackage_test", ".gpkg", tmpFolder);
        dbFile.deleteOnExit();
        return dbFile;
    }

    public File createEmptyDatabase() throws Exception {
        File file = newFile();
        GeoPackage geoPackage = new GeoPackage(file);
        try {
            geoPackage.init();
        } finally {
            geoPackage.close();
        }
        return file;
    }

    public DataStore createDataStore(File dbFile) {

        GeoPkgDataStoreFactory f = new GeoPkgDataStoreFactory();

        DataStore dataStore;
        final HashMap<String, Serializable> params = new HashMap<>(3);
        params.put(GeoPkgDataStoreFactory.DBTYPE.key, "geopkg");
        params.put(GeoPkgDataStoreFactory.DATABASE.key, dbFile.getAbsolutePath());
        params.put(GeoPkgDataStoreFactory.USER.key, "user");
        try {
            dataStore = f.createDataStore(params);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create GeoPkgDataStore", ioe);
        }
        return dataStore;
    }

    public File createDefaultTestData() throws Exception {

        File file = createEmptyDatabase();

        MemoryDataStore memStore = TestData.newMemoryDataStore();
        memStore.addFeatures(ImmutableList.of(point1, point2, point3));
        memStore.addFeatures(ImmutableList.of(line1, line2, line3));
        memStore.addFeatures(ImmutableList.of(poly1, poly2, poly3));

        DataStore gpkgStore = createDataStore(file);
        try {
            export(memStore.getFeatureSource(pointsType.getName().getLocalPart()), gpkgStore);
            export(memStore.getFeatureSource(linesType.getName().getLocalPart()), gpkgStore);
            export(memStore.getFeatureSource(polysType.getName().getLocalPart()), gpkgStore);
        } finally {
            gpkgStore.dispose();
        }
        return file;
    }

    private void export(SimpleFeatureSource source, DataStore gpkg) throws IOException {

        Transaction gttx = new DefaultTransaction();
        try {
            gpkg.createSchema(source.getSchema());
            SimpleFeatureStore store;
            store = (SimpleFeatureStore) gpkg.getFeatureSource(source.getName().getLocalPart());
            Preconditions.checkState(store.getQueryCapabilities().isUseProvidedFIDSupported());
            store.setTransaction(gttx);
            SimpleFeatureCollection features = source.getFeatures();
            
            store.addFeatures(features);
            gttx.commit();
        } finally {
            gttx.close();
        }
    }

}
