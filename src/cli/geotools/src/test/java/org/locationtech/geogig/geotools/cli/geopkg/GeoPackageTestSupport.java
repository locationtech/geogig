/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geopkg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.lines1;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.lines2;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.lines3;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.linesType;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points1;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points2;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points3;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.pointsType;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.geotools.data.DataStore;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.factory.Hints;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.locationtech.geogig.cli.test.functional.TestFeatures;
import org.locationtech.geogig.geotools.geopkg.GeopkgGeogigMetadata;
import org.locationtech.geogig.test.MemoryDataStoreWithProvidedFIDSupport;
import org.locationtech.geogig.test.TestData;
import org.opengis.feature.Feature;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

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

    public File newFile() throws IOException {
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

    public void exportToFile(File file, String tableName, ImmutableList<Feature> features)
            throws Exception {
        MemoryDataStore memStore = new MemoryDataStoreWithProvidedFIDSupport();
        memStore.addFeatures(features);
        DataStore gpkgStore = createDataStore(file);
        try {
            export(memStore.getFeatureSource(tableName), gpkgStore);
        } finally {
            gpkgStore.dispose();
        }
    }

    public File createDefaultTestData() throws Exception {

        File file = createEmptyDatabase();

        MemoryDataStore memStore = new MemoryDataStoreWithProvidedFIDSupport();
        TestFeatures.setupFeatures();
        memStore.addFeatures(ImmutableList.of(points1, points2, points3));
        memStore.addFeatures(ImmutableList.of(lines1, lines2, lines3));

        DataStore gpkgStore = createDataStore(file);
        try {
            export(memStore.getFeatureSource(pointsType.getName().getLocalPart()), gpkgStore);
            export(memStore.getFeatureSource(linesType.getName().getLocalPart()), gpkgStore);
        } finally {
            gpkgStore.dispose();
        }
        return file;
    }

    static AtomicLong nextId = new AtomicLong(37);

    public void export(SimpleFeatureSource source, DataStore gpkg)
            throws IOException, SQLException {

        JDBCDataStore gpkgStore = (JDBCDataStore) gpkg;
        try (Connection cx = gpkgStore.getConnection(Transaction.AUTO_COMMIT);
                GeopkgGeogigMetadata metadata = new GeopkgGeogigMetadata(cx)) {
            Transaction gttx = new DefaultTransaction();
            try {
                gpkg.createSchema(source.getSchema());
                SimpleFeatureStore store;
                store = (SimpleFeatureStore) gpkg.getFeatureSource(source.getName().getLocalPart());
                Preconditions.checkState(store.getQueryCapabilities().isUseProvidedFIDSupported());
                store.setTransaction(gttx);
                SimpleFeatureCollection features = source.getFeatures();

                SimpleFeatureIterator iter = features.features();
                SimpleFeature original, updated;
                SimpleFeatureType featureType = null;
                List<SimpleFeature> updatedFeatures = new LinkedList<SimpleFeature>();
                ConcurrentMap<String, String> mappings = new ConcurrentHashMap<String, String>();
                while (iter.hasNext()) {
                    original = iter.next();
                    featureType = original.getFeatureType();
                    updated = transformFeatureId(original);
                    updatedFeatures.add(updated);
                    mappings.put(updated.getID(), original.getID());
                }
                ListFeatureCollection transformedFeatures = new ListFeatureCollection(featureType,
                        updatedFeatures);
                metadata.createFidMappingTable(source.getName().getLocalPart(), mappings);

                store.addFeatures(transformedFeatures);
                gttx.commit();
            } finally {
                gttx.close();
            }
        }
    }

    private SimpleFeature transformFeatureId(SimpleFeature feature) {
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(feature.getFeatureType());
        for (Property property : feature.getProperties()) {
            if (property instanceof GeometryAttribute) {
                builder.set(feature.getFeatureType().getGeometryDescriptor().getName(),
                        property.getValue());
            } else {
                builder.set(property.getName().getLocalPart(), property.getValue());
            }
        }
        Map<Object, Object> userData = feature.getUserData();
        for (Entry<Object, Object> entry : userData.entrySet()) {
            builder.featureUserData(entry.getKey(), entry.getValue());
        }
        long fidValue = nextId.incrementAndGet();
        builder.featureUserData(Hints.USE_PROVIDED_FID, true);
        builder.featureUserData(Hints.PROVIDED_FID, Long.valueOf(fidValue));
        SimpleFeature modifiedFeature = builder.buildFeature(Long.toString(fidValue));
        return modifiedFeature;
    }

}
