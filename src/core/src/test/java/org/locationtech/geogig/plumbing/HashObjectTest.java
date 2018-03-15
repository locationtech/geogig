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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.geotools.data.DataUtilities;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.impl.CommitBuilder;
import org.locationtech.geogig.model.impl.RevFeatureBuilder;
import org.locationtech.geogig.model.impl.RevFeatureTypeBuilder;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.model.impl.RevPersonBuilder;
import org.locationtech.geogig.model.impl.RevTagBuilder;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;

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
        featureType1 = RevFeatureTypeBuilder.build(pointsType);
        featureType2 = RevFeatureTypeBuilder.build(linesType);
        featureType1Duplicate = RevFeatureTypeBuilder.build(pointsType);

        pointFeature1 = RevFeatureBuilder.build(points1);
        pointFeature2 = RevFeatureBuilder.build(points2);
        pointFeature1Duplicate = RevFeatureBuilder.build(points1);

        CommitBuilder b = new CommitBuilder();
        b.setAuthor("groldan");
        b.setAuthorEmail("groldan@boundlessgeo.com");
        b.setCommitter("jdeolive");
        b.setCommitterEmail("jdeolive@boundlessgeo.com");
        b.setMessage("cool this works");
        b.setCommitterTimestamp(1000);
        b.setCommitterTimeZoneOffset(5);

        ObjectId treeId = RevObjectTestSupport.hashString("fake tree content");

        b.setTreeId(treeId);

        ObjectId parentId1 = RevObjectTestSupport.hashString("fake parent content 1");
        ObjectId parentId2 = RevObjectTestSupport.hashString("fake parent content 2");
        List<ObjectId> parentIds = ImmutableList.of(parentId1, parentId2);
        b.setParentIds(parentIds);

        commit1 = b.build();
        commit1Duplicate = b.build();

        b.setMessage(null);
        b.setAuthor(null);
        b.setAuthorEmail(null);
        b.setCommitterTimestamp(-1000);
        b.setCommitterTimeZoneOffset(-5);
        b.setParentIds(ImmutableList.of(parentId1, ObjectId.NULL));

        commit2 = b.build();

        Object boolArray = new boolean[] { true, false, true, true, false };
        Object byteArray = new byte[] { 100, 127, -110, 26, 42 };
        Object charArray = new char[] { 'a', 'b', 'c', 'd', 'e' };
        Object doubleArray = new double[] { 1.5, 1.6, 1.7, 1.8 };
        Object floatArray = new float[] { 1.1f, 3.14f, 6.0f, 0.0f };
        Object intArray = new int[] { 5, 7, 9, 11, 32 };
        Object longArray = new long[] { 100, 200, 300, 400 };

        SimpleFeatureType coverageFeatureType = DataUtilities.createType(
                "http://geoserver.org/test", "TestType",
                "str:String," + "str2:String," + "bool:Boolean," + "byte:java.lang.Byte,"
                        + "doub:Double," + "bdec:java.math.BigDecimal," + "flt:Float,"
                        + "int:Integer," + "bint:java.math.BigInteger,"
                        + "boolArray:java.lang.Object," + "byteArray:java.lang.Object,"
                        + "charArray:java.lang.Object," + "doubleArray:java.lang.Object,"
                        + "floatArray:java.lang.Object," + "intArray:java.lang.Object,"
                        + "longArray:java.lang.Object," + "serialized:java.io.Serializable,"
                        + "randomClass:java.lang.Object," + "pp:Point:srid=4326,"
                        + "lng:java.lang.Long," + "uuid:java.util.UUID");

        coverageRevFeatureType = RevFeatureTypeBuilder.build(coverageFeatureType);

        Feature coverageFeature = feature(coverageFeatureType, "TestType.Coverage.1",
                "StringProp1_1", null, Boolean.TRUE, Byte.valueOf("18"), new Double(100.01),
                new BigDecimal("1.89e1021"), new Float(12.5), new Integer(1000),
                new BigInteger("90000000"), boolArray, byteArray, charArray, doubleArray,
                floatArray, intArray, longArray, "POINT(1 1)", new Long(800000),
                UUID.fromString("bd882d24-0fe9-11e1-a736-03b3c0d0d06d"));

        coverageRevFeature = RevFeatureBuilder.build(coverageFeature);

        hashCommand = new HashObject();

        Context mockCommandLocator = mock(Context.class);
        hashCommand.setContext(mockCommandLocator);
        when(mockCommandLocator.command(eq(DescribeFeatureType.class)))
                .thenReturn(new DescribeFeatureType());

        RevPerson tagger = RevPersonBuilder.build("volaya", "volaya@boundlessgeo.com", -1000, -1);
        RevPerson tagger2 = RevPersonBuilder.build("groldan", "groldan@boundlessgeo.com", 10000, 0);
        tag1 = RevTagBuilder.build("tag1", RevObjectTestSupport.hashString("fake commit id"),
                "message", tagger);
        tag2 = RevTagBuilder.build("tag2",
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

        RevFeature revFeature1 = RevFeatureBuilder.build(f1);
        RevFeature revFeature2 = RevFeatureBuilder.build(f2);
        RevFeature revFeature3 = RevFeatureBuilder.build(f3);

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
            RevFeatureBuilder.builder().addValue(1).addValue("s").addValue(serializableObject)
                    .build();
            fail("Expected IAE");
        } catch (IllegalArgumentException iae) {
            String expected = "Objects of class " + serializableObject.getClass().getName()
                    + " are not supported as RevFeature attributes";
            assertTrue(iae.getMessage(), iae.getMessage().startsWith(expected));
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
