/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class VersionedFormatTest {
    @Test
    public void testConstructorAndAccessors() {
        VersionedFormat format = new VersionedFormat("formatName", "1.0", String.class);
        assertEquals("formatName", format.getFormat());
        assertEquals("1.0", format.getVersion());
        assertEquals(String.class, format.getImplementingClass());
        assertEquals("formatName;v=1.0", format.toString());

        try {
            new VersionedFormat(null, "1.0", String.class);
            fail();
        } catch (NullPointerException e) {
            // expected
        }

        try {
            new VersionedFormat("formatName", null, String.class);
            fail();
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    public void testEquals() {
        VersionedFormat format1 = new VersionedFormat("formatName", "1.0", String.class);
        VersionedFormat format2 = new VersionedFormat("formatName2", "1.0", String.class);
        VersionedFormat format3 = new VersionedFormat("formatName", "2.0", String.class);
        assertTrue(format1.equals(format1));
        assertFalse(format1.equals(format2));
        assertFalse(format1.equals(format3));
        assertFalse(format2.equals(format3));
        assertFalse(format1.equals("someString"));
        assertNotSame(format1.hashCode(), format2.hashCode());
        assertNotSame(format1.hashCode(), format3.hashCode());
        assertNotSame(format2.hashCode(), format3.hashCode());
    }
}
