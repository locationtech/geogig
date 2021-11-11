package org.locationtech.geogig.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.Assert;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

public @UtilityClass class RevObjectTestUtil {

    public static void deepEquals(@NonNull RevCommit expected, @NonNull RevCommit actual) {
        assertEquals(TYPE.COMMIT, actual.getType());
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getCommitter(), actual.getCommitter());
        assertEquals(expected.getAuthor(), actual.getAuthor());
        assertEquals(expected.getMessage(), actual.getMessage());
        assertEquals(expected.getParentIds(), actual.getParentIds());
        assertEquals(expected.getTreeId(), actual.getTreeId());
        assertEquals(expected.getType(), actual.getType());
    }

    private static void deepEquals(@NonNull Iterable<Bucket> expected,
            @NonNull Iterable<Bucket> actual) {

        List<Bucket> e = Lists.newArrayList(expected);
        List<Bucket> a = Lists.newArrayList(actual);
        assertEquals(e.size(), a.size());
        for (int i = 0; i < e.size(); i++) {
            deepEquals(e.get(i), a.get(i));
        }
    }

    public static void deepEquals(@NonNull Bucket expected, @NonNull Bucket actual) {
        assertEquals(expected.getObjectId(), actual.getObjectId());
        assertEquals(expected.getIndex(), actual.getIndex());
        assertNotNull(expected.bounds());
        assertNotNull(actual.bounds());
        equalsPrecise(expected.bounds().orElse(null), actual.bounds().orElse(null));
    }

    public static void equalsPrecise(Envelope expected, Envelope actual) {
        final Envelope preciseExpected = RevObjects.makePrecise(expected);
        final Envelope preciseActual = RevObjects.makePrecise(actual);
        assertEquals(preciseExpected, preciseActual);
    }

    public static void deepEquals(@NonNull List<Node> expected, @NonNull List<Node> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            deepEquals(expected.get(i), actual.get(i));
        }
    }

    public static void deepEquals(@NonNull Node expected, @NonNull Node actual) {
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getObjectId(), actual.getObjectId());
        assertEquals(expected.getType(), actual.getType());
        assertNotNull(expected.getMetadataId());
        assertNotNull(actual.getMetadataId());
        assertEquals(expected.getMetadataId().orElse(null), actual.getMetadataId().orElse(null));
        assertNotNull(expected.bounds());
        assertNotNull(actual.bounds());
        equalsPrecise(expected.bounds().orElse(null), actual.bounds().orElse(null));
        Map<String, Object> expectedExtraData = expected.getExtraData();
        Map<String, Object> actualExtraData = actual.getExtraData();
        assertNotNull(expectedExtraData);
        assertNotNull(actualExtraData);
        assertDeepEquals(expectedExtraData, actualExtraData);
    }

    public static void deepEquals(@NonNull RevTree expected, @NonNull RevTree actual) {
        assertEquals(TYPE.TREE, actual.getType());
        assertEquals(expected.getId(), expected.getId());
        assertEquals(expected.size(), expected.size());
        assertEquals(expected.numTrees(), expected.numTrees());
        assertEquals(expected.treesSize(), expected.treesSize());
        assertEquals(expected.featuresSize(), expected.featuresSize());
        assertEquals(expected.bucketsSize(), expected.bucketsSize());
        deepEquals(expected.trees(), actual.trees());
        deepEquals(expected.features(), actual.features());
        deepEquals(expected.getBuckets(), actual.getBuckets());
        for (Bucket b : expected.getBuckets()) {
            deepEquals(b, actual.getBucket(b.getIndex()).orElse(null));
        }
    }

    public static void deepEquals(@NonNull RevFeatureType expected,
            @NonNull RevFeatureType actual) {

        assertEquals(TYPE.FEATURETYPE, actual.getType());
        assertEquals(expected.getId(), actual.getId());
        assertNotNull(expected.getName());
        assertNotNull(actual.getName());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getType(), actual.getType());
        List<PropertyDescriptor> eds = expected.descriptors();
        List<PropertyDescriptor> ads = actual.descriptors();
        assertEquals(eds.size(), ads.size());
        for (int i = 0; i < eds.size(); i++) {
            PropertyDescriptor ed = eds.get(i);
            PropertyDescriptor ad = ads.get(i);
            assertEquals(ed.getName(), ad.getName());
            assertEquals(ed.getBinding(), ad.getBinding());
            assertEquals(ed.getMaxOccurs(), ad.getMaxOccurs());
            assertEquals(ed.getMinOccurs(), ad.getMinOccurs());
            assertEquals(ed.getComplexBindingType(), ad.getComplexBindingType());
            assertEquals(ed.getTypeName(), ad.getTypeName());
            assertEquals(ed.getCoordinateReferenceSystem(), ad.getCoordinateReferenceSystem());
            assertEquals(ed, ad);
        }
        assertEquals(expected, actual);
    }

    public static void deepEquals(@NonNull ValueArray expected, @NonNull ValueArray actual) {
        assertEquals(expected.size(), actual.size());
        List<Integer> geometryIndices = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) {
            Object expectedValue = expected.get(i).orElse(null);
            Object actualValue = actual.get(i).orElse(null);
            if (expectedValue instanceof Geometry) {
                geometryIndices.add(i);
            }
            assertDeepEqualsValue(expectedValue, actualValue);
        }
        GeometryFactory providedGeomFac = new GeometryFactory();
        for (int index : geometryIndices) {
            Optional<Geometry> geom = actual.get(index, providedGeomFac);
            assertTrue(geom.isPresent());
            assertSame(providedGeomFac, geom.get().getFactory());
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertDeepEqualsValue(Object expected, Object actual) {
        if (Objects.equal(expected, actual)) {
            return;
        }
        if (expected == null) {
            fail("expected is null, but actual is " + actual + " (" + actual.getClass().getName()
                    + ")");
        }
        if (actual == null) {
            fail("actual is null, but expected is " + expected + " ("
                    + expected.getClass().getName() + ")");
        }
        if (expected instanceof Map) {
            assertTrue(actual instanceof Map);
            assertDeepEquals((Map<String, Object>) expected, (Map<String, Object>) actual);
        }
        if (expected instanceof Geometry) {
            ObjectId expectedGeomHash = HashObjectFunnels.hashValue(expected);
            ObjectId actualGeomHash = HashObjectFunnels.hashValue(actual);
            if (!expectedGeomHash.equals(actualGeomHash)) {
                assertEquals(expected, actual);
            }
        }
        if (expected.getClass().isArray()) {
            switch (FieldType.forBinding(expected.getClass())) {
            case BOOLEAN_ARRAY:
                Assert.assertArrayEquals((boolean[]) expected, (boolean[]) actual);
                break;
            case BYTE_ARRAY:
                Assert.assertArrayEquals((byte[]) expected, (byte[]) actual);
                break;
            case INTEGER_ARRAY:
                Assert.assertArrayEquals((int[]) expected, (int[]) actual);
                break;
            case CHAR_ARRAY:
                Assert.assertArrayEquals((char[]) expected, (char[]) actual);
                break;
            case DOUBLE_ARRAY:
                Assert.assertArrayEquals((double[]) expected, (double[]) actual, 1e-9);
                break;
            case FLOAT_ARRAY:
                Assert.assertArrayEquals((float[]) expected, (float[]) actual, 1e-7f);
                break;
            case LONG_ARRAY:
                Assert.assertArrayEquals((long[]) expected, (long[]) actual);
                break;
            case SHORT_ARRAY:
                Assert.assertArrayEquals((short[]) expected, (short[]) actual);
                break;
            case STRING_ARRAY:
                Assert.assertArrayEquals((String[]) expected, (String[]) actual);
                break;
            default:
                throw new IllegalStateException(expected.getClass().getName());
            }
            return;
        }
        assertEquals(expected, actual);
    }

    private static void assertDeepEquals(@NonNull Map<String, Object> expected,
            @NonNull Map<String, Object> actual) {
        assertEquals(expected.size(), actual.size());
        assertEquals("Key sets don't match", new TreeSet<>(expected.keySet()),
                new TreeSet<>(actual.keySet()));
        for (String key : expected.keySet()) {
            Object expectedVal = expected.get(key);
            Object actualVal = actual.get(key);
            assertDeepEqualsValue(expectedVal, actualVal);
        }
    }

    public static Object sampleValue(final FieldType fieldType) {
        switch (fieldType) {
        case BIG_DECIMAL:
            return BigDecimal.TEN;
        case BIG_INTEGER:
            return BigInteger.ONE;
        case BOOLEAN:
            return Boolean.TRUE;
        case BOOLEAN_ARRAY:
            return new boolean[] { true, true, false, true, false, false };
        case BYTE:
            return Byte.valueOf((byte) 0xff);
        case BYTE_ARRAY:
            return new byte[] { 1, 2, 3, 4 };
        case CHAR:
            return Character.valueOf((char) 64);
        case CHAR_ARRAY:
            return new char[] { 'a', 'b', 'c', 'd' };
        case DATE:
            return java.sql.Date.valueOf(LocalDate.of(1977, 1, 17));
        case DATETIME:
            return new java.util.Date(1000000000);
        case DOUBLE:
            return 12345.67890D;
        case DOUBLE_ARRAY:
            return new double[] { Double.MIN_VALUE, 0, Double.MAX_VALUE };
        case ENVELOPE_2D:
            return new Envelope(-180, 180, -90, 90);
        case FLOAT:
            return 56.78f;
        case FLOAT_ARRAY:
            return new float[] { Float.MIN_VALUE, 0, Float.MAX_VALUE };
        case LONG:
            return 12345L;
        case LONG_ARRAY:
            return new long[] { Long.MIN_VALUE, 0, Long.MAX_VALUE };
        case MAP:
            return createExtraData(7);
        case INTEGER:
            return 7890;
        case INTEGER_ARRAY:
            return new int[] { Integer.MIN_VALUE, 0, Integer.MAX_VALUE };
        case GEOMETRY:
            return geom("POINT(9.8765 4.321)");
        case GEOMETRYCOLLECTION:
            return geom(
                    "GEOMETRYCOLLECTION(POINT(9.8765 4.321), LINESTRING(0 0, 0 1, 1 1, 1 0, 0 1))");
        case LINESTRING:
            return geom("LINESTRING(0 0, 0 1, 1 1, 1 0, 0 1)");
        case MULTILINESTRING:
            return geom("MULTILINESTRING((0 0, 0 1), (1 1, 1 0, 0 1))");
        case MULTIPOINT:
            return geom("MULTIPOINT((9.8765 4.321), (8.8765 3.321), (7.8765 2.321))");
        case MULTIPOLYGON:
            return geom(
                    "MULTIPOLYGON(((0 0, 0 1, 1 1, 1 0, 0 0)), ((10 10, 10 11, 11 11, 11 10, 10 10)))");
        case NULL:
            return null;
        case POINT:
            return geom("POINT(98.765 43.21)");
        case POLYGON:
            return geom("POLYGON((0 0, 0 1, 1 1, 1 0, 0 0))");
        case SHORT:
            return Short.MIN_VALUE;
        case SHORT_ARRAY:
            return new short[] { Short.MIN_VALUE, 0, Short.MAX_VALUE };
        case STRING:
            return "string value";
        case STRING_ARRAY:
            return new String[] { "string1", "string2", "string3" };
        case TIME:
            return java.sql.Time.valueOf(LocalTime.of(8, 59, 15));
        case TIMESTAMP:
            return new java.sql.Timestamp(1234567890);
        case UUID:
            return UUID.fromString("469d9612-f11a-437b-9c38-bb9ef3eb2f5e");
        default:
            throw new IllegalStateException();
        }
    }

    public static Geometry geom(String wkt) {
        Geometry value;
        try {
            value = new WKTReader().read(wkt);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        return value;
    }

    public static Map<String, Object> createExtraData(int nodeIndex) {
        Map<String, Object> map1, map2, extraData;
        map1 = new HashMap<>();
        map2 = new TreeMap<>();

        map1.put("long", Long.valueOf(nodeIndex));
        map2.put("long", Long.valueOf(nodeIndex));

        map1.put("int", Integer.valueOf(1000 + nodeIndex));
        map2.put("int", Integer.valueOf(1000 + nodeIndex));

        map1.put("string", "hello " + nodeIndex);
        map2.put("string", "hello " + nodeIndex);

        Geometry geom = geom(
                String.format("LINESTRING(%d 1, 1.1 %d.1, 100 1000)", nodeIndex, nodeIndex));
        map1.put("geom", geom);
        map2.put("geom", geom);

        extraData = Map.of("I", (Object) "am", "a", (Object) "different", "map than", (Object) map1,
                "and", (Object) map2);

        return extraData;
    }
}
