/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.text;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

import org.junit.Test;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.storage.FieldType;
import org.locationtech.geogig.storage.ObjectReader;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.RevFeatureSerializationTest;
import org.opengis.feature.Feature;

public class RevFeatureTextSerializationTest extends RevFeatureSerializationTest {

    @Override
    protected ObjectSerializingFactory getObjectSerializingFactory() {
        return new TextSerializationFactory();
    }

    @Test
    public void testNonAsciiCharacters() throws Exception {

        Feature feature = feature(featureType1, "TestType.feature.1", "геогит", Boolean.TRUE,
                Byte.valueOf("18"), new Double(100.01), new BigDecimal("1.89e1021"),
                new Float(12.5), new Integer(1000), new BigInteger("90000000"), "POINT(1 1)",
                new Long(800000), UUID.fromString("bd882d24-0fe9-11e1-a736-03b3c0d0d06d"));

        testFeatureReadWrite(feature);
    }

    @Test
    public void testMalformedSerializedObject() throws Exception {

        // a wrong value
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
        writer.write(TYPE.FEATURE.name() + "\n");
        writer.write(FieldType.FLOAT.name() + "\tNUMBER" + "\n");
        writer.flush();

        ObjectReader<RevFeature> reader = factory.createFeatureReader();
        try {
            reader.read(ObjectId.forString("ID_STRING"),
                    new ByteArrayInputStream(out.toByteArray()));
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("wrong value"));
        }

        // an unrecognized class
        out = new ByteArrayOutputStream();
        writer = new OutputStreamWriter(out, "UTF-8");
        writer.write(TYPE.FEATURE.name() + "\n");
        writer.write(this.getClass().getName() + "\tvalue" + "\n");
        writer.flush();

        try {
            reader.read(ObjectId.forString("ID_STRING"),
                    new ByteArrayInputStream(out.toByteArray()));
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Wrong type name"));
        }

        // a wrong category
        out = new ByteArrayOutputStream();
        writer = new OutputStreamWriter(out, "UTF-8");
        writer.write(TYPE.COMMIT.name() + "\n");
        writer.flush();
        reader = factory.createFeatureReader();
        try {
            reader.read(ObjectId.forString("ID_STRING"),
                    new ByteArrayInputStream(out.toByteArray()));
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().equals("Wrong type: COMMIT"));
        }

    }

}
