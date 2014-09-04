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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

import org.geotools.data.DataUtilities;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.WKTReader2;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureBuilder;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;

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

    protected ObjectSerializingFactory factory = getObjectSerializingFactory();

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
        ObjectWriter<RevFeature> writer = factory.<RevFeature> createObjectWriter(TYPE.FEATURE);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.write(newFeature, output);

        byte[] data = output.toByteArray();
        assertTrue(data.length > 0);

        ObjectReader<RevFeature> reader = factory.<RevFeature> createObjectReader(TYPE.FEATURE);
        ByteArrayInputStream input = new ByteArrayInputStream(data);
        RevFeature feat = reader.read(newFeature.getId(), input);

        assertNotNull(feat);
        assertEquals(newFeature.getValues().size(), feat.getValues().size());

        for (int i = 0; i < newFeature.getValues().size(); i++) {
            assertEquals(newFeature.getValues().get(i).orNull(), feat.getValues().get(i).orNull());
        }

    }

    protected Feature feature(SimpleFeatureType type, String id, Object... values)
            throws ParseException {
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(type);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (type.getDescriptor(i) instanceof GeometryDescriptor) {
                if (value instanceof String) {
                    value = new WKTReader2().read((String) value);
                }
            }
            builder.set(i, value);
        }
        return builder.buildFeature(id);
    }
}
