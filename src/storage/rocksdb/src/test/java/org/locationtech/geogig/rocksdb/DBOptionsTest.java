package org.locationtech.geogig.rocksdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DBOptionsTest {

    @Test
    public void testConstructor() {
        DBOptions o1 = new DBOptions("path", true);
        assertEquals("path", o1.getDbPath());
        assertTrue(o1.isReadOnly());

        DBOptions o2 = new DBOptions("path2", false);
        assertEquals("path2", o2.getDbPath());
        assertFalse(o2.isReadOnly());
    }

    @Test
    public void testEquals() {
        DBOptions o1 = new DBOptions("path1", true);
        DBOptions o2 = new DBOptions("path1", false);
        DBOptions o3 = new DBOptions("path2", true);
        DBOptions o4 = new DBOptions("path2", false);
        DBOptions o5 = new DBOptions("path2", false);

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
        DBOptions o1 = new DBOptions("path", true);
        assertEquals("rocksdb[path: path, readonly: true]", o1.toString());
    }

}
