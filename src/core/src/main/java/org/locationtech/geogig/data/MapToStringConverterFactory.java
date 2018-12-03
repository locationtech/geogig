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

import static org.locationtech.geogig.model.FieldType.UNKNOWN;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.geotools.factory.Hints;
import org.geotools.util.Converter;
import org.geotools.util.ConverterFactory;
import org.geotools.util.Converters;
import org.locationtech.geogig.model.FieldType;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.base.Preconditions;

/**
 * Provides GeoTools converters for attributes of type {@link Map java.util.Map<String, Object>} to
 * and from String, since GeoGig supports these kind of attributes in {@link SimpleFeature}s (See
 * {@link FieldType}).
 * <p>
 * Engages into the {@link Converters#getConverterFactories} SPI lookup by means of the
 * {@code src/main/resources/META-INF/services/org.geotools.util.ConverterFactory} file.
 */
public class MapToStringConverterFactory implements ConverterFactory {

    private static final char VALUE_START = '<';

    private static final char VALUE_END = '>';

    private static final char KEY_VALUE_SEPARATOR = '=';

    private static final char ENTRY_SEPARATOR = '|';

    @Override
    public Converter createConverter(Class<?> source, Class<?> target, Hints hints) {

        final FieldType sourceBinding = FieldType.forBinding(source);
        final FieldType targetBinding = FieldType.forBinding(target);
        if (UNKNOWN == sourceBinding || UNKNOWN == targetBinding) {
            return null;
        }
        final boolean sourceIsMap = Map.class.isAssignableFrom(sourceBinding.getBinding());
        final boolean targetIsMap = Map.class.isAssignableFrom(targetBinding.getBinding());
        if (!(sourceIsMap || targetIsMap)) {
            return null;
        }
        if (String.class.equals(sourceBinding.getBinding())) {
            return FROM_STRING;
        }
        if (String.class.equals(targetBinding.getBinding())) {
            return TO_STRING;
        }
        return null;
    }

    private static Converter TO_STRING = new Converter() {

        @Override
        public <T> T convert(Object source, Class<T> target) throws Exception {
            if (source == null) {
                return null;
            }
            Preconditions.checkArgument(Map.class.isAssignableFrom(source.getClass()));
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) source;
            StringBuilder sb = new StringBuilder();

            for (Iterator<Map.Entry<String, Object>> entries = map.entrySet().iterator(); entries
                    .hasNext();) {
                Entry<String, Object> e = entries.next();
                String key = e.getKey();
                Object value = e.getValue();
                FieldType valueType = FieldType.forValue(value);
                String convertedValue = Converters.convert(value, String.class);
                sb.append(key);
                sb.append(KEY_VALUE_SEPARATOR);
                sb.append(valueType).append(VALUE_START).append(convertedValue).append(VALUE_END);
                if (entries.hasNext()) {
                    sb.append(ENTRY_SEPARATOR);
                }
            }

            return target.cast(sb.toString());
        }
    };

    private static Converter FROM_STRING = new Converter() {

        @Override
        public <T> T convert(Object source, Class<T> target) throws Exception {
            Preconditions.checkArgument(source == null || source.getClass().equals(String.class));
            Preconditions.checkArgument(Map.class.isAssignableFrom(target));
            if (source == null || ((String) source).trim().isEmpty()) {
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
                            String.format("Invalid field type '%s' for key '%s', value '%s;",
                                    valueType, key, value),
                            e);
                }
                Class<?> valueBinding = valueFieldType.getBinding();

                Object finalValue = Converters.convert(stringValue, valueBinding);

                map.put(key, finalValue);
            }
            return target.cast(map);
        }

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
