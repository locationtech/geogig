/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import java.io.IOException;
import java.util.NoSuchElementException;

import org.geotools.data.FeatureReader;
import org.locationtech.geogig.geotools.adapt.GT;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.annotations.VisibleForTesting;

/**
 * Adapts a closeable iterator of features as a {@link FeatureReader}
 */
public class FeatureReaderAdapter implements FeatureReader<SimpleFeatureType, SimpleFeature> {

    private final SimpleFeatureType schema;

    @VisibleForTesting
    final AutoCloseableIterator<SimpleFeature> iterator;

    public FeatureReaderAdapter(SimpleFeatureType schema,
            AutoCloseableIterator<SimpleFeature> iterator) {
        this.schema = schema;
        this.iterator = iterator;
    }

    public static FeatureReaderAdapter adapt(SimpleFeatureType schema,
            AutoCloseableIterator<org.locationtech.geogig.feature.Feature> iterator) {

        AutoCloseableIterator<SimpleFeature> gtFeatures = AutoCloseableIterator.transform(iterator,
                gf -> GT.adapt((SimpleFeatureType) schema, gf));

        return new FeatureReaderAdapter(schema, gtFeatures);
    }

    public @Override SimpleFeatureType getFeatureType() {
        return schema;
    }

    public @Override SimpleFeature next() throws NoSuchElementException {
        try {
            return iterator.next();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    public @Override boolean hasNext() throws IOException {
        try {
            return iterator.hasNext();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    public @Override void close() {
        iterator.close();
    }

}
