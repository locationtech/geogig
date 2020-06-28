/* Copyright (c) 2015-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.junit.Test;
import org.locationtech.geogig.model.FieldType;

import com.google.common.collect.ImmutableMap;

/**
 * Test suite for {@link MapMarshaller}
 *
 */
public class MapMarshallerTest {

    @Test
    public void nullTest() {
        assertNull(FieldType.unmarshall(null, Map.class));
        assertNull(FieldType.unmarshall("", Map.class));
        assertNull(FieldType.unmarshall("    ", Map.class));
    }

    @Test
    public void emptyTest() {
        assertEquals(Collections.emptyMap(), FieldType.unmarshall("{}", Map.class));
        assertEquals(Collections.emptyMap(), FieldType.unmarshall(" {} ", Map.class));
        assertEquals(Collections.emptyMap(), FieldType.unmarshall(" {  } ", Map.class));
    }

    @Test
    public void roundtripTest() {

        Map<String, Object> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key:2", "value|2");
        map.put("key3", Integer.valueOf(12345));
        map.put("nullval", null);
        map.put("arrayval", new double[] { Math.PI, Double.MIN_VALUE, Double.MAX_VALUE });
        map.put("submap", ImmutableMap.of("submap1", "subvalue1", "submap2", Long.valueOf(789)));

        // assertNull(StringConverters.marshall(map, Integer.class));

        String converted = FieldType.marshall(map);
        assertNotNull(converted);

        @SuppressWarnings("unchecked")
        Map<String, Object> roundTripped = FieldType.unmarshall(converted, Map.class);
        assertNotNull(roundTripped);

        assertEquals(map.size(), roundTripped.size());
        assertEquals(map.keySet(), roundTripped.keySet());

        for (Entry<String, Object> e : map.entrySet()) {
            assertTrue(Objects.deepEquals(e.getValue(), roundTripped.get(e.getKey())));
        }
    }

}
