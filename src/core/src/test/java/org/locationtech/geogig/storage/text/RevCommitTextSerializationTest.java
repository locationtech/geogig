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

import org.junit.Test;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.storage.ObjectReader;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.RevCommitSerializationTest;

/**
 *
 */
public class RevCommitTextSerializationTest extends RevCommitSerializationTest {

    @Override
    protected ObjectSerializingFactory getObjectSerializingFactory() {
        return new TextSerializationFactory();
    }

    @Test
    public void testMalformedSerializedObject() throws Exception {

        // a missing entry
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
        writer.write(TYPE.COMMIT.name() + "\n");
        writer.write("tree\t" + ObjectId.forString("TREE_ID_STRING") + "\n");
        writer.write("author\tvolaya\tvolaya@boundlessgeo.com\n");
        writer.write("commiter\tvolaya<volaya@boundlessgeo.com>\n");
        writer.write("timestamp\t" + Long.toString(12345678) + "\n");
        writer.write("message\tMy message\n");
        writer.flush();

        ObjectReader<RevCommit> reader = factory.createCommitReader();
        try {
            reader.read(ObjectId.forString("ID_STRING"),
                    new ByteArrayInputStream(out.toByteArray()));
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Expected parents"));
        }

        // a wrongly formatted author
        out = new ByteArrayOutputStream();
        writer = new OutputStreamWriter(out, "UTF-8");
        writer.write(TYPE.COMMIT.name() + "\n");
        writer.write("tree\t" + ObjectId.forString("TREE_ID_STRING") + "\n");
        writer.write("parents\t" + ObjectId.forString("PARENT_ID_STRING") + "\n");
        writer.write("author\tvolaya volaya@boundlessgeo.com\n");
        writer.write("commiter\tvolaya volaya@boundlessgeo.com\n");
        writer.write("timestamp\t" + Long.toString(12345678) + "\n");
        writer.write("message\tMy message\n");
        writer.flush();

        try {
            reader.read(ObjectId.forString("ID_STRING"),
                    new ByteArrayInputStream(out.toByteArray()));
            fail();
        } catch (Exception e) {
            assertTrue(true);
        }

        // a wrong category
        out = new ByteArrayOutputStream();
        writer = new OutputStreamWriter(out, "UTF-8");
        writer.write(TYPE.FEATURE.name() + "\n");
        writer.flush();
        try {
            reader.read(ObjectId.forString("ID_STRING"),
                    new ByteArrayInputStream(out.toByteArray()));
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().equals("Wrong type: FEATURE"));
        }

    }

}
