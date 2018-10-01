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
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;

import com.google.common.annotations.VisibleForTesting;

/**
 * Adapts a closeable iterator of features as a {@link FeatureReader}
 */
public class FeatureReaderAdapter<T extends FeatureType, F extends Feature>
        implements FeatureReader<T, F> {

    private final T schema;

    @VisibleForTesting
    final AutoCloseableIterator<? extends F> iterator;

    public FeatureReaderAdapter(T schema, AutoCloseableIterator<? extends F> iterator) {
        this.schema = schema;
        this.iterator = iterator;
    }

    public static <T extends FeatureType, F extends Feature> FeatureReaderAdapter<T, F> of(T schema,
            AutoCloseableIterator<? extends F> iterator) {
        return new FeatureReaderAdapter<T, F>(schema, iterator);
    }

    @Override
    public T getFeatureType() {
        return schema;
    }

    @Override
    public F next() throws NoSuchElementException {
        try {
            return iterator.next();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    public boolean hasNext() throws IOException {
        try {
            return iterator.hasNext();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    public void close() {
        iterator.close();
    }

}
