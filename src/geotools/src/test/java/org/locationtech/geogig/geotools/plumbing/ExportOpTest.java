/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Juan Marin (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.Query;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.junit.Test;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException.StatusCode;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Function;
import com.google.common.base.Optional;

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
    public void testExportWithBBOXFilter() throws Exception {
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

        ReferencedEnvelope bbox = (ReferencedEnvelope) points1.getBounds();
        geogig.command(ExportOp.class).setFeatureStore(featureStore).setPath("HEAD:" + pointsName)
                .setBBoxFilter(bbox).call();

        featureSource = dataStore.getFeatureSource(typeName);
        assertEquals(1, featureSource.getCount(Query.ALL));
    }

    @Test
    public void testExportingUsingFunction() throws Exception {
        // Testing export of points feature type into a simplified feature type that
        // does not contain the integer attribute.
        String simplifiedPointsName = "simplifiedPoints";
        String simplifiedPointsTypeSpec = "sp:String,pp:Point:srid=4326";
        SimpleFeatureType simplifiedPointsType = DataUtilities.createType(pointsNs,
                simplifiedPointsName, simplifiedPointsTypeSpec);

        Feature simplifiedPoints1 = feature(simplifiedPointsType, ((SimpleFeature) points1).getID(),
                ((SimpleFeature) points1).getAttribute(0),
                ((SimpleFeature) points1).getAttribute(2));
        Feature simplifiedPoints2 = feature(simplifiedPointsType, ((SimpleFeature) points2).getID(),
                ((SimpleFeature) points2).getAttribute(0),
                ((SimpleFeature) points2).getAttribute(2));
        Feature simplifiedPoints3 = feature(simplifiedPointsType, ((SimpleFeature) points3).getID(),
                ((SimpleFeature) points3).getAttribute(0),
                ((SimpleFeature) points3).getAttribute(2));

        Feature[] simplifiedPoints = new Feature[] { simplifiedPoints1, simplifiedPoints2,
                simplifiedPoints3 };

        final SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(simplifiedPointsType);
        final Function<Feature, Optional<Feature>> function = (feature) -> {
            SimpleFeature simpleFeature = (SimpleFeature) feature;
            featureBuilder.add(simpleFeature.getAttribute(0));
            featureBuilder.add(simpleFeature.getAttribute(2));
            return Optional.of((Feature) featureBuilder.buildFeature(null));
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
            String wrongFeaturesTypeSpec = "noCommonAtt:String";
            SimpleFeatureType wrongFeaturesType = DataUtilities.createType(pointsNs,
                    wrongFeaturesName, wrongFeaturesTypeSpec);
            final SimpleFeatureBuilder wrongFeatureBuilder = new SimpleFeatureBuilder(
                    wrongFeaturesType);
            final Function<Feature, Optional<Feature>> wrongFunction = (feature) -> {
                SimpleFeature simpleFeature = (SimpleFeature) feature;
                wrongFeatureBuilder.add(simpleFeature.getAttribute(0));
                return Optional.of((Feature) wrongFeatureBuilder.buildFeature(null));
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
    public void testExportFromTreeWithSeveralFeatureTypesUsingDefaultFeatureType()
            throws Exception {
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
                .setAlter(true).setFilterFeatureTypeId(
                        RevFeatureType.builder().type(modifiedPointsType).build().getId())
                .call();
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
                .setFilterFeatureTypeId(
                        RevFeatureType.builder().type(modifiedPointsType).build().getId())
                .call();
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
                    .setFilterFeatureTypeId(RevObjectTestSupport.hashString("fake")).call();
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

}
