/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing.diff;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Optional;

/**
 *
 */
public class AttributeDiffTest extends Assert {

    @Before
    public void setUp() {
    }

    @Test
    public void testAttributeDiffRemoved() {
        Optional<Integer> oldValue = Optional.of(1);
        Optional<Integer> newValue = null;

        AttributeDiff diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertTrue(diff.getType() == AttributeDiff.TYPE.REMOVED);
        assertTrue(diff.getOldValue().equals(oldValue));
        assertFalse(diff.getNewValue().isPresent());

        newValue = Optional.absent();
        diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertTrue(diff.getType() == AttributeDiff.TYPE.REMOVED);
        assertTrue(diff.getOldValue().equals(oldValue));
        assertFalse(diff.getNewValue().isPresent());
    }

    @Test
    public void testAttributeDiffAdded() {
        Optional<Integer> oldValue = null;
        Optional<Integer> newValue = Optional.of(1);
        AttributeDiff diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertTrue(diff.getType() == AttributeDiff.TYPE.ADDED);
        assertFalse(diff.getOldValue().isPresent());
        assertTrue(diff.getNewValue().equals(newValue));

        oldValue = Optional.absent();
        diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertTrue(diff.getType() == AttributeDiff.TYPE.ADDED);
        assertFalse(diff.getOldValue().isPresent());
        assertTrue(diff.getNewValue().equals(newValue));
    }

    @Test
    public void testAttributeDiffModified() {
        Optional<Integer> oldValue = Optional.of(1);
        Optional<Integer> newValue = Optional.of(2);
        AttributeDiff diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertTrue(diff.getType() == AttributeDiff.TYPE.MODIFIED);
        assertTrue(diff.getOldValue().equals(oldValue));
        assertTrue(diff.getNewValue().equals(newValue));
    }

    @Test
    public void testAttributeDiffNoChange() {
        Optional<Integer> oldValue = Optional.of(1);
        Optional<Integer> newValue = Optional.of(1);
        AttributeDiff diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertTrue(diff.getType() == AttributeDiff.TYPE.NO_CHANGE);
        assertTrue(diff.getOldValue().equals(oldValue));
        assertTrue(diff.getNewValue().equals(newValue));

        oldValue = Optional.absent();
        newValue = Optional.absent();
        diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertTrue(diff.getType() == AttributeDiff.TYPE.NO_CHANGE);
        assertFalse(diff.getOldValue().isPresent());
        assertFalse(diff.getNewValue().isPresent());

        oldValue = null;
        newValue = null;
        diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertTrue(diff.getType() == AttributeDiff.TYPE.NO_CHANGE);
        assertFalse(diff.getOldValue().isPresent());
        assertFalse(diff.getNewValue().isPresent());

        oldValue = null;
        newValue = Optional.absent();
        diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertTrue(diff.getType() == AttributeDiff.TYPE.NO_CHANGE);
        assertFalse(diff.getOldValue().isPresent());
        assertFalse(diff.getNewValue().isPresent());

        oldValue = Optional.absent();
        newValue = null;
        diff = new GenericAttributeDiffImpl(oldValue, newValue);
        assertTrue(diff.getType() == AttributeDiff.TYPE.NO_CHANGE);
        assertFalse(diff.getOldValue().isPresent());
        assertFalse(diff.getNewValue().isPresent());
    }
}
