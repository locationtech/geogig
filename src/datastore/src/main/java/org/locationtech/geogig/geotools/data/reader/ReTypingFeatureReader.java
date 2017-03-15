/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import org.geotools.data.FeatureReader;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.identity.FeatureId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * provides functionality for xforming features from one feature type to another.
 * The new feature type is a sub-set of the attributes in the original feature type.
 */
public class ReTypingFeatureReader
        implements FeatureReader<SimpleFeatureType, SimpleFeature> {

    FeatureReader<SimpleFeatureType, SimpleFeature> underlying;
    SimpleFeatureType underlyingSchema;
    SimpleFeatureType newSchema;
    int[] indexesIntoUnderlyingAttributes;

    public ReTypingFeatureReader(FeatureReader<SimpleFeatureType, SimpleFeature> underlying, SimpleFeatureType underlyingSchema, SimpleFeatureType newSchema) {
        this.underlying = underlying;
        this.underlyingSchema = underlyingSchema;
        this.newSchema = newSchema;

        indexesIntoUnderlyingAttributes = new int[newSchema.getAttributeCount()];
        for (int t = 0; t < indexesIntoUnderlyingAttributes.length; t++) {
            indexesIntoUnderlyingAttributes[t] = underlyingSchema.indexOf(newSchema.getDescriptor(t).getName());
        }
    }

    @Override
    public SimpleFeatureType getFeatureType() {
        return newSchema;
    }

    @Override
    public SimpleFeature next() throws IOException, IllegalArgumentException, NoSuchElementException {
        SimpleFeature underlyingFeature = underlying.next();
        FeatureId id = underlyingFeature.getIdentifier();
        List<Object> atts = new ArrayList<>(indexesIntoUnderlyingAttributes.length);
        for (int t = 0; t < indexesIntoUnderlyingAttributes.length; t++) {
            atts.add(underlyingFeature.getAttribute(indexesIntoUnderlyingAttributes[t]));
        }
        SimpleFeature result = new SimpleFeatureImpl(atts, newSchema, id);

        //TODO: Userdata

        return result;
    }

    @Override
    public boolean hasNext() throws IOException {
        return underlying.hasNext();
    }

    @Override
    public void close() throws IOException {
        underlying.close();
    }
}
