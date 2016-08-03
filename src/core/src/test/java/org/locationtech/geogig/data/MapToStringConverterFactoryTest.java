/* Copyright (c) 2015 Boundless and others.
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

import java.util.Map;

import org.geotools.util.Converters;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class MapToStringConverterFactoryTest {

    @Test
    public void roundtripTest() {

        Map<String, ? extends Object> map = ImmutableMap.of(//
                "key1", "value1",//
                "key:2", "value|2",//
                "key3", Integer.valueOf(12345),//
                "submap", ImmutableMap.of("submap1", "subvalue1", "submap2", new Long(789)));

        assertNull(Converters.convert(map, Integer.class));

        String converted = Converters.convert(map, String.class);
        assertNotNull(converted);

        @SuppressWarnings("unchecked")
        Map<String, Object> roundTripped = Converters.convert(converted, Map.class);
        assertNotNull(roundTripped);

        assertEquals(map, roundTripped);
    }

}
