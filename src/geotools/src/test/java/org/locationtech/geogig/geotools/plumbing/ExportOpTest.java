/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Juan Marin (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import java.io.IOException;
import java.util.*;

import javax.annotation.Nullable;

import com.google.common.collect.Sets;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.FactoryFinder;
import org.geotools.factory.GeoTools;
import org.geotools.factory.Hints;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.junit.Test;
import org.locationtech.geogig.api.*;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.cli.porcelain.Commit;
import org.locationtech.geogig.geotools.cli.porcelain.PGExport;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException.StatusCode;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.identity.Identifier;
import sun.java2d.pipe.SpanShapeRenderer;

public class ExportOpTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Test
    public void testExportFromWorkingTree() throws Exception {
        Feature[] points = new Feature[] { points1, points2, points3 };
        for (Feature feature : points) {
            insert(feature);
        }
        MemoryDataStore dataStore = new MemoryDataStore(pointsType);
        final String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName).call();
        featureSource = dataStore.getFeatureSource(typeName);
        featureStore = (SimpleFeatureStore) featureSource;
        SimpleFeatureCollection featureCollection = featureStore.getFeatures();
        assertEquals(featureCollection.size(), points.length);
        SimpleFeatureIterator features = featureCollection.features();
        assertTrue(collectionsAreEqual(features, points));
    }

    @Test
    public void testExportFromHEAD() throws Exception {
        Feature[] points = new Feature[] { points1, points2, points3 };
        for (Feature feature : points) {
            insert(feature);
        }
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setAll(true).call();
        MemoryDataStore dataStore = new MemoryDataStore(pointsType);
        final String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath("HEAD:" + pointsName)
                .call();
        featureSource = dataStore.getFeatureSource(typeName);
        featureStore = (SimpleFeatureStore) featureSource;
        SimpleFeatureCollection featureCollection = featureStore.getFeatures();
        assertEquals(featureCollection.size(), points.length);
        SimpleFeatureIterator features = featureCollection.features();
        assertTrue(collectionsAreEqual(features, points));
    }

    @Test
    public void testExportingUsingFunction() throws Exception {
        // Testing export of points feature type into a simplified feature type that
        // does not contain the integer attribute.
        String simplifiedPointsName = "simplifiedPoints";
        String simplifiedPointsTypeSpec = "sp:String,pp:Point:srid=4326";
        SimpleFeatureType simplifiedPointsType = DataUtilities.createType(pointsNs,
                simplifiedPointsName, simplifiedPointsTypeSpec);

        Feature simplifiedPoints1 = feature(simplifiedPointsType,
                ((SimpleFeature) points1).getID(), ((SimpleFeature) points1).getAttribute(0),
                ((SimpleFeature) points1).getAttribute(2));
        Feature simplifiedPoints2 = feature(simplifiedPointsType,
                ((SimpleFeature) points2).getID(), ((SimpleFeature) points2).getAttribute(0),
                ((SimpleFeature) points2).getAttribute(2));
        Feature simplifiedPoints3 = feature(simplifiedPointsType,
                ((SimpleFeature) points3).getID(), ((SimpleFeature) points3).getAttribute(0),
                ((SimpleFeature) points3).getAttribute(2));

        Feature[] simplifiedPoints = new Feature[] { simplifiedPoints1, simplifiedPoints2,
                simplifiedPoints3 };

        final SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(simplifiedPointsType);
        Function<Feature, Optional<Feature>> function = new Function<Feature, Optional<Feature>>() {
            @Override
            @Nullable
            public Optional<Feature> apply(@Nullable Feature feature) {
                SimpleFeature simpleFeature = (SimpleFeature) feature;
                featureBuilder.add(simpleFeature.getAttribute(0));
                featureBuilder.add(simpleFeature.getAttribute(2));
                return Optional.of((Feature) featureBuilder.buildFeature(null));
            }
        };

        Feature[] points = new Feature[] { points1, points2, points3 };
        for (Feature feature : points) {
            insert(feature);
        }
        MemoryDataStore dataStore = new MemoryDataStore(simplifiedPointsType);
        final String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName)
                .setFeatureTypeConversionFunction(function).call();
        featureSource = dataStore.getFeatureSource(typeName);
        featureStore = (SimpleFeatureStore) featureSource;
        SimpleFeatureCollection featureCollection = featureStore.getFeatures();
        assertEquals(featureCollection.size(), points.length);
        SimpleFeatureIterator features = featureCollection.features();
        assertTrue(collectionsAreEqual(features, simplifiedPoints));

        // check for exceptions when using a function that returns features with a wrong featuretype
        try {
            String wrongFeaturesName = "wrongFeatures";
            String wrongFeaturesTypeSpec = "sp:String";
            SimpleFeatureType wrongFeaturesType = DataUtilities.createType(pointsNs,
                    wrongFeaturesName, wrongFeaturesTypeSpec);
            final SimpleFeatureBuilder wrongFeatureBuilder = new SimpleFeatureBuilder(
                    wrongFeaturesType);
            Function<Feature, Optional<Feature>> wrongFunction = new Function<Feature, Optional<Feature>>() {
                @Override
                @Nullable
                public Optional<Feature> apply(@Nullable Feature feature) {
                    SimpleFeature simpleFeature = (SimpleFeature) feature;
                    wrongFeatureBuilder.add(simpleFeature.getAttribute(0));
                    return Optional.of((Feature) wrongFeatureBuilder.buildFeature(null));
                }
            };
            geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName)
                    .setFeatureTypeConversionFunction(wrongFunction).call();
            fail();
        } catch (GeoToolsOpException e) {
            assertEquals(e.statusCode, StatusCode.UNABLE_TO_ADD);
        }

    }

    private boolean collectionsAreEqual(SimpleFeatureIterator features, Feature[] points) {
        // features are not iterated in the same order as the original set, so
        // we just do pairwise comparison to check that all the original features
        // are represented in the exported feature store
        while (features.hasNext()) {
            boolean found = true;
            List<Object> attributesExported = features.next().getAttributes();
            for (int i = 0; i < points.length; i++) {
                found = true;
                List<Object> attributesOriginal = ((SimpleFeature) points[i]).getAttributes();
                for (int j = 0; j < attributesExported.size(); j++) {
                    Object attributeExported = attributesExported.get(j);
                    Object attributeOriginal = attributesOriginal.get(j);
                    if (!attributeOriginal.equals(attributeExported)) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void testExportFromWrongFeatureType() throws Exception {
        MemoryDataStore dataStore = new MemoryDataStore(pointsType);
        final String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        try {
            geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName).call();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

    }

    @Test
    public void testExportFromTreeWithSeveralFeatureTypesUsingDefaultFeatureType() throws Exception {
        Feature[] points = new Feature[] { points2, points1B, points3 };
        for (Feature feature : points) {
            insert(feature);
        }
        Feature[] expectedPoints = new Feature[] { points2, points3 };
        MemoryDataStore dataStore = new MemoryDataStore(pointsType);
        final String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName)
                .exportDefaultFeatureType().call();
        featureSource = dataStore.getFeatureSource(typeName);
        featureStore = (SimpleFeatureStore) featureSource;
        SimpleFeatureCollection featureCollection = featureStore.getFeatures();
        assertEquals(featureCollection.size(), expectedPoints.length);
        SimpleFeatureIterator features = featureCollection.features();
        assertTrue(collectionsAreEqual(features, expectedPoints));
    }

    @Test
    public void testExportWithAlterUsingDefaultFeatureType() throws Exception {
        Feature[] points = new Feature[] { points2, points1B, points3 };
        for (Feature feature : points) {
            insert(feature);
        }
        MemoryDataStore dataStore = new MemoryDataStore(pointsType);
        final String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName)
                .setAlter(true).call();
        featureSource = dataStore.getFeatureSource(typeName);
        featureStore = (SimpleFeatureStore) featureSource;
        SimpleFeatureCollection featureCollection = featureStore.getFeatures();
        assertEquals(featureCollection.size(), points.length);
        SimpleFeatureIterator features = featureCollection.features();
        assertTrue(collectionsAreEqual(features, points));
    }

    @Test
    public void testExportWithAlterUsingFeatureTypeId() throws Exception {
        Feature[] points = new Feature[] { points2, points1B, points3 };
        for (Feature feature : points) {
            insert(feature);
        }
        MemoryDataStore dataStore = new MemoryDataStore(modifiedPointsType);
        final String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName)
                .setAlter(true)
                .setFilterFeatureTypeId(RevFeatureTypeImpl.build(modifiedPointsType).getId()).call();
        featureSource = dataStore.getFeatureSource(typeName);
        featureStore = (SimpleFeatureStore) featureSource;
        SimpleFeatureCollection featureCollection = featureStore.getFeatures();
        assertEquals(featureCollection.size(), points.length);
        SimpleFeatureIterator features = featureCollection.features();
        while (features.hasNext()) {
            List<Object> attributes = features.next().getAttributes();
            assertEquals(4, attributes.size());
        }

    }

    @Test
    public void testExportFromTreeWithSeveralFeatureTypesUsingFeatureTypeId() throws Exception {
        Feature[] points = new Feature[] { points2, points1B, points3 };
        for (Feature feature : points) {
            insert(feature);
        }
        Feature[] expectedPoints = new Feature[] { points1B };
        MemoryDataStore dataStore = new MemoryDataStore(pointsType);
        final String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName)
                .setFilterFeatureTypeId(RevFeatureTypeImpl.build(modifiedPointsType).getId()).call();
        featureSource = dataStore.getFeatureSource(typeName);
        featureStore = (SimpleFeatureStore) featureSource;
        SimpleFeatureCollection featureCollection = featureStore.getFeatures();
        assertEquals(expectedPoints.length, featureCollection.size());
        SimpleFeatureIterator features = featureCollection.features();
        assertTrue(collectionsAreEqual(features, expectedPoints));
    }

    @Test
    public void testExportFromTreeWithSeveralFeatureTypesUsingNonexistantTypeId() throws Exception {
        Feature[] points = new Feature[] { points2, points1B, points3 };
        for (Feature feature : points) {
            insert(feature);
        }
        MemoryDataStore dataStore = new MemoryDataStore(pointsType);
        final String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        try {
            geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName)
                    .setFilterFeatureTypeId(ObjectId.forString("fake")).call();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("filter feature type"));
        }

    }

    @Test
    public void testExportFromTreeWithSeveralFeatureTypes() throws Exception {
        Feature[] points = new Feature[] { points2, points1B, points3 };
        for (Feature feature : points) {
            insert(feature);
        }
        MemoryDataStore dataStore = new MemoryDataStore(pointsType);
        final String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        try {
            geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName).call();
            fail();
        } catch (GeoToolsOpException e) {
            assertEquals(GeoToolsOpException.StatusCode.MIXED_FEATURE_TYPES, e.statusCode);

        }
    }

    private static MemoryDataStore getChangeStore(SimpleFeatureType type) {
        return new MemoryDataStore(type) {
            @Override
            protected Set getSupportedHints() {
                return Sets.newHashSet(Hints.USE_PROVIDED_FID);
            }
        };
    }

    @Test
    public void exportChangeAdd() throws Exception {
        insertAndAdd(points1);
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("Some message").call();
        MemoryDataStore store = getChangeStore(pointsType);
        final String typeName = store.getTypeNames()[0];
        SimpleFeatureSource featureSource = store.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName).setTransactional(false).call();
        SimpleFeatureCollection collection1 = featureStore.getFeatures();
        assertEquals(1, collection1.size());
        assertTrue(collectionsAreEqual(collection1.features(), new Feature[]{points1}));
        insertAndAdd(points2);
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("Some message").call();
        insertAndAdd(points3);
        RevCommit commit3 = geogig.command(CommitOp.class).setMessage("Some message").call();
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setOldRef(commit1.getTreeId().toString())
                .setNewRef(commit3.getTreeId().toString()).setPath(pointsName).setTransactional(false).call();
        SimpleFeatureCollection collection2 = featureStore.getFeatures();
        assertEquals(3, collection2.size());
        assertTrue(collectionsAreEqual(collection2.features(), new Feature[]{points1, points2, points3}));
    }

    @Test
    public void exportChangeModify() throws Exception {
        insertAndAdd(points1);
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("Some message").call();
        MemoryDataStore store = getChangeStore(pointsType);
        final String typeName = store.getTypeNames()[0];
        SimpleFeatureSource featureSource = store.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName).setTransactional(false).call();
        SimpleFeatureCollection collection1 = featureStore.getFeatures();
        assertEquals(1, collection1.size());
        assertTrue(collectionsAreEqual(collection1.features(), new Feature[]{points1}));
        insertAndAdd(points1_modified);
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("Some message").call();
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setOldRef(commit1.getTreeId().toString())
                .setNewRef(commit2.getTreeId().toString()).setPath(pointsName).setTransactional(false).call();
        SimpleFeatureCollection collection2 = featureStore.getFeatures();
        assertEquals(1, collection2.size());
        assertTrue(collectionsAreEqual(collection2.features(), new Feature[]{points1_modified}));
    }

    @Test
    public void exportChangeDelete() throws Exception {
        insertAndAdd(points1);
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("Some message").call();
        MemoryDataStore store = getChangeStore(pointsType);
        final String typeName = store.getTypeNames()[0];
        SimpleFeatureSource featureSource = store.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName).setTransactional(false).call();
        SimpleFeatureCollection collection1 = featureStore.getFeatures();
        assertEquals(1, collection1.size());
        assertTrue(collectionsAreEqual(collection1.features(), new Feature[]{points1}));
        deleteAndAdd(points1);
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("Some message").call();
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setOldRef(commit1.getTreeId().toString())
                .setNewRef(commit2.getTreeId().toString()).setPath(pointsName).setTransactional(false).call();
        SimpleFeatureCollection collection2 = featureStore.getFeatures();
        assertEquals(0, collection2.size());
    }

    private void testChangeExport(Feature[] expected, RevCommit since, RevCommit until, SimpleFeatureStore featureStore) throws Exception {
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setOldRef(since.getTreeId().toString())
                .setNewRef(until.getTreeId().toString()).setPath(pointsName).setTransactional(false).call();
        SimpleFeatureCollection collection = featureStore.getFeatures();
        assertEquals(expected.length, collection.size());
        assertTrue(collectionsAreEqual(collection.features(), expected));
    }

    @Test
    public void exportChangeMultipleDelete() throws Exception {
        insertAndAdd(points1);
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("Some message").call();
        MemoryDataStore store = getChangeStore(pointsType);
        final String typeName = store.getTypeNames()[0];
        SimpleFeatureSource featureSource = store.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName).setTransactional(false).call();
        SimpleFeatureCollection collection1 = featureStore.getFeatures();
        assertEquals(1, collection1.size());
        assertTrue(collectionsAreEqual(collection1.features(), new Feature[]{points1}));
        deleteAndAdd(points1);
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("Some message").call();
        insertAndAdd(points1);
        RevCommit commit3 = geogig.command(CommitOp.class).setMessage("Some message").call();
        deleteAndAdd(points1);
        RevCommit commit4 = geogig.command(CommitOp.class).setMessage("Some message").call();
        testChangeExport(new Feature[] {}, commit1, commit2, featureStore);
        testChangeExport(new Feature[] {points1}, commit2, commit3, featureStore);
        testChangeExport(new Feature[] {}, commit3, commit4, featureStore);
    }

    @Test
    public void testChangeMultiple() throws Exception {
        insertAndAdd(points1);
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("Some message").call();
        MemoryDataStore store = getChangeStore(pointsType);
        final String typeName = store.getTypeNames()[0];
        SimpleFeatureSource featureSource = store.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath(pointsName).setTransactional(false).call();
        SimpleFeatureCollection collection1 = featureStore.getFeatures();
        assertEquals(1, collection1.size());
        assertTrue(collectionsAreEqual(collection1.features(), new Feature[]{points1}));
        insertAndAdd(points2);
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("Some message").call();
        insertAndAdd(points1_modified);
        RevCommit commit3 = geogig.command(CommitOp.class).setMessage("Some message").call();
        testChangeExport(new Feature[] {points1_modified, points2}, commit1, commit3, featureStore);
    }
}
