/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.ql.porcelain;

import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.Hints;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.collection.DecoratingSimpleFeatureCollection;
import org.geotools.feature.collection.DecoratingSimpleFeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class SetUseProvidedFidSimpleFeatureCollection extends DecoratingSimpleFeatureCollection {

    protected SetUseProvidedFidSimpleFeatureCollection(
            FeatureCollection<SimpleFeatureType, SimpleFeature> delegate) {
        super(delegate);
    }

    @Override
    public SimpleFeatureIterator features() {
        return new SetUseProvidedFidSimpleFeatureIterator(delegate.features());
    }

    private static class SetUseProvidedFidSimpleFeatureIterator
            extends DecoratingSimpleFeatureIterator {

        public SetUseProvidedFidSimpleFeatureIterator(SimpleFeatureIterator iterator) {
            super(iterator);
        }

        @Override
        public SimpleFeature next() throws java.util.NoSuchElementException {
            SimpleFeature next = super.next();
            next.getUserData().put(Hints.USE_PROVIDED_FID, Boolean.TRUE);
            return next;
        }
    }
}
