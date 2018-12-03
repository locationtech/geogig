package org.locationtech.geogig.model.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.FieldType;

import com.google.common.collect.ImmutableMap;

/**
 * Holds on the node's extra data as an array of objects to lower the memory impact of HashMap,
 * and makes sure the returned map is a recursive safe copy in order to preserve the node's
 * immutability
 *
 */
public class ExtraData {
    private static ExtraData EMPTY = new ExtraData(new Object[0]);

    private Object[] kvp;

    ExtraData(Object[] kvp) {
        this.kvp = kvp;
    }

    public @Nullable Object get(String key) {
        for (int i = 0; i < kvp.length; i += 2) {
            if (Objects.equals(kvp[i], key)) {
                return safeCopy(kvp[i + 1]);
            }
        }
        return null;
    }

    public Map<String, Object> asMap() {
        final int size = kvp.length;
        if (0 == size) {
            return ImmutableMap.of();
        }
        Map<String, Object> map = new HashMap<>(size);
        for (int i = 0; i < size; i += 2) {
            String k = (String) kvp[i];
            Object v = safeCopy(kvp[i + 1]);
            map.put(k, v);
        }
        return map;
    }

    static ExtraData of(@Nullable Map<String, Object> map) {
        if (null == map || map.isEmpty()) {
            return EMPTY;
        }
        final int size = map.size();
        Object[] kvp = new Object[2 * size];
        int i = 0;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            kvp[i] = e.getKey();
            kvp[i + 1] = safeCopy(e.getValue());
            i += 2;
        }
        return new ExtraData(kvp);
    }

    private static Object safeCopy(Object v) {
        Object safeCopy = FieldType.forValue(v).safeCopy(v);
        return safeCopy;
    }
}