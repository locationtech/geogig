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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.geotools.util.Converters;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

/**
 * Test suite for {@link MapToStringConverterFactory}
 *
 */
public class MapToStringConverterFactoryTest {
    
    @Test
    public void nullTest(){
        assertNull(Converters.convert(null, Map.class));
        assertNull(Converters.convert("", Map.class));
        assertNull(Converters.convert(" ", Map.class));
    }

    @Test
    public void roundtripTest() {

        Map<String, Object> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key:2", "value|2");
        map.put("key3", Integer.valueOf(12345));
        map.put("nullval", null);
        map.put("arrayval", new double[] { Math.PI, Double.MIN_VALUE, Double.MAX_VALUE });
        map.put("submap", ImmutableMap.of("submap1", "subvalue1", "submap2", new Long(789)));

        assertNull(Converters.convert(map, Integer.class));

        String converted = Converters.convert(map, String.class);
        assertNotNull(converted);

        @SuppressWarnings("unchecked")
        Map<String, Object> roundTripped = Converters.convert(converted, Map.class);
        assertNotNull(roundTripped);

        assertEquals(map.size(), roundTripped.size());
        assertEquals(map.keySet(), roundTripped.keySet());

        for (Entry<String, Object> e : map.entrySet()) {
            assertTrue(Objects.deepEquals(e.getValue(), roundTripped.get(e.getKey())));
        }
    }

}
