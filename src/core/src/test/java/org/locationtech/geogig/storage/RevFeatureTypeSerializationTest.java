/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.geotools.data.DataUtilities;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevFeatureTypeImpl;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;

import com.vividsolutions.jts.geom.Polygon;

public abstract class RevFeatureTypeSerializationTest extends Assert {
    private ObjectSerializingFactory factory = getObjectSerializingFactory();

    private String namespace = "http://geoserver.org/test";

    private String typeName = "TestType";

    private String typeSpec = "str:String," + "bool:Boolean," + "byte:java.lang.Byte,"
            + "doub:Double," + "bdec:java.math.BigDecimal," + "flt:Float," + "int:Integer,"
            + "bint:java.math.BigInteger," + "pp:Point:srid=4326," + "lng:java.lang.Long,"
            + "uuid:java.util.UUID";

    private SimpleFeatureType featureType;

    protected abstract ObjectSerializingFactory getObjectSerializingFactory();

    @Before
    public void setUp() throws Exception {
        featureType = DataUtilities.createType(namespace, typeName, typeSpec);
    }

    @Test
    public void testSerialization() throws Exception {
        RevFeatureType revFeatureType = RevFeatureTypeImpl.build(featureType);
        ObjectWriter<RevFeatureType> writer = factory.createObjectWriter(TYPE.FEATURETYPE);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.write(revFeatureType, output);

        byte[] data = output.toByteArray();
        assertTrue(data.length > 0);

        ObjectReader<RevFeatureType> reader = factory.createObjectReader(TYPE.FEATURETYPE);
        ByteArrayInputStream input = new ByteArrayInputStream(data);
        RevFeatureType rft = reader.read(revFeatureType.getId(), input);

        assertNotNull(rft);
        SimpleFeatureType serializedFeatureType = (SimpleFeatureType) rft.type();
        assertEquals(serializedFeatureType.getDescriptors().size(), featureType.getDescriptors()
                .size());

        for (int i = 0; i < featureType.getDescriptors().size(); i++) {
            assertEquals(featureType.getDescriptor(i), serializedFeatureType.getDescriptor(i));
        }

        assertEquals(featureType.getGeometryDescriptor(),
                serializedFeatureType.getGeometryDescriptor());
        assertEquals(featureType.getCoordinateReferenceSystem(),
                serializedFeatureType.getCoordinateReferenceSystem());
    }

    @Test
    public void testSerializationWGS84() throws Exception {
        SimpleFeatureTypeBuilder ftb = new SimpleFeatureTypeBuilder();
        ftb.add("geom", Polygon.class, DefaultGeographicCRS.WGS84);
        ftb.setName("type");
        SimpleFeatureType ftype = ftb.buildFeatureType();
        RevFeatureType revFeatureType = RevFeatureTypeImpl.build(ftype);
        ObjectWriter<RevFeatureType> writer = factory.createObjectWriter(TYPE.FEATURETYPE);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.write(revFeatureType, output);

        byte[] data = output.toByteArray();
        assertTrue(data.length > 0);

        ObjectReader<RevFeatureType> reader = factory.createObjectReader(TYPE.FEATURETYPE);
        ByteArrayInputStream input = new ByteArrayInputStream(data);
        RevFeatureType rft = reader.read(revFeatureType.getId(), input);

        assertNotNull(rft);
        FeatureType serializedFeatureType = rft.type();

        assertEquals("EPSG:4326", CRS.toSRS(serializedFeatureType.getCoordinateReferenceSystem()));

    }

}
