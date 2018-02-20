/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import junit.framework.TestCase;

public class ObjectIdTest extends TestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testIsNull() {
        assertTrue(ObjectId.NULL.isNull());
    }

    @Test
    public void testEquals() {
        ObjectId nullId = ObjectId.valueOf("0000000000000000000000000000000000000000");
        ObjectId id1 = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        ObjectId id2 = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        assertNotSame(id1, id2);
        assertEquals(ObjectId.NULL, nullId);
        assertEquals(id1, id2);
        assertFalse(id1.equals(ObjectId.valueOf("abc123000000000000001234567890abcdef0001")));
        assertFalse(id1.equals("blah"));
    }

    @Test
    public void testToStringAndValueOf() {
        final String hexString = "abc123000000000000001234567890abcdef0000";
        ObjectId id1 = ObjectId.valueOf(hexString);
        String stringRep = id1.toString();
        assertEquals(hexString, stringRep);
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
        //@formatter:off
        byte[] raw = new byte[] { 
                (byte) 0xab, 
                01, 
                02, 
                03, 
                (byte) 0xff, 
                4, 
                8, 
                16, 
                32, 
                64, 
                (byte) 128, 
                -1, 
                -2,
                -4, 
                -8, 
                -16, 
                -32, 
                -64, 
                -128, 
                0 };
        //@formatter:on
        ObjectId oid = ObjectId.create(raw);
        for (int i = 0; i < raw.length; i++) {
            int expected = raw[i] & 0xFF;
            int actual = oid.byteN(i);
            assertEquals("At index " + i, expected, actual);
        }

        try {
            oid.byteN(-1);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            oid.byteN(ObjectId.NUM_BYTES);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testToRaw() {
        byte bytes[] = new byte[] { (byte) 0xff, (byte) 0x68, (byte) 0xb7, (byte) 0x47, (byte) 0x66,
                (byte) 0xe2, (byte) 0x0c, (byte) 0xca };
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
    public void testCreate() {
        byte[] rawBytes = new byte[] { (byte) 0xab, 01, 02, 03, (byte) 0xff, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0 };
        ObjectId id1 = ObjectId.create(rawBytes);
        ObjectId id2 = ObjectId.create(rawBytes);
        assertEquals(id1, id2);
    }

    @Test
    public void testGetRawValue() {
        ObjectId oid = ObjectId.valueOf("ff68b74766e20cca000000000000000000000000");
        byte[] raw = new byte[ObjectId.NUM_BYTES];
        oid.getRawValue(raw);
        byte[] oidRaw = oid.getRawValue();
        for (int i = 0; i < ObjectId.NUM_BYTES; i++) {
            assertEquals(oidRaw[i], raw[i]);
        }
    }

    @Test
    public void testGetRawValue2() {
        byte bytes[] = new byte[] { (byte) 0xff, (byte) 0x68, (byte) 0xb7, (byte) 0x47, (byte) 0x66,
                (byte) 0xe2, (byte) 0x0c, (byte) 0xca, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        ObjectId id1 = ObjectId.valueOf("ff68b74766e20cca000000000000000000000000");
        byte bytes2[] = id1.getRawValue();
        assertTrue(Arrays.equals(bytes, bytes2));
    }

    @Test
    public void testGetRawValueConstraints() {
        ObjectId oid = ObjectId.valueOf("ff68b74766e20cca000000000000000000000000");
        byte[] raw = new byte[ObjectId.NUM_BYTES];
        try {
            oid.getRawValue(raw, -1);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            oid.getRawValue(raw, ObjectId.NUM_BYTES + 1);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        byte[] tooShort = new byte[5];

        try {
            oid.getRawValue(tooShort, ObjectId.NUM_BYTES);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

}
