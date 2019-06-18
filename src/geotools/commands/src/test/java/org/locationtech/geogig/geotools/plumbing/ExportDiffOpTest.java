/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import java.util.List;

import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.junit.Ignore;
import org.junit.Test;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.geotools.adapt.GT;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;

import com.google.common.base.Objects;

@Ignore // REVISIT, ExportOp needs a revamp
public class ExportDiffOpTest extends RepositoryTestCase {

    SimpleFeatureType gtPointsType;

    protected @Override void setUpInternal() throws Exception {
        gtPointsType = GT.adapt(pointsType);
    }

    @Test
    public void testExportDiff() throws Exception {
        insertAndAdd(points1);
        final RevCommit insertCommit = repo.command(CommitOp.class).setAll(true).call();

        final String featureId = points1.getId();
        final org.locationtech.geogig.feature.Feature modifiedFeature = feature(points1.getType(),
                featureId, "changedProp", new Integer(1500), "POINT(1 1)");
        insertAndAdd(modifiedFeature, points2);
        final RevCommit changeCommit = repo.command(CommitOp.class).setAll(true).call();

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.add("changetype", String.class);
        for (AttributeDescriptor descriptor : gtPointsType.getAttributeDescriptors()) {
            builder.add(descriptor);
        }
        builder.setName(gtPointsType.getName());
        builder.setCRS(gtPointsType.getCoordinateReferenceSystem());
        SimpleFeatureType outputFeatureType = builder.buildFeatureType();

        FeatureType gigOutputFeatureType = GT.adapt(outputFeatureType);
        final org.locationtech.geogig.feature.Feature outputFeature1 = feature(gigOutputFeatureType,
                featureId, "M", "changedProp", new Integer(1500), "POINT(1 1)");
        final org.locationtech.geogig.feature.Feature outputFeature2 = feature(gigOutputFeatureType,
                points2.getId(), "A", "StringProp1_2", new Integer(2000), "POINT(2 2)");

        MemoryDataStore dataStore = new MemoryDataStore(outputFeatureType);
        final String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        repo.command(ExportDiffOp.class).setFeatureStore(featureStore).setPath(pointsName)
                .setNewRef(changeCommit.getId().toString())
                .setOldRef(insertCommit.getId().toString()).call();
        featureSource = dataStore.getFeatureSource(typeName);
        featureStore = (SimpleFeatureStore) featureSource;
        SimpleFeatureCollection featureCollection = featureStore.getFeatures();
        assertEquals(2, featureCollection.size());
        SimpleFeatureIterator features = featureCollection.features();
        assertTrue(collectionsAreEqual(features, outputFeature1, outputFeature2));
    }

    @Test
    public void testExportDiffUsingOldVersion() throws Exception {
        insertAndAdd(points1);
        final RevCommit insertCommit = repo.command(CommitOp.class).setAll(true).call();

        final String featureId = points1.getId();
        final org.locationtech.geogig.feature.Feature modifiedFeature = feature(points1.getType(),
                featureId, "changedProp", new Integer(1500));
        insertAndAdd(modifiedFeature, points2);
        final RevCommit changeCommit = repo.command(CommitOp.class).setAll(true).call();

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.add(ExportDiffOp.CHANGE_TYPE_NAME, String.class);
        for (AttributeDescriptor descriptor : gtPointsType.getAttributeDescriptors()) {
            builder.add(descriptor);
        }
        builder.setName(gtPointsType.getName());
        builder.setCRS(gtPointsType.getCoordinateReferenceSystem());
        SimpleFeatureType outputFeatureType = builder.buildFeatureType();

        final org.locationtech.geogig.feature.Feature outputFeature1 = feature(
                GT.adapt(outputFeatureType), featureId, "M", "StringProp1_1", new Integer(1000),
                "POINT(1 1)");

        MemoryDataStore dataStore = new MemoryDataStore(outputFeatureType);
        final String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        repo.command(ExportDiffOp.class).setFeatureStore(featureStore).setPath(pointsName)
                .setNewRef(changeCommit.getId().toString())
                .setOldRef(insertCommit.getId().toString()).setUseOld(true).call();
        featureSource = dataStore.getFeatureSource(typeName);
        featureStore = (SimpleFeatureStore) featureSource;
        SimpleFeatureCollection featureCollection = featureStore.getFeatures();
        assertEquals(1, featureCollection.size());
        SimpleFeatureIterator features = featureCollection.features();
        assertTrue(collectionsAreEqual(features, outputFeature1));
    }

    private boolean collectionsAreEqual(SimpleFeatureIterator features,
            org.locationtech.geogig.feature.Feature... points) {
        // features are not iterated in the same order as the original set, so
        // we just do pairwise comparison to check that all the original features
        // are represented in the exported feature store
        while (features.hasNext()) {
            boolean found = true;
            List<Object> attributesExported = features.next().getAttributes();
            for (int i = 0; i < points.length; i++) {
                found = true;
                List<Object> attributesOriginal = points[i].getAttributes();
                for (int j = 0; j < attributesExported.size() - 1; j++) {
                    Object attributeExported = attributesExported.get(j);
                    Object attributeOriginal = attributesOriginal.get(j);
                    if (!Objects.equal(attributeOriginal, attributeExported)) {
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
}
