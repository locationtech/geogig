/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.text;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;

import org.junit.Test;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.storage.ObjectReader;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.RevTreeSerializationTest;

public class RevTreeTextSerialiationTest extends RevTreeSerializationTest {

    @Override
    protected ObjectSerializingFactory getObjectSerializingFactory() {
        return new TextSerializationFactory();
    }

    @Test
    public void testMalformedSerializedObject() throws Exception {

        // TODO: add more cases here

        // a wrong type
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
        writer.write(TYPE.FEATURE.name() + "\n");
        writer.flush();
        ObjectReader<RevTree> reader = factory.createRevTreeReader();
        try {
            reader.read(ObjectId.forString("ID_STRING"),
                    new ByteArrayInputStream(out.toByteArray()));
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().equals("Wrong type: FEATURE"));
        }

    }

}
