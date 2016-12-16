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
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

public class AutoCloseableIteratorTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    static class TestAutoCloseableIterator implements AutoCloseableIterator<String> {

        private Iterator<String> source = Lists.newArrayList("item1", "item2", "item11").iterator();

        private AtomicBoolean wasClosed = null;

        TestAutoCloseableIterator(AtomicBoolean wasClosed) {
            this.wasClosed = wasClosed;
        }

        @Override
        public void close() {
            wasClosed.set(true);
        }

        @Override
        public boolean hasNext() {
            return source.hasNext();
        }

        @Override
        public String next() {
            return source.next();
        }

    }

    @Test
    public void testEmptyIterator() {
        try (AutoCloseableIterator<Object> it = AutoCloseableIterator.emptyIterator()) {
            assertFalse(it.hasNext());
            assertEquals(null, it.next());
        }
    }

    @Test
    public void testFromIterator() {
        Iterator<String> original = Lists.newArrayList("item1", "item2").iterator();

        try (AutoCloseableIterator<String> it = AutoCloseableIterator.fromIterator(original)) {
            assertTrue(it.hasNext());
            assertEquals("item1", it.next());
            assertEquals("item2", it.next());
            assertFalse(it.hasNext());
        }
    }

    @Test
    public void testTransform() {
        AtomicBoolean closed = new AtomicBoolean(false);
        TestAutoCloseableIterator testIter = new TestAutoCloseableIterator(closed);
        try (AutoCloseableIterator<String> transformed = AutoCloseableIterator.transform(testIter,
                (str) -> str.toUpperCase());) {
            assertTrue(transformed.hasNext());
            assertEquals("ITEM1", transformed.next());
            assertTrue(transformed.hasNext());
            assertEquals("ITEM2", transformed.next());
            assertTrue(transformed.hasNext());
            assertEquals("ITEM11", transformed.next());
            assertFalse(transformed.hasNext());
        }
        assertTrue(closed.get());
    }

    @Test
    public void testFilter() {
        AtomicBoolean closed = new AtomicBoolean(false);
        TestAutoCloseableIterator testIter = new TestAutoCloseableIterator(closed);
        try (AutoCloseableIterator<String> filtered = AutoCloseableIterator.filter(testIter,
                new Predicate<String>() {
                    @Override
                    public boolean apply(String input) {
                        return input.contains("1");
                    }
                })) {
            assertTrue(filtered.hasNext());
            assertTrue(filtered.hasNext());
            assertEquals("item1", filtered.next());
            assertEquals("item11", filtered.next());
            assertFalse(filtered.hasNext());
            exception.expect(NoSuchElementException.class);
            filtered.next();

        }
        assertTrue(closed.get());
    }

    @Test
    public void testConcat() {
        AtomicBoolean closed1 = new AtomicBoolean(false);
        TestAutoCloseableIterator testIter1 = new TestAutoCloseableIterator(closed1);
        AtomicBoolean closed2 = new AtomicBoolean(false);
        TestAutoCloseableIterator testIter2 = new TestAutoCloseableIterator(closed2);
        try (AutoCloseableIterator<String> concatenated = AutoCloseableIterator.concat(testIter1,
                testIter2)) {
            assertTrue(concatenated.hasNext());
            assertEquals("item1", concatenated.next());
            assertEquals("item2", concatenated.next());
            assertEquals("item11", concatenated.next());
            assertEquals("item1", concatenated.next());
            assertEquals("item2", concatenated.next());
            assertEquals("item11", concatenated.next());
            assertFalse(concatenated.hasNext());
        }
        assertTrue(closed1.get());
        assertTrue(closed2.get());
    }
}
