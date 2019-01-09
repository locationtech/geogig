/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.text;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;

import org.junit.Test;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.impl.RevObjectSerializerConformanceTest;

/**
 *
 */
public class TextRevObjectSerializerTest extends RevObjectSerializerConformanceTest {

    @Override
    protected RevObjectSerializer newObjectSerializer() {
        return new TextRevObjectSerializer();
    }

    @Test
    public void testMalformedSerializedObject() throws Exception {

        // a missing entry
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
        writer.write(TYPE.COMMIT.name() + "\n");
        writer.write("tree\t" + RevObjectTestSupport.hashString("TREE_ID_STRING") + "\n");
        writer.write("author\tvolaya\tvolaya@boundlessgeo.com\n");
        writer.write("commiter\tvolaya<volaya@boundlessgeo.com>\n");
        writer.write("timestamp\t" + Long.toString(12345678) + "\n");
        writer.write("message\tMy message\n");
        writer.flush();

        try {
            serializer.read(RevObjectTestSupport.hashString("ID_STRING"),
                    new ByteArrayInputStream(out.toByteArray()));
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Expected parents"));
        }

        // a wrongly formatted author
        out = new ByteArrayOutputStream();
        writer = new OutputStreamWriter(out, "UTF-8");
        writer.write(TYPE.COMMIT.name() + "\n");
        writer.write("tree\t" + RevObjectTestSupport.hashString("TREE_ID_STRING") + "\n");
        writer.write("parents\t" + RevObjectTestSupport.hashString("PARENT_ID_STRING") + "\n");
        writer.write("author\tvolaya volaya@boundlessgeo.com\n");
        writer.write("commiter\tvolaya volaya@boundlessgeo.com\n");
        writer.write("timestamp\t" + Long.toString(12345678) + "\n");
        writer.write("message\tMy message\n");
        writer.flush();

        try {
            serializer.read(RevObjectTestSupport.hashString("ID_STRING"),
                    new ByteArrayInputStream(out.toByteArray()));
            fail();
        } catch (Exception e) {
            assertTrue(true);
        }
    }

}
