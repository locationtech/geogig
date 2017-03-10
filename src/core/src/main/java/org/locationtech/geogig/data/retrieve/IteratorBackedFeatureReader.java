/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */

package org.locationtech.geogig.data.retrieve;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.geotools.data.FeatureReader;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 * This is a very simple Geotools utility class that allows a Iterator<SimpleFeature>
 * to "back" a FeatureReader.
 */
class IteratorBackedFeatureReader implements FeatureReader<SimpleFeatureType, SimpleFeature> {

    SimpleFeatureType type;

    Iterator<SimpleFeature> features;

    public IteratorBackedFeatureReader(SimpleFeatureType type, Iterator<SimpleFeature> features) {
        this.type = type;
        this.features = features;
    }

    @Override
    public SimpleFeatureType getFeatureType() {
        return type;
    }

    @Override
    public SimpleFeature next()
            throws IOException, IllegalArgumentException, NoSuchElementException {
        return features.next();
    }

    @Override
    public boolean hasNext() throws IOException {
        return features.hasNext();
    }

    @Override
    public void close() throws IOException {
        // no op (cannot close an iterator)
    }

}
