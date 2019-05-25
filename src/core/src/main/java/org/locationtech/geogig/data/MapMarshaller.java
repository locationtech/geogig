/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.data;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.model.FieldType;

import com.google.common.base.Preconditions;

import lombok.NonNull;

/**
 * Converts values of type {@link Map java.util.Map<String, Object>} to and from String, since
 * GeoGig supports these kind of attributes in {@link Feature}s (See {@link FieldType}).
 */
class MapMarshaller implements Marshaller {

    private static final char VALUE_START = '<';

    private static final char VALUE_END = '>';

    private static final char KEY_VALUE_SEPARATOR = '=';

    private static final char ENTRY_SEPARATOR = '|';

    public @Override Class<?> getValueType() {
        return null;
    }

    public @Override boolean canHandle(@NonNull Class<?> valueClass) {
        return Map.class.isAssignableFrom(valueClass);
    }

    @SuppressWarnings("unchecked")
    public @Override @NonNull String marshall(@NonNull Object object) {
        Map<String, Object> map = (Map<String, Object>) object;
        StringBuilder sb = new StringBuilder();
        for (Iterator<Map.Entry<String, Object>> entries = map.entrySet().iterator(); entries
                .hasNext();) {
            Entry<String, Object> e = entries.next();
            String key = e.getKey();
            Object value = e.getValue();
            FieldType valueType = FieldType.forValue(value);
            String convertedValue = StringConverters.marshall(value);
            sb.append(key);
            sb.append(KEY_VALUE_SEPARATOR);
            sb.append(valueType).append(VALUE_START).append(convertedValue).append(VALUE_END);
            if (entries.hasNext()) {
                sb.append(ENTRY_SEPARATOR);
            }
        }
        return sb.toString();
    }

    public @Override @NonNull Map<String, Object> unmarshall(@NonNull String source,
            @NonNull Class<?> target) {
        if (source == null || source.trim().isEmpty()) {
            return null;
        }
        Map<String, String> entries = EntrySplitter.split((String) source);

        Map<String, Object> map = new HashMap<>();

        for (Entry<String, String> entry : entries.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            int openIdex = value.indexOf('<');
            Preconditions.checkArgument(openIdex > 0 && value.endsWith(">"),
                    "Invalid value format for key %s: '%s'. Expected: {FieldType}<{converted value}>",
                    key, value);
            String valueType = value.substring(0, openIdex);
            String stringValue = value.substring(openIdex + 1, value.length() - 1);

            FieldType valueFieldType;
            try {
                valueFieldType = FieldType.valueOf(valueType);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        String.format("Invalid field type '%s' for key '%s', value '%s;", valueType,
                                key, value),
                        e);
            }
            Class<?> valueBinding = valueFieldType.getBinding();

            Object finalValue = StringConverters.unmarshall(stringValue, valueBinding);

            map.put(key, finalValue);
        }
        return map;
    };

    private static class EntrySplitter {

        public enum States {
            STARTING, IN_VALUE, END_VALUE
        }

        public static Map<String, String> split(final String string) {

            Map<String, String> map = new LinkedHashMap<>();

            States state = States.STARTING;
            StringBuilder temp = new StringBuilder();

            int depth = 0;

            String key = null, value = null;

            for (int i = 0; i < string.length(); i++) {
                char cTemp = string.charAt(i);
                switch (cTemp) {
                case KEY_VALUE_SEPARATOR: {
                    if (state == States.STARTING) {
                        state = States.IN_VALUE;
                        key = temp.toString();
                        temp.setLength(0);
                    } else {
                        temp.append(cTemp);
                    }
                    break;
                }
                case VALUE_START: {
                    depth++;
                    temp.append(cTemp);
                    Preconditions.checkState(state == States.IN_VALUE);
                    break;
                }
                case VALUE_END: {
                    depth--;
                    temp.append(cTemp);
                    if (depth == 0) {
                        state = States.STARTING;
                        value = temp.toString();
                        temp.setLength(0);
                        map.put(key, value);
                    }
                    break;
                }
                case ENTRY_SEPARATOR: {
                    if (depth == 0) {
                        state = States.STARTING;
                    } else {
                        temp.append(cTemp);
                    }
                    break;
                }
                default: {
                    temp.append(cTemp);
                }
                }
            }

            return map;
        }
    }

}
