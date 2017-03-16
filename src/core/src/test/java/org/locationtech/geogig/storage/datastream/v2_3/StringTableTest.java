/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_3;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.Test;

import com.google.common.io.ByteStreams;

public class StringTableTest {

    @Test
    public void testUnique() {
        StringTable t = StringTable.unique();
        t.add("one");
        t.add("two");
        t.add("one");
        t.add("two");
        assertEquals(2, t.size());
        assertEquals("one", t.get(0));
        assertEquals("two", t.get(1));
    }

    @Test
    public void testEncodeUnique() throws IOException {
        StringTable orig = StringTable.unique();
        orig.add("one");
        orig.add("two");
        orig.add("one");
        orig.add("two");

        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(buff);
        orig.encode(out);
        out.writeUTF("some other content");

        StringTable decoded = StringTable.decode(ByteStreams.newDataInput(buff.toByteArray()));
        assertEquals(2, decoded.size());
        assertEquals("one", decoded.get(0));
        assertEquals("two", decoded.get(1));
    }

}
