/* Copyright (c) 2015-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.text;

import java.lang.reflect.Array;
import java.util.List;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.model.FieldType;

import com.google.common.base.Splitter;

import lombok.NonNull;

/**
 * Converts values of primitive arrays types
 * ({@code boolean[], byte[], short[], integer[], long[], float[], double[]}) to and from String,
 * since GeoGig supports these kind of attributes in {@link Feature}s (See {@link FieldType}).
 */
class PrimitiveArrayMarshaller implements Marshaller {

    private final Class<?> target;

    private final FieldType arrayType;

    PrimitiveArrayMarshaller(Class<?> target) {
        Preconditions.checkArgument(target.isArray());
        Preconditions.checkArgument(target.getComponentType().isPrimitive()
                || char[].class.equals(target.getComponentType()));
        this.target = target;
        this.arrayType = FieldType.forBinding(target);
        Preconditions.checkState(arrayType.getBinding().isArray());
    }

    public @Override @NonNull String marshall(@NonNull Object val) {
        Preconditions.checkArgument(val.getClass().isArray());
        final int length = Array.getLength(val);
        StringBuilder sb = new StringBuilder("[");
        if (length > 0) {
            for (int i = 0; i < length - 1; i++) {
                sb.append(Array.get(val, i));
                sb.append(", ");
            }
            sb.append(Array.get(val, length - 1));
        }

        return sb.append(']').toString();

    }

    public Object unmarshall(String string) {
        if (string == null) {
            return null;
        }
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
            case CHAR_ARRAY:
                Array.setChar(array, i, val.charAt(0));
                break;
            default:
                throw new IllegalArgumentException();
            }
        }
        return target.cast(array);
    }
}
