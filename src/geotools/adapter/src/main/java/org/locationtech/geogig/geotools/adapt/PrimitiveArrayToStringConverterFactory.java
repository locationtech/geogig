/* Copyright (c) 2015-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.adapt;

import static org.locationtech.geogig.model.FieldType.UNKNOWN;

import org.geotools.util.Converter;
import org.geotools.util.ConverterFactory;
import org.geotools.util.Converters;
import org.geotools.util.factory.Hints;
import org.locationtech.geogig.model.FieldType;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.base.Preconditions;

/**
 * Provides GeoTools converters for attributes of primitive arrays types (
 * {@code boolean[], byte[], short[], integer[], long[], float[], double[]}) to and from String,
 * since GeoGig supports these kind of attributes in {@link SimpleFeature}s (See {@link FieldType}).
 * <p>
 * Engages into the {@link Converters#getConverterFactories} SPI lookup by means of the
 * {@code src/main/resources/META-INF/services/org.geotools.util.ConverterFactory} file.
 */
public class PrimitiveArrayToStringConverterFactory implements ConverterFactory {

    public @Override Converter createConverter(Class<?> source, Class<?> target, Hints hints) {

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
        public @Override <T> T convert(Object source, Class<T> target) throws Exception {
            return target.cast(FieldType.marshall(source));
        }
    };

    private static Converter FROM_STRING = new Converter() {
        public @Override <T> T convert(Object source, Class<T> target) throws Exception {
            Preconditions.checkArgument(source == null || source.getClass().equals(String.class));
            Preconditions.checkArgument(target.isArray());
            Preconditions.checkArgument(target.getComponentType().isPrimitive());
            return target.cast(FieldType.unmarshall((String) source, target));
        }
    };

}
