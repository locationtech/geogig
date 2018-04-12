/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.data.retrieve;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.data.FeatureBuilder;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.storage.DiffObjectInfo;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Function;
import com.vividsolutions.jts.geom.GeometryFactory;

class DiffFeatureFlattenedBuilder implements Function<DiffObjectInfo<RevFeature>, SimpleFeature> {

    private SimpleFeatureType diffType;

    private SimpleFeatureBuilder diffFeatureBuilder;

    private GeometryFactory geometryFactory;

    public DiffFeatureFlattenedBuilder(SimpleFeatureType diffType,
            GeometryFactory geometryFactory) {
        this.diffType = diffType;
        this.geometryFactory = geometryFactory;
        this.diffFeatureBuilder = new SimpleFeatureBuilder(diffType);
    }

    public @Override SimpleFeature apply(DiffObjectInfo<RevFeature> info) {
        DiffEntry entry = info.entry();

        final String id = entry.name();

        RevFeature oldFeature = info.oldValue().orElse(null);
        RevFeature newFeature = info.newValue().orElse(null);

        for (int i = 0, j = 0; i < diffType.getAttributeCount() / 2; i++, j += 2) {
            Object o = oldFeature == null ? null : oldFeature.get(i).orNull();
            Object n = newFeature == null ? null : newFeature.get(i).orNull();
            diffFeatureBuilder.set(j, o);
            diffFeatureBuilder.set(j + 1, n);
        }

        return diffFeatureBuilder.buildFeature(id);
    }
}
