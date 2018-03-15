/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.locationtech.geogig.storage.impl.PersistedIterable.newStringIterable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import com.google.common.collect.Lists;

public class PersistedIterableTest {

    @Test
    public void testNoCompression() {
        boolean compress = false;
        int bufferSize = 10;
        int entryCount = 13;
        test(bufferSize, entryCount, compress);
    }

    @Test
    public void testWithCompression() {
        boolean compress = true;
        int bufferSize = 10;
        int entryCount = 13;
        test(bufferSize, entryCount, compress);
    }

    private void test(final int bufferSize, final int entryCount, final boolean compress) {

        List<String> expected = new ArrayList<>();
        Random rnd = new Random();

        try (PersistedIterable<String> iterable = newStringIterable(bufferSize, compress)) {
            for (int i = 0; i < entryCount; i++) {
                String s = "s " + rnd.nextInt();
                expected.add(s);
                iterable.add(s);
                assertEquals(i + 1, iterable.size());
            }
            assertEquals(expected.size(), iterable.size());
            Iterator<String> iterator = iterable.iterator();
            List<String> actual = Lists.newArrayList(iterator);
            assertEquals(expected.size(), actual.size());
            assertEquals(expected, actual);
            iterable.close();
            assertNull(iterable.serializedFile);
        } finally {
        }
    }

}
