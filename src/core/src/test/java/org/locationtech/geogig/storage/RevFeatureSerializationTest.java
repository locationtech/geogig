/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import static org.locationtech.geogig.model.RevFeatureBuilder.build;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.geotools.data.DataUtilities;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.WKTReader2;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureBuilder;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;

public abstract class RevFeatureSerializationTest extends Assert {
    private String namespace1 = "http://geoserver.org/test";

    private String typeName1 = "TestType";

    private String typeSpec1 = "str:String," + //
            "bool:Boolean," + //
            "byte:java.lang.Byte," + //
            "doub:Double," + //
            "bdec:java.math.BigDecimal," + //
            "flt:Float," + //
            "int:Integer," + //
            "bint:java.math.BigInteger," + //
            "pp:Point:srid=4326," + //
            "lng:java.lang.Long," + //
            "datetime:java.util.Date," + //
            "date:java.sql.Date," + //
            "time:java.sql.Time," + //
            "timestamp:java.sql.Timestamp," + //
            "uuid:java.util.UUID";

    protected SimpleFeatureType featureType1;

    private Feature feature1_1;

    protected ObjectSerializingFactory serializer = getObjectSerializingFactory();

    protected abstract ObjectSerializingFactory getObjectSerializingFactory();

    @Before
    public void initializeFeatureAndFeatureType() throws Exception {
        /* now we will setup our feature types and test features. */
        featureType1 = DataUtilities.createType(namespace1, typeName1, typeSpec1);
        // have to store timestamp in a variable since the nanos field is only accessible via setter
        // and getter
        java.sql.Timestamp timestamp = new java.sql.Timestamp(1264396155228L);
        timestamp.setNanos(23456);
        feature1_1 = feature(featureType1, //
                "TestType.feature.1", //
                "StringProp1_1", //
                Boolean.TRUE, //
                Byte.valueOf("18"), //
                new Double(100.01), //
                new BigDecimal("1.89e1021"), //
                new Float(12.5), //
                new Integer(1000), //
                new BigInteger("90000000"), //
                "POINT(1 1)", //
                new Long(800000), //
                new java.util.Date(1264396155228L), //
                new java.sql.Date(1364356800000L), //
                new java.sql.Time(57355228L), //
                timestamp, //
                UUID.fromString("bd882d24-0fe9-11e1-a736-03b3c0d0d06d"));
    }

    @Test
    public void testSerialize() throws Exception {
        testFeatureReadWrite(feature1_1);
    }

    protected void testFeatureReadWrite(Feature feature) throws Exception {

        RevFeature newFeature = RevFeatureBuilder.build(feature);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        serializer.write(newFeature, output);

        byte[] data = output.toByteArray();
        assertTrue(data.length > 0);

        ByteArrayInputStream input = new ByteArrayInputStream(data);
        RevFeature feat = (RevFeature) serializer.read(newFeature.getId(), input);

        assertNotNull(feat);
        assertEquals(newFeature.getValues().size(), feat.getValues().size());

        for (int i = 0; i < newFeature.getValues().size(); i++) {
            assertEquals(newFeature.getValues().get(i).orNull(), feat.getValues().get(i).orNull());
        }

    }

    private Geometry geom(String wkt) throws ParseException {
        Geometry value = new WKTReader2().read(wkt);
        return value;
    }

    protected Feature feature(SimpleFeatureType type, String id, Object... values)
            throws ParseException {
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(type);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (type.getDescriptor(i) instanceof GeometryDescriptor) {
                if (value instanceof String) {
                    value = geom((String) value);
                }
            }
            builder.set(i, value);
        }
        return builder.buildFeature(id);
    }

    @Test
    public void testLargeStringValue() throws Exception {

        SimpleFeatureType type = DataUtilities.createType("LongStringType", "clob:String");

        final int length = 256 * 1024;
        final String largeString = Strings.repeat("a", length);

        Feature feature = feature(type, "fid1", largeString);

        RevFeature revFeature = RevFeatureBuilder.build(feature);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        serializer.write(revFeature, output);

        byte[] data = output.toByteArray();

        ByteArrayInputStream input = new ByteArrayInputStream(data);
        RevFeature feat = (RevFeature) serializer.read(revFeature.getId(), input);
        assertNotNull(feat);
        assertEquals(1, feat.getValues().size());

        Optional<Object> value = feat.getValues().get(0);
        assertTrue(value.isPresent());
        String deserialized = (String) value.get();

        assertEquals(largeString.length(), deserialized.length());
        assertEquals(largeString, deserialized);
    }

    @Test
    public void testMapAttribute() throws Exception {

        SimpleFeatureType featureType = DataUtilities.createType("http://geogig.org/test",
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

        RevFeature revFeature1 = build(feature(featureType, "f1", "the name", map1));
        RevFeature revFeature2 = build(feature(featureType, "f2", "the name", map2));
        RevFeature revFeature3 = build(feature(featureType, "f3", "the name", map3));

        assertEquals(revFeature1, revFeature2);
        assertEquals(revFeature1.getValues(), revFeature2.getValues());

        byte[] data1 = serialize(revFeature1);
        byte[] data2 = serialize(revFeature2);
        byte[] data3 = serialize(revFeature3);

        RevFeature read1 = read(data1, revFeature1.getId());
        RevFeature read2 = read(data2, revFeature2.getId());
        RevFeature read3 = read(data3, revFeature3.getId());

        assertEquals(read1, read2);
        assertEquals(read1.getValues(), read2.getValues());
        assertEquals(revFeature3, read3);
        assertEquals(revFeature3.getValues(), read3.getValues());
    }

    private RevFeature read(byte[] data, ObjectId id) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(data);
        RevFeature rft = (RevFeature) serializer.read(id, input);
        return rft;
    }

    private byte[] serialize(RevFeature revFeature1) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        serializer.write(revFeature1, output);

        byte[] data = output.toByteArray();
        return data;
    }

}
