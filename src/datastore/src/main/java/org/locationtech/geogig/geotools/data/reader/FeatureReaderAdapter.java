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
import com.google.common.base.Throwables;

/**
 * Adapts a closeable iterator of features as a {@link FeatureReader}
 */
class FeatureReaderAdapter<T extends FeatureType, F extends Feature>
        implements FeatureReader<T, F> {

    private final T schema;

    @VisibleForTesting
    final AutoCloseableIterator<? extends F> iterator;

    public FeatureReaderAdapter(T schema, AutoCloseableIterator<? extends F> iterator) {
        this.schema = schema;
        this.iterator = iterator;
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
            throw Throwables.propagate(e);
        }
    }

    @Override
    public boolean hasNext() throws IOException {
        try {
            return iterator.hasNext();
        } catch (RuntimeException e) {
            close();
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void close() {
        iterator.close();
    }

}
