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
import org.locationtech.geogig.repository.AutoCloseableIterator;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;

class FeatureReaderAdapter<T extends FeatureType, F extends Feature>
        implements FeatureReader<T, F> {

    private final T schema;

    private final AutoCloseableIterator<F> iterator;

    public FeatureReaderAdapter(T schema, AutoCloseableIterator<F> iterator) {
        this.schema = schema;
        this.iterator = iterator;
    }

    @Override
    public T getFeatureType() {
        return schema;
    }

    @Override
    public F next() throws NoSuchElementException {
        return iterator.next();
    }

    @Override
    public boolean hasNext() throws IOException {
        return iterator.hasNext();
    }

    @Override
    public void close() throws IOException {
        iterator.close();
    }

}
