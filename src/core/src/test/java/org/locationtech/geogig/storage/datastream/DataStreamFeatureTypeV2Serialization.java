/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

import static org.locationtech.geogig.api.RevFeatureBuilder.build;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.geotools.data.DataUtilities;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.WKTReader2;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.RevFeatureTypeSerializationTest;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;

import com.google.common.collect.ImmutableMap;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;

public class DataStreamFeatureTypeV2Serialization extends RevFeatureTypeSerializationTest {

    private SimpleFeatureType ftWithMapAttribute;

    @Before
    public void before() {
        SimpleFeatureTypeBuilder ftb = new SimpleFeatureTypeBuilder();
        ftb.setName("TestType");
        ftb.add("name", String.class);
        ftb.add("tags", Map.class);
        
        ftWithMapAttribute = ftb.buildFeatureType();
    }

    @Override
    protected ObjectSerializingFactory getObjectSerializingFactory() {
        return new DataStreamSerializationFactoryV2();
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
