/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class AttributeDiffTest extends Assert {

    @Before
    public void setUp() {
    }

    @Test
    public void testAttributeDiffRemoved() {
        Integer oldValue = 1;
        Integer newValue = null;

        AttributeDiff diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertEquals(AttributeDiff.TYPE.REMOVED, diff.getType());
        assertEquals(oldValue, diff.getOldValue());
        assertNull(diff.getNewValue());
    }

    @Test
    public void testAttributeDiffAdded() {
        Integer oldValue = null;
        Integer newValue = 1;
        AttributeDiff diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertEquals(AttributeDiff.TYPE.ADDED, diff.getType());
        assertNull(diff.getOldValue());
        assertEquals(newValue, diff.getNewValue());

        oldValue = null;
        diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertEquals(AttributeDiff.TYPE.ADDED, diff.getType());
        assertNull(diff.getOldValue());
        assertEquals(newValue, diff.getNewValue());
    }

    @Test
    public void testAttributeDiffModified() {
        Integer oldValue = 1;
        Integer newValue = 2;
        AttributeDiff diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertEquals(AttributeDiff.TYPE.MODIFIED, diff.getType());
        assertEquals(oldValue, diff.getOldValue());
        assertEquals(newValue, diff.getNewValue());
    }

    @Test
    public void testAttributeDiffNoChange() {
        Integer oldValue = 1;
        Integer newValue = 1;
        AttributeDiff diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertEquals(AttributeDiff.TYPE.NO_CHANGE, diff.getType());
        assertEquals(oldValue, diff.getOldValue());
        assertEquals(newValue, diff.getNewValue());

        oldValue = null;
        newValue = null;
        diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertEquals(AttributeDiff.TYPE.NO_CHANGE, diff.getType());
        assertNull(diff.getOldValue());
        assertNull(diff.getNewValue());

        oldValue = null;
        newValue = null;
        diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertEquals(AttributeDiff.TYPE.NO_CHANGE, diff.getType());
        assertNull(diff.getOldValue());
        assertNull(diff.getNewValue());
    }
}
