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

import static org.locationtech.geogig.model.FieldType.UNKNOWN;

import java.lang.reflect.Array;
import java.util.List;

import org.geotools.factory.Hints;
import org.geotools.util.Converter;
import org.geotools.util.ConverterFactory;
import org.geotools.util.Converters;
import org.locationtech.geogig.model.FieldType;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

/**
 * Provides GeoTools converters for attributes of primitive arrays types (
 * {@code boolean[], byte[], short[], integer[], long[], float[], double[]}) to and from String,
 * since GeoGig supports these kind of attributes in {@link SimpleFeature}s (See {@link FieldType}).
 * <p>
 * Engages into the {@link Converters#getConverterFactories} SPI lookup by means of the
 * {@code src/main/resources/META-INF/services/org.geotools.util.ConverterFactory} file.
 */
public class PrimitiveArrayToStringConverterFactory implements ConverterFactory {

    @Override
    public Converter createConverter(Class<?> source, Class<?> target, Hints hints) {

        final FieldType sourceBinding = FieldType.forBinding(source);
        final FieldType targetBinding = FieldType.forBinding(target);
        if (UNKNOWN == sourceBinding || UNKNOWN == targetBinding) {
            return null;
        }
        final boolean sourceIsArray = sourceBinding.getBinding().isArray();
        final boolean targetIsArray = targetBinding.getBinding().isArray();
        if (!sourceIsArray && !targetIsArray) {
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
            Preconditions.checkArgument(source.getClass().isArray());
            final int length = Array.getLength(source);
            StringBuilder sb = new StringBuilder("[");
            if (length > 0) {
                for (int i = 0; i < length - 1; i++) {
                    sb.append(Array.get(source, i));
                    sb.append(", ");
                }
                sb.append(Array.get(source, length - 1));
            }

            return target.cast(sb.append(']').toString());

        }
    };

    private static Converter FROM_STRING = new Converter() {

        @Override
        public <T> T convert(Object source, Class<T> target) throws Exception {
            Preconditions.checkArgument(source == null || source.getClass().equals(String.class));
            Preconditions.checkArgument(target.isArray());
            Preconditions.checkArgument(target.getComponentType().isPrimitive());
            if (source == null || ((String) source).trim().isEmpty()) {
                return null;
            }

            final FieldType arrayType = FieldType.forBinding(target);
            Preconditions.checkState(arrayType.getBinding().isArray());

            String string = (String) source;
            if (!string.startsWith("[") && !string.endsWith("]")) {
                return null;
            }
            string = string.substring(1);
            string = string.substring(0, string.length() - 1);

            final List<String> list = Splitter.on(',').omitEmptyStrings().trimResults()
                    .splitToList(string);
            final int length = list.size();
            Object array = Array.newInstance(target.getComponentType(), length);
            for (int i = 0; i < length; i++) {
                String val = list.get(i);
                switch (arrayType) {
                case BOOLEAN_ARRAY:
                    Array.setBoolean(array, i, Boolean.parseBoolean(val));
                    break;
                case BYTE_ARRAY:
                    Array.setByte(array, i, Byte.parseByte(val));
                    break;
                case SHORT_ARRAY:
                    Array.setShort(array, i, Short.parseShort(val));
                    break;
                case INTEGER_ARRAY:
                    Array.setInt(array, i, Integer.parseInt(val));
                    break;
                case LONG_ARRAY:
                    Array.setLong(array, i, Long.parseLong(val));
                    break;
                case FLOAT_ARRAY:
                    Array.setFloat(array, i, Float.parseFloat(val));
                    break;
                case DOUBLE_ARRAY:
                    Array.setDouble(array, i, Double.parseDouble(val));
                    break;
                default:
                    throw new IllegalArgumentException();
                }
            }
            return target.cast(array);
        }
    };

}
