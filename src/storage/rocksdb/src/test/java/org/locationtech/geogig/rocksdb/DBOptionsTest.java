/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DBOptionsTest {

    @Test
    public void testConstructor() {
        DBConfig o1 = new DBConfig("path", true);
        assertEquals("path", o1.getDbPath());
        assertTrue(o1.isReadOnly());

        DBConfig o2 = new DBConfig("path2", false);
        assertEquals("path2", o2.getDbPath());
        assertFalse(o2.isReadOnly());
    }

    @Test
    public void testEquals() {
        DBConfig o1 = new DBConfig("path1", true);
        DBConfig o2 = new DBConfig("path1", false);
        DBConfig o3 = new DBConfig("path2", true);
        DBConfig o4 = new DBConfig("path2", false);
        DBConfig o5 = new DBConfig("path2", false);

        assertTrue(o1.equals(o1));
        assertFalse(o1.equals(o2));
        assertFalse(o1.equals(o3));
        assertFalse(o1.equals(o4));
        assertFalse(o1.equals(o5));
        assertFalse(o2.equals(o1));
        assertTrue(o2.equals(o2));
        assertFalse(o2.equals(o3));
        assertFalse(o2.equals(o4));
        assertFalse(o2.equals(o5));
        assertFalse(o3.equals(o1));
        assertFalse(o3.equals(o2));
        assertTrue(o3.equals(o3));
        assertFalse(o3.equals(o4));
        assertFalse(o3.equals(o5));
        assertFalse(o4.equals(o1));
        assertFalse(o4.equals(o2));
        assertFalse(o4.equals(o3));
        assertTrue(o4.equals(o4));
        assertTrue(o4.equals(o5));
        assertFalse(o5.equals(o1));
        assertFalse(o5.equals(o2));
        assertFalse(o5.equals(o3));
        assertTrue(o5.equals(o4));
        assertTrue(o5.equals(o5));

        assertFalse(o1.equals("NotOptions"));
    }

    @Test
    public void testToString() {
        DBConfig o1 = new DBConfig("path", true);
        assertEquals("rocksdb[path: path, readonly: true]", o1.toString());
    }

}
