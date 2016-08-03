/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import org.geotools.feature.FeatureIterator;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 * Provides forwarding feature iterators to enable feature to be modified during import.
 */
public abstract class ForwardingFeatureIteratorProvider {

    /**
     * @param iterator the feature iterator to forward
     * @param featureType the feature type of the features in the iterator
     * @return a new feature iterator which may transform the features
     */
    @SuppressWarnings("rawtypes")
    public abstract FeatureIterator forwardIterator(FeatureIterator iterator,
            SimpleFeatureType featureType);

}
