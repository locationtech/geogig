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

import org.geotools.feature.FeatureCollection;
import org.geotools.feature.collection.DecoratingFeatureCollection;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;

public class ForwardingFeatureCollection<T extends FeatureType, F extends Feature>
        extends DecoratingFeatureCollection<T, F> {

    public ForwardingFeatureCollection(FeatureCollection<T, F> delegate) {
        super(delegate);
    }

}
