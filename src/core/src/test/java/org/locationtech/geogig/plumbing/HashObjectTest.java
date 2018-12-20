/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.geotools.data.DataUtilities;
import org.junit.Test;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObjectTestUtil;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class HashObjectTest extends RepositoryTestCase {

    private RevFeatureType featureType1;

    private RevFeatureType featureType2;

    private RevFeatureType featureType1Duplicate;

    private RevFeature pointFeature1;

    private RevFeature pointFeature2;

    private RevFeature pointFeature1Duplicate;

    private RevCommit commit1;

    private RevCommit commit2;

    private RevCommit commit1Duplicate;

    private HashObject hashCommand;

    private RevFeature coverageRevFeature;

    private RevFeatureType coverageRevFeatureType;

    private RevTag tag1;

    private RevTag tag2;

    @Override
    protected void setUpInternal() throws Exception {
        featureType1 = RevFeatureType.builder().type(pointsType).build();
        featureType2 = RevFeatureType.builder().type(linesType).build();
        featureType1Duplicate = RevFeatureType.builder().type(pointsType).build();

        pointFeature1 = RevFeature.builder().build(points1);
        pointFeature2 = RevFeature.builder().build(points2);
        pointFeature1Duplicate = RevFeature.builder().build(points1);

        RevCommitBuilder b = RevCommit.builder();
        b.author("groldan");
        b.authorEmail("groldan@boundlessgeo.com");
        b.committer("jdeolive");
        b.committerEmail("jdeolive@boundlessgeo.com");
        b.message("cool this works");
        b.committerTimestamp(1000L);
        b.committerTimeZoneOffset(5);

        ObjectId treeId = RevObjectTestSupport.hashString("fake tree content");

        b.treeId(treeId);

        ObjectId parentId1 = RevObjectTestSupport.hashString("fake parent content 1");
        ObjectId parentId2 = RevObjectTestSupport.hashString("fake parent content 2");
        List<ObjectId> parentIds = ImmutableList.of(parentId1, parentId2);
        b.parentIds(parentIds);

        commit1 = b.build();
        commit1Duplicate = b.build();

        b.message(null);
        b.author(null);
        b.authorEmail(null);
        b.committerTimestamp(-1000L);
        b.committerTimeZoneOffset(-5);
        b.parentIds(ImmutableList.of(parentId1, ObjectId.NULL));

        commit2 = b.build();

        final SimpleFeatureType coverageFeatureType;
        final Feature coverageFeature;
        {
            List<String> attributeDescriptorSpecs = new ArrayList<>();
            List<Object> attributeSampleValues = new ArrayList<>();

            final FieldType[] fieldTypes = FieldType.values();
            for (FieldType ft : fieldTypes) {
                if (ft == FieldType.NULL) {
                    attributeDescriptorSpecs.add("expected_null:String");
                    attributeSampleValues.add(null);
                } else if (ft != FieldType.UNKNOWN) {
                    attributeDescriptorSpecs
                            .add(String.format("%s_type:%s", ft, ft.getBinding().getName()));
                    attributeSampleValues.add(RevObjectTestUtil.sampleValue(ft));
                }
            }
            final String spec = Joiner.on(',').join(attributeDescriptorSpecs);
            coverageFeatureType = DataUtilities.createType("http://geoserver.org/test", "TestType",
                    spec);

            coverageFeature = feature(coverageFeatureType, "TestType.Coverage.1",
                    attributeSampleValues.toArray());

        }
        coverageRevFeatureType = RevFeatureType.builder().type(coverageFeatureType).build();

        coverageRevFeature = RevFeature.builder().build(coverageFeature);

        hashCommand = new HashObject();

        Context mockCommandLocator = mock(Context.class);
        hashCommand.setContext(mockCommandLocator);
        when(mockCommandLocator.command(eq(DescribeFeatureType.class)))
                .thenReturn(new DescribeFeatureType());

        RevPerson tagger = RevPerson.builder().build("volaya", "volaya@boundlessgeo.com", -1000,
                -1);
        RevPerson tagger2 = RevPerson.builder().build("groldan", "groldan@boundlessgeo.com", 10000,
                0);
        tag1 = RevTag.builder().build(null, "tag1",
                RevObjectTestSupport.hashString("fake commit id"), "message", tagger);
        tag2 = RevTag.builder().build(null, "tag2",
                RevObjectTestSupport.hashString("another fake commit id"), "another message",
                tagger2);

    }

    @Test
    public void testHashNullObject() throws Exception {
        try {
            hashCommand.setObject(null).call();
            fail("expected IllegalStateException on null feature type");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Object has not been set"));
        }
    }

    @Test
    public void testHashFeatures() throws Exception {

        ObjectId feature1Id = hashCommand.setObject(pointFeature1).call();
        ObjectId feature2Id = hashCommand.setObject(pointFeature2).call();
        ObjectId coverageFeatureId = hashCommand.setObject(coverageRevFeature).call();
        ObjectId feature1DuplicateId = hashCommand.setObject(pointFeature1Duplicate).call();

        assertNotNull(feature1Id);
        assertNotNull(feature2Id);
        assertNotNull(coverageFeatureId);
        assertNotNull(feature1DuplicateId);

        assertFalse(feature1Id.equals(feature2Id));
        assertFalse(feature1Id.equals(coverageFeatureId));
        assertFalse(feature2Id.equals(coverageFeatureId));
        assertTrue(feature1Id.equals(feature1DuplicateId));

    }

    @Test
    public void testHashFeatureWithMapProperty() throws Exception {
        SimpleFeatureType featureType = DataUtilities.createType("http://geoserver.org/test",
                "TestType", "str:String, map:java.util.Map");

        Map<String, Object> map1, map2, map3;
        map1 = new HashMap<>();
        map2 = new TreeMap<>();

        map1.put("long", Long.valueOf(123));
        map2.put("long", Long.valueOf(123));

        map1.put("int", Integer.valueOf(456));
        map2.put("int", Integer.valueOf(456));

        map1.put("string", "hello");
        map2.put("string", "hello");

        map1.put("geom", geom("LINESTRING(1 1, 1.1 2.1, 100 1000)"));
        map2.put("geom", geom("LINESTRING(1 1, 1.1 2.1, 100 1000)"));

        map3 = ImmutableMap.of("I", (Object) "am", "a", (Object) "different", "map than",
                (Object) map1, "and", (Object) map2);

        Feature f1 = feature(featureType, "f1", "the name", map1);
        Feature f2 = feature(featureType, "f2", "the name", map2);
        Feature f3 = feature(featureType, "f3", "the name", map3);

        RevFeature revFeature1 = RevFeature.builder().build(f1);
        RevFeature revFeature2 = RevFeature.builder().build(f2);
        RevFeature revFeature3 = RevFeature.builder().build(f3);

        ObjectId oid1 = hashCommand.setObject(revFeature1).call();
        ObjectId oid2 = hashCommand.setObject(revFeature2).call();
        ObjectId oid3 = hashCommand.setObject(revFeature3).call();

        assertNotNull(oid1);
        assertNotNull(oid2);
        assertNotNull(oid3);

        assertEquals(oid1, oid2);
        assertFalse(oid1.equals(oid3));
        assertEquals(oid3, hashCommand.setObject(revFeature3).call());
    }

    @Test
    public void tesFeaturePropertyOfUnsupportedType() {
        TestSerializableObject serializableObject = new TestSerializableObject();
        serializableObject.words = "words to serialize";

        try {
            RevFeature.builder().addValue(1).addValue("s").addValue(serializableObject).build();
            fail("Expected IAE");
        } catch (IllegalArgumentException iae) {
            String expected = String.format(
                    "Objects of type %s are not supported as property values (%s)",
                    serializableObject.getClass().getName(), serializableObject);

            assertEquals(expected, iae.getMessage());
        }
    }

    @Test
    public void testHashFeatureTypes() throws Exception {

        ObjectId featureType1Id = hashCommand.setObject(featureType1).call();
        ObjectId featureType2Id = hashCommand.setObject(featureType2).call();
        ObjectId coverageTypeId = hashCommand.setObject(coverageRevFeatureType).call();
        ObjectId featureType1DuplicateId = hashCommand.setObject(featureType1Duplicate).call();

        assertNotNull(featureType1Id);
        assertNotNull(featureType2Id);
        assertNotNull(coverageTypeId);
        assertNotNull(featureType1DuplicateId);

        assertFalse(featureType1Id.equals(featureType2Id));
        assertFalse(featureType1Id.equals(coverageTypeId));
        assertFalse(featureType2Id.equals(coverageTypeId));
        assertTrue(featureType1Id.equals(featureType1DuplicateId));

    }

    @Test
    public void testHashCommits() throws Exception {

        ObjectId commit1Id = hashCommand.setObject(commit1).call();
        ObjectId commit2Id = hashCommand.setObject(commit2).call();
        ObjectId commit1DuplicateId = hashCommand.setObject(commit1Duplicate).call();

        assertNotNull(commit1Id);
        assertNotNull(commit2Id);
        assertNotNull(commit1DuplicateId);

        assertFalse(commit1Id.equals(commit2Id));
        assertTrue(commit1Id.equals(commit1DuplicateId));

    }

    @Test
    public void testHashTags() throws Exception {
        ObjectId tagId = hashCommand.setObject(tag1).call();
        ObjectId tagId2 = hashCommand.setObject(tag2).call();
        assertNotNull(tagId);
        assertNotNull(tagId2);
        assertNotSame(tagId, tagId2);
    }

    @Test
    public void testHashCommitsConsistency() throws Exception {
        testHashCommitsConsistency(pointFeature1);
        testHashCommitsConsistency(featureType1);
        testHashCommitsConsistency(tag1);
        testHashCommitsConsistency(commit1);
    }

    private void testHashCommitsConsistency(RevObject o) throws Exception {
        ObjectId expected = o.getId();
        ObjectId actual = hashCommand.setObject(o).call();

        assertEquals(expected, actual);
    }
}
