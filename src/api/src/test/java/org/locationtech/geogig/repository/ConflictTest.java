/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.ObjectId;

public class ConflictTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testConstructorAndAccessors() {
        ObjectId id1 = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        ObjectId id2 = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");
        ObjectId id3 = ObjectId.valueOf("abc123000000000000001234567890abcdef0002");

        Conflict conflict = new Conflict("Points/1", id1, id2, id3);
        assertEquals("Points/1", conflict.getPath());
        assertEquals(id1, conflict.getAncestor());
        assertEquals(id2, conflict.getOurs());
        assertEquals(id3, conflict.getTheirs());
    }

    @Test
    public void testEquals() {
        ObjectId id1 = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        ObjectId id2 = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");
        ObjectId id3 = ObjectId.valueOf("abc123000000000000001234567890abcdef0002");
        ObjectId id4 = ObjectId.valueOf("abc123000000000000001234567890abcdef0003");

        Conflict conflict1 = new Conflict("Points/1", id1, id2, id3);
        Conflict conflict2 = new Conflict("Points/2", id1, id2, id3);
        Conflict conflict3 = new Conflict("Points/1", id4, id2, id3);
        Conflict conflict4 = new Conflict("Points/1", id1, id4, id3);
        Conflict conflict5 = new Conflict("Points/1", id1, id2, id4);

        assertFalse(conflict1.equals(conflict2));
        assertFalse(conflict1.equals(conflict3));
        assertFalse(conflict1.equals(conflict4));
        assertFalse(conflict1.equals(conflict5));
        assertTrue(conflict1.equals(conflict1));
        assertTrue(conflict2.equals(conflict2));
        assertTrue(conflict3.equals(conflict3));
        assertTrue(conflict4.equals(conflict4));
        assertTrue(conflict5.equals(conflict5));
        assertFalse(conflict1.equals("conflict1"));
    }

    @Test
    public void testHashCode() {
        ObjectId id1 = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        ObjectId id2 = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");
        ObjectId id3 = ObjectId.valueOf("abc123000000000000001234567890abcdef0002");
        ObjectId id4 = ObjectId.valueOf("abc123000000000000001234567890abcdef0003");

        Conflict conflict1 = new Conflict("Points/1", id1, id2, id3);
        Conflict conflict2 = new Conflict("Points/2", id1, id2, id3);
        Conflict conflict3 = new Conflict("Points/1", id4, id2, id3);
        Conflict conflict4 = new Conflict("Points/1", id1, id4, id3);
        Conflict conflict5 = new Conflict("Points/1", id1, id2, id4);

        assertNotSame(conflict1.hashCode(), conflict2.hashCode());
        assertNotSame(conflict1.hashCode(), conflict3.hashCode());
        assertNotSame(conflict1.hashCode(), conflict4.hashCode());
        assertNotSame(conflict1.hashCode(), conflict5.hashCode());
        assertNotSame(conflict2.hashCode(), conflict3.hashCode());
        assertNotSame(conflict2.hashCode(), conflict4.hashCode());
        assertNotSame(conflict2.hashCode(), conflict5.hashCode());
        assertNotSame(conflict3.hashCode(), conflict4.hashCode());
        assertNotSame(conflict3.hashCode(), conflict5.hashCode());
        assertNotSame(conflict4.hashCode(), conflict5.hashCode());
    }

    @Test
    public void testToString() {
        ObjectId id1 = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        ObjectId id2 = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");
        ObjectId id3 = ObjectId.valueOf("abc123000000000000001234567890abcdef0002");

        Conflict conflict = new Conflict("Points/1", id1, id2, id3);
        String conflictStr = conflict.toString();
        assertTrue(conflictStr.contains("Points/1"));
        assertTrue(conflictStr.contains(id1.toString()));
        assertTrue(conflictStr.contains(id2.toString()));
        assertTrue(conflictStr.contains(id3.toString()));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testValueOf() {
        ObjectId id1 = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        ObjectId id2 = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");
        ObjectId id3 = ObjectId.valueOf("abc123000000000000001234567890abcdef0002");
        Conflict conflict = Conflict.valueOf(
                "Points/1\t" + id1.toString() + "\t" + id2.toString() + "\t" + id3.toString());
        assertEquals("Points/1", conflict.getPath());
        assertEquals(id1, conflict.getAncestor());
        assertEquals(id2, conflict.getOurs());
        assertEquals(id3, conflict.getTheirs());

        exception.expect(IllegalArgumentException.class);
        Conflict.valueOf("Not a valid conflict format");
    }
}
