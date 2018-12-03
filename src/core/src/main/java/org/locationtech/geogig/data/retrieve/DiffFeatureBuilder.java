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
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.storage.DiffObjectInfo;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Function;

class DiffFeatureBuilder implements Function<DiffObjectInfo<RevFeature>, SimpleFeature> {

    private FeatureBuilder valueBuilder;

    private SimpleFeatureBuilder diffFeatureBuilder;

    private GeometryFactory geometryFactory;

    public DiffFeatureBuilder(SimpleFeatureType diffType, FeatureBuilder valueBuilder,
            GeometryFactory geometryFactory) {
        this.valueBuilder = valueBuilder;
        this.geometryFactory = geometryFactory;
        this.diffFeatureBuilder = new SimpleFeatureBuilder(diffType);
    }

    public @Override SimpleFeature apply(DiffObjectInfo<RevFeature> info) {
        DiffEntry entry = info.entry();

        final String id = entry.name();

        RevFeature oldFeature = info.oldValue().orElse(null);
        RevFeature newFeature = info.newValue().orElse(null);

        SimpleFeature oldValue;
        SimpleFeature newValue;
        oldValue = (SimpleFeature) (oldFeature == null ? null
                : valueBuilder.build(id, oldFeature, geometryFactory));
        newValue = (SimpleFeature) (newFeature == null ? null
                : valueBuilder.build(id, newFeature, geometryFactory));

        SimpleFeature diffFeature;
        diffFeatureBuilder.reset();

        final ChangeType changeType = info.entry().changeType();
        diffFeatureBuilder.set(BulkFeatureRetriever.DIFF_FEATURE_CHANGETYPE_ATTNAME,
                Integer.valueOf(changeType.value()));

        diffFeatureBuilder.set("old", oldValue);
        diffFeatureBuilder.set("new", newValue);
        diffFeature = diffFeatureBuilder.buildFeature(id);
        return diffFeature;
    }
}
