/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.data;

import org.geotools.feature.FeatureIterator;
import org.geotools.feature.collection.DecoratingFeatureIterator;
import org.opengis.feature.Feature;

public class ForwardingFeatureIterator<F extends Feature> extends DecoratingFeatureIterator<F> {

    public ForwardingFeatureIterator(FeatureIterator<F> iterator) {
        super(iterator);
    }

}
