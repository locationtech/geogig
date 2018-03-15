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
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
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
    public void testConcat2() {
        AtomicBoolean closed = new AtomicBoolean(false);
        TestAutoCloseableIterator testIter1 = new TestAutoCloseableIterator(closed);

        AutoCloseableIterator<List<String>> partition = AutoCloseableIterator.partition(testIter1, 1);

        AutoCloseableIterator<Iterator<String>> main = AutoCloseableIterator.transform(partition, item -> item.iterator());

        AutoCloseableIterator<String> result = AutoCloseableIterator.concat(main);
        assertEquals("item1", result.next());
        assertEquals("item2", result.next());
        assertEquals("item11", result.next());

        assertTrue(!closed.get());

        result.close();

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

    @Test
    public void testLimit() {
        AtomicBoolean closed = new AtomicBoolean(false);
        try (AutoCloseableIterator<String> testIter = new TestAutoCloseableIterator(closed)) {
            assertEquals(3, Iterators.size(testIter));
        }
        assertTrue(closed.get());
        closed.set(false);

        AutoCloseableIterator<String> testIter = new TestAutoCloseableIterator(closed);
        AutoCloseableIterator<String> limit = AutoCloseableIterator.limit(testIter, 2);
        assertEquals(2, Iterators.size(limit));
        limit.close();
        assertTrue(closed.get());
    }
    
    @Test
    public void testPartition() {
        AtomicBoolean closed = new AtomicBoolean(false);
        AutoCloseableIterator<String> orig = new TestAutoCloseableIterator(closed);

        AutoCloseableIterator<List<String>> partition = AutoCloseableIterator.partition(orig, 2);
        assertTrue(partition.hasNext());
        assertEquals(2, partition.next().size());
        
        assertTrue(partition.hasNext());
        assertEquals(1, partition.next().size());

        assertFalse(partition.hasNext());
        partition.close();
        assertTrue(closed.get());
    }
}
