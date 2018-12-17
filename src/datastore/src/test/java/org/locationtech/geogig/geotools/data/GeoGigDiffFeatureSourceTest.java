/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.geotools.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.store.FeatureIteratorIterator;
import org.junit.Test;
import org.locationtech.geogig.data.FeatureBuilder;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class GeoGigDiffFeatureSourceTest extends RepositoryTestCase {

    private GeoGigDataStore dataStore;

    private List<RevCommit> masterBranchCommits, branch1Commits;

    protected @Override void setUpInternal() throws Exception {
        masterBranchCommits = new ArrayList<>();
        branch1Commits = new ArrayList<>();
        dataStore = new GeoGigDataStore(geogig.getRepository());
        dataStore.createSchema(super.pointsType);
        dataStore.createSchema(super.linesType);

        insertAndAdd(points1, lines1);
        masterBranchCommits.add(commit("first commit"));

        insertAndAdd(points2, lines2, lines3);
        masterBranchCommits.add(commit("second commit"));

        branch("branch1");
        checkout("branch1");

        branch1Commits.addAll(masterBranchCommits);

        insertAndAdd(points3, points1_modified);
        branch1Commits.add(commit("third commit, add points3, modify points1"));

        deleteAndAdd(null, lines2);
        branch1Commits.add(commit("fourth commit, deleted lines2"));

        deleteAndAdd(null, points3);
        branch1Commits.add(commit("fifth commit, deleted points3"));

        checkout("master");
    }

    protected @Override void tearDownInternal() throws Exception {
        dataStore.dispose();
        dataStore = null;
    }

    public @Test(expected = IOException.class) void getDiffFeatureSourceNoSuchType()
            throws IOException {
        dataStore.getDiffFeatureSource("NonExistentLayer", "HEAD");
    }

    public @Test void testNoDiff() throws IOException {
        testNoDiffs(pointsName, "HEAD");
        testNoDiffs(linesName, "HEAD");
        testNoDiffs(pointsName, "HEAD~1");
        testNoDiffs(linesName, "HEAD~1");
    }

    private void testNoDiffs(String typeName, String head) throws IOException {
        dataStore.setHead(head);
        SimpleFeatureType valueType = dataStore.getSchema(typeName);

        SimpleFeatureSource source = dataStore.getDiffFeatureSource(typeName, head);
        assertDiffSchema(source, valueType);
        SimpleFeatureCollection collection = source.getFeatures();
        assertNotNull(collection);
        assertFalse(collection.features().hasNext());
    }

    public @Test void testDiff() throws IOException {
        SimpleFeatureSource source = dataStore.getDiffFeatureSource(pointsName, "HEAD~1");
        SimpleFeatureCollection collection = source.getFeatures();
        SimpleFeatureIterator features = collection.features();
        assertTrue(features.hasNext());
        SimpleFeature diffFeature = features.next();
        assertFalse(features.hasNext());
        SimpleFeature oldValue = (SimpleFeature) diffFeature.getAttribute("old");
        SimpleFeature newValue = (SimpleFeature) diffFeature.getAttribute("new");
        assertNull(oldValue);
        assertEquals(points2.getIdentifier(), newValue.getIdentifier());
    }

    public @Test void testDiffBranchVsMaster() throws IOException {
        testLayerDiff(pointsName, "master", "branch1");
        testLayerDiff(linesName, "master", "branch1");
    }

    public @Test void testHeadVsAllPastVersions() throws IOException {
        final String head = "branch1";
        for (RevCommit c : this.branch1Commits) {
            String oldHead = c.getId().toString();
            testLayerDiff(pointsName, oldHead, head);
            testLayerDiff(linesName, oldHead, head);
        }
    }

    public @Test void testAllVersionsVsParent() throws IOException {
        final List<RevCommit> commits = this.branch1Commits;
        // latest commit is the lates on the list
        for (int i = commits.size() - 1; i > 0; i--) {
            String head = commits.get(i).getId().toString();
            String oldHead = head + "~1";
            testLayerDiff(pointsName, oldHead, head);
            testLayerDiff(linesName, oldHead, head);
        }
    }

    private void testLayerDiff(String layerName, String oldHead, String newHead)
            throws IOException {

        final SimpleFeatureType valueType = dataStore.getSchema(layerName);
        final SimpleFeatureSource source = dataStore.getDiffFeatureSource(layerName, oldHead);
        final SimpleFeatureType diffSchema = source.getSchema();
        assertDiffSchema(diffSchema, valueType);

        List<DiffEntry> entries = getExpectedEntries(layerName, oldHead, newHead);
        dataStore.setHead(newHead);
        List<SimpleFeature> features = toList(source.getFeatures());
        assertEquals(entries.size(), features.size());

        final Map<String, DiffEntry> entriesById;
        final Map<String, SimpleFeature> featuresById;

        entriesById = Maps.uniqueIndex(entries, e -> e.name());
        featuresById = Maps.uniqueIndex(features, f -> f.getID());
        assertEquals(entriesById.size(), featuresById.size());

        entriesById.forEach((id, entry) -> {
            SimpleFeature diffFeature = featuresById.get(id);
            assertNotNull(diffFeature);
            assertDiffFeature(entry, diffFeature, diffSchema, valueType);
        });
    }

    private void assertDiffFeature(DiffEntry entry, SimpleFeature diffFeature,
            SimpleFeatureType diffSchema, SimpleFeatureType valueType) {

        assertEquals(diffSchema, diffFeature.getType());
        switch (entry.changeType()) {
        case ADDED:
            assertEquals(0, diffFeature.getAttribute("geogig.changeType"));
            assertNull(diffFeature.getAttribute("old"));
            assertDiffFeature(entry.getNewObject(), diffFeature, "new", valueType);
            break;
        case MODIFIED:
            assertEquals(1, diffFeature.getAttribute("geogig.changeType"));
            assertNotNull(diffFeature.getAttribute("old"));
            assertNotNull(diffFeature.getAttribute("new"));
            assertDiffFeature(entry.getOldObject(), diffFeature, "old", valueType);
            assertDiffFeature(entry.getNewObject(), diffFeature, "new", valueType);
            break;
        case REMOVED:
            assertEquals(2, diffFeature.getAttribute("geogig.changeType"));
            assertNull(diffFeature.getAttribute("new"));
            assertDiffFeature(entry.getOldObject(), diffFeature, "old", valueType);
            break;
        default:
            throw new IllegalStateException();
        }
    }

    private void assertDiffFeature(NodeRef ref, SimpleFeature diffFeature, String attribute,
            SimpleFeatureType valueType) {
        Object value = diffFeature.getAttribute(attribute);
        assertTrue(value instanceof SimpleFeature);

        SimpleFeature valueFeature = (SimpleFeature) value;
        assertEquals(valueType, valueFeature.getType());
        assertEquals(ref.name(), valueFeature.getID());

        RevFeatureType nativeType = repo.objectDatabase().getFeatureType(ref.getMetadataId());
        RevFeature expected = repo.getFeature(ref.getObjectId());
        SimpleFeature expectedFeatureValue = (SimpleFeature) new FeatureBuilder(nativeType)
                .build(ref.name(), expected);

        List<Object> expectedAttributes = expectedFeatureValue.getAttributes();
        List<Object> actualAttributes = valueFeature.getAttributes();
        assertEquals(expectedAttributes, actualAttributes);
    }

    private List<SimpleFeature> toList(SimpleFeatureCollection features) {
        return Lists.newArrayList(new FeatureIteratorIterator<>(features.features()));
    }

    private List<DiffEntry> getExpectedEntries(String layerName, String oldHead, String newHead) {
        AutoCloseableIterator<DiffEntry> diffs = repo.command(DiffTree.class)
                .setPathFilter(layerName).setOldVersion(oldHead).setNewVersion(newHead).call();
        return Lists.newArrayList(diffs);
    }

    private void assertDiffSchema(SimpleFeatureSource diffFeatureSource,
            SimpleFeatureType valueType) {
        assertNotNull(diffFeatureSource);
        SimpleFeatureType schema = diffFeatureSource.getSchema();
        assertDiffSchema(schema, valueType);
    }

    private void assertDiffSchema(SimpleFeatureType diffSchema, SimpleFeatureType valueType) {
        assertNotNull(diffSchema);
        assertEquals(valueType.getTypeName(), diffSchema.getTypeName());
        assertEquals(3, diffSchema.getAttributeCount());
        AttributeDescriptor changeType = diffSchema.getDescriptor(0);
        assertEquals("geogig.changeType", changeType.getLocalName());
        assertEquals(Integer.class, changeType.getType().getBinding());

        AttributeDescriptor oldValueDescriptor = diffSchema.getDescriptor(1);
        AttributeDescriptor newValueDescriptor = diffSchema.getDescriptor(2);
        assertEquals("old", oldValueDescriptor.getLocalName());
        assertEquals("new", newValueDescriptor.getLocalName());
        assertValueDescriptor(oldValueDescriptor, valueType);
        assertValueDescriptor(newValueDescriptor, valueType);
    }

    private void assertValueDescriptor(AttributeDescriptor d, SimpleFeatureType expectedValueType) {
        assertEquals(1, d.getMaxOccurs());
        assertEquals(1, d.getMinOccurs());
        assertTrue(d.isNillable());
        AttributeType actualValueType = d.getType();
        assertNotNull(actualValueType);
        assertEquals(expectedValueType, actualValueType);
    }
}
