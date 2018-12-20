/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.storage.datastream.FormatCommonV2_1.LazyRevFeature;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTReader;

import com.google.common.collect.Lists;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class FormatCommonV2_1Test {

    RevFeature feature;

    Geometry geom;

    int geomIndex;

    FormatCommonV2_1 encoder = new FormatCommonV2_1();

    List<Object> values;

    @Before
    public void before() throws Exception {
        geom = new WKTReader().read(
                "MULTIPOLYGON (((-121.3647138 38.049474, -121.3646902 38.049614, -121.3646159 38.0496058, -121.3646188 38.049587, -121.3645936 38.049586, -121.3645924 38.0496222, -121.3645056 38.0496178, -121.3645321 38.0494567, -121.3647138 38.049474)))");
        values = Lists.newArrayList(//
                "StringProp1_1", //
                Boolean.TRUE, //
                Byte.valueOf("18"), //
                new Double(100.01), //
                new BigDecimal("1.89e1021"), //
                new Float(12.5), //
                new Integer(1000), //
                new BigInteger("90000000"), //
                new Long(800000), //
                new java.util.Date(1264396155228L), //
                new java.sql.Date(1364356800000L), //
                new java.sql.Time(57355228L), //
                UUID.fromString("bd882d24-0fe9-11e1-a736-03b3c0d0d06d"), //
                geom);
        geomIndex = values.size() - 1;
        feature = RevFeature.builder().addAll(values).build();
    }

    @Test
    public void testWriteFeatureSlowPath() throws IOException {
        ByteArrayDataOutput target = ByteStreams.newDataOutput();
        encoder.writeFeature(feature, target);
        byte[] encoded = target.toByteArray();
        RevFeature decoded = encoder.readFeature(null, ByteStreams.newDataInput(encoded));

        assertTrue(decoded instanceof LazyRevFeature);
        assertEquals(feature, decoded);
        assertTrue(decoded.equals(feature));
        assertEquals(feature.getValues(), decoded.getValues());
    }

    @Test
    public void testWriteFeatureFastPath() throws IOException {

        ByteArrayDataOutput target = ByteStreams.newDataOutput();
        encoder.writeFeature(feature, target);
        byte[] encoded = target.toByteArray();
        LazyRevFeature decoded = (LazyRevFeature) encoder.readFeature(null,
                ByteStreams.newDataInput(encoded));

        target = ByteStreams.newDataOutput();
        encoder.fastEncode(decoded, target);
        encoded = target.toByteArray();

        LazyRevFeature decoded2 = (LazyRevFeature) encoder.readFeature(null,
                ByteStreams.newDataInput(encoded));

        assertTrue(decoded instanceof LazyRevFeature);
        assertEquals(feature, decoded);
        assertTrue(decoded.equals(feature));

        assertEquals(feature.getValues(), decoded.getValues());
        assertEquals(feature, decoded2);
        assertEquals(feature.getValues(), decoded2.getValues());
    }

    @Test
    public void testLazyRevFeatureProvidedGeometryFactory() throws IOException {
        LazyRevFeature decoded;
        {
            ByteArrayDataOutput target = ByteStreams.newDataOutput();
            encoder.writeFeature(feature, target);
            byte[] encoded = target.toByteArray();
            decoded = (LazyRevFeature) encoder.readFeature(null, ByteStreams.newDataInput(encoded));
        }

        GeometryFactory provided = new GeometryFactory();

        Geometry g1 = (Geometry) decoded.get(geomIndex).orNull();
        assertNotSame(geom, g1);
        assertEquals(geom, g1);

        Geometry g2 = decoded.get(geomIndex, provided).orNull();
        assertEquals(geom, g2);
        assertNotSame(g1, g2);
        assertSame(provided, g2.getFactory());
    }

    @Test
    public void testLazyRevFeatureForEach() throws IOException {
        LazyRevFeature decoded;
        {
            ByteArrayDataOutput target = ByteStreams.newDataOutput();
            encoder.writeFeature(feature, target);
            byte[] encoded = target.toByteArray();
            decoded = (LazyRevFeature) encoder.readFeature(null, ByteStreams.newDataInput(encoded));
        }
        List<Object> values = new ArrayList<>();
        decoded.forEach((v) -> values.add(v));
        assertEquals(this.values, values);
    }
}
