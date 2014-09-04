/* Copyright (c) 2012-2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import java.util.Arrays;

import junit.framework.TestCase;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ObjectIdTest extends TestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testIsNull() {
        assertTrue(ObjectId.NULL.isNull());
    }

    @Test
    public void testEquals() {
        ObjectId id1 = ObjectId.forString("some content");
        ObjectId id2 = ObjectId.forString("some content");
        assertNotSame(id1, id2);
        assertEquals(id1, id2);
        assertFalse(id1.equals(ObjectId.forString("some other content")));
        assertFalse(id1.equals("blah"));
    }

    @Test
    public void testToStringAndValueOf() {
        ObjectId id1 = ObjectId.forString("some content");
        String stringRep = id1.toString();
        ObjectId valueOf = ObjectId.valueOf(stringRep);
        assertEquals(id1, valueOf);

        boolean caughtException = false;

        try {
            ObjectId.valueOf("bab");
        } catch (IllegalArgumentException e) {
            caughtException = true;
        }
        assertTrue(caughtException);
    }

    @Test
    public void testByeN() {
        ObjectId oid = new ObjectId(new byte[] { 00, 01, 02, 03, (byte) 0xff, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0 });
        assertEquals(0, oid.byteN(0));
        assertEquals(1, oid.byteN(1));
        assertEquals(2, oid.byteN(2));
        assertEquals(3, oid.byteN(3));
        assertEquals(255, oid.byteN(4));
    }

    @Test
    public void testToRaw() {
        byte bytes[] = new byte[] { (byte) 0xff, (byte) 0x68, (byte) 0xb7, (byte) 0x47,
                (byte) 0x66, (byte) 0xe2, (byte) 0x0c, (byte) 0xca };
        byte bytes2[] = ObjectId.toRaw("ff68b74766e20cca");
        assertTrue(Arrays.equals(bytes, bytes2));
        boolean caughtException = false;
        try {
            ObjectId.toRaw("r");
        } catch (IllegalArgumentException e) {
            caughtException = true;
        }
        assertTrue(caughtException);
    }

    @Test
    public void testIncorrectSizeOfHashCode() {
        boolean caughtException = false;
        try {
            new ObjectId(new byte[] { 00 });
        } catch (IllegalArgumentException e) {
            caughtException = true;
        }
        assertTrue(caughtException);
    }

    @Test
    public void testDefaultConstructor() {
        ObjectId oid = new ObjectId();
        assertTrue(oid.isNull());
    }

    @Test
    public void testCompareTo() {
        ObjectId oid = new ObjectId(new byte[] { (byte) 0xab, 01, 02, 03, (byte) 0xff, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
        ObjectId oid2 = new ObjectId(new byte[] { (byte) 0xba, 01, 02, 03, (byte) 0xff, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
        assertTrue(oid.compareTo(oid2) < 0);
        assertTrue(oid2.compareTo(oid) > 0);
        assertEquals(oid.compareTo(oid), 0);
    }

    @Test
    public void testGetRawValue() {
        byte bytes[] = new byte[] { (byte) 0xff, (byte) 0x68, (byte) 0xb7, (byte) 0x47,
                (byte) 0x66, (byte) 0xe2, (byte) 0x0c, (byte) 0xca, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0 };
        ObjectId id1 = ObjectId.valueOf("ff68b74766e20cca000000000000000000000000");
        byte bytes2[] = id1.getRawValue();
        assertTrue(Arrays.equals(bytes, bytes2));
    }

}
