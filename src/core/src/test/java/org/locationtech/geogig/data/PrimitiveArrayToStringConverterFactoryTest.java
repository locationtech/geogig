/* Copyright (c) 2015-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Array;
import java.util.List;

import org.geotools.util.Converters;
import org.junit.Test;

import com.google.common.base.Splitter;

/**
 * Test suite for {@link PrimitiveArrayToStringConverterFactory}
 */
public class PrimitiveArrayToStringConverterFactoryTest {

    @Test
    public void testNull() {
        assertNull(Converters.convert(null, String.class));
        assertNull(Converters.convert(null, int[].class));
        assertNull(Converters.convert("", boolean[].class));
        assertNull(Converters.convert("", byte[].class));
        assertNull(Converters.convert("", short[].class));
        assertNull(Converters.convert("", int[].class));
        assertNull(Converters.convert("", long[].class));
        assertNull(Converters.convert("", float[].class));
        assertNull(Converters.convert("", double[].class));

        assertNull(Converters.convert(" ", boolean[].class));
        assertNull(Converters.convert(" ", byte[].class));
        assertNull(Converters.convert(" ", short[].class));
        assertNull(Converters.convert(" ", int[].class));
        assertNull(Converters.convert(" ", long[].class));
        assertNull(Converters.convert(" ", float[].class));
        assertNull(Converters.convert(" ", double[].class));
    }

    @Test
    public void testBoolean() {
        roundtripTest(new boolean[0]);
        roundtripTest(new boolean[] { true, false, false, false, true });
    }

    @Test
    public void testByte() {
        roundtripTest(new byte[0]);
        roundtripTest(new byte[] { Byte.MIN_VALUE, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, Byte.MAX_VALUE });
    }

    @Test
    public void testShort() {
        roundtripTest(new short[0]);
        roundtripTest(
                new short[] { Short.MIN_VALUE, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, Short.MAX_VALUE });
    }

    @Test
    public void testInt() {
        roundtripTest(new int[0]);
        roundtripTest(
                new int[] { Integer.MIN_VALUE, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, Integer.MAX_VALUE });
    }

    @Test
    public void testLong() {
        roundtripTest(new long[0]);
        roundtripTest(new long[] { Long.MIN_VALUE, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, Long.MAX_VALUE });
    }

    @Test
    public void testFloat() {
        roundtripTest(new float[0]);
        roundtripTest(new float[] { Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.MIN_VALUE, -1.1f, 0, 1.1f, 2.2f, 3.3f, Float.MAX_VALUE });
    }

    @Test
    public void testDouble() {
        roundtripTest(new double[0]);
        roundtripTest(new double[] { Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.MIN_VALUE, -1.1, 0, 1.1, 2.2, 3.3, Double.MAX_VALUE, Math.PI, Math.E });
    }

    private void roundtripTest(Object value) {

        String converted = Converters.convert(value, String.class);
        assertNotNull(converted);
        int length = Array.getLength(value);
        if (0 == length) {
            assertEquals("[]", converted);
        } else {
            assertTrue(converted.startsWith("["));
            assertTrue(converted.endsWith("]"));
            String plain = converted.substring(1);
            plain = plain.substring(0, plain.length() - 1);
            List<String> elems = Splitter.on(',').omitEmptyStrings().trimResults()
                    .splitToList(plain);
            assertEquals(length, elems.size());
        }
        Object roundTripped = Converters.convert(converted, value.getClass());

        assertNotNull(roundTripped);
        assertTrue(roundTripped.getClass().isArray());
        assertTrue(roundTripped.getClass().getComponentType().isPrimitive());

        assertArrayEquals(value, roundTripped);
    }

    private void assertArrayEquals(Object array1, Object array2) {
        assertEquals(Array.getLength(array1), Array.getLength(array2));
        int length = Array.getLength(array1);
        for (int i = 0; i < length; i++) {
            Object v1 = Array.get(array1, i);
            Object v2 = Array.get(array2, i);
            assertEquals(v1, v2);
        }
    }

}
