/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal;

import org.geotools.feature.DecoratingFeature;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;

public class MappedFeature extends DecoratingFeature {

    private String path;

    public MappedFeature(String path, Feature feature) {
        super((SimpleFeature) feature);
        this.path = path;
    }

    @Deprecated
    public Feature getFeature() {
        return this;
    }

    public String getPath() {
        return path;
    }

}
