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

import java.util.function.Function;

import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.storage.DiffObjectInfo;
import org.locationtech.jts.geom.GeometryFactory;

class DiffFeatureBuilder implements Function<DiffObjectInfo<RevFeature>, Feature> {

    private GeometryFactory geometryFactory;

    private FeatureType diffType;

    private FeatureType valueType;

    public DiffFeatureBuilder(FeatureType diffType, FeatureType valueType,
            GeometryFactory geometryFactory) {
        this.diffType = diffType;
        this.valueType = valueType;
        this.geometryFactory = geometryFactory;
    }

    public @Override Feature apply(DiffObjectInfo<RevFeature> info) {
        DiffEntry entry = info.entry();

        final String id = entry.name();

        RevFeature oldFeature = info.oldValue().orElse(null);
        RevFeature newFeature = info.newValue().orElse(null);

        Feature oldValue = oldFeature == null ? null
                : Feature.build(id, valueType, oldFeature, geometryFactory);
        Feature newValue = newFeature == null ? null
                : Feature.build(id, valueType, newFeature, geometryFactory);

        Feature diffFeature = Feature.build(id, diffType);
        final ChangeType changeType = info.entry().changeType();
        diffFeature.setAttribute(BulkFeatureRetriever.DIFF_FEATURE_CHANGETYPE_ATTNAME,
                Integer.valueOf(changeType.value()));

        diffFeature.setAttribute("old", oldValue);
        diffFeature.setAttribute("new", newValue);
        return diffFeature;
    }
}
