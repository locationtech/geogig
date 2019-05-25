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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.storage.DiffObjectInfo;

import lombok.NonNull;

class DiffFeatureFlattenedBuilder implements Function<DiffObjectInfo<RevFeature>, Feature> {

    private List<String> nativeAttNames;

    private List<String> flattenedAttNames;

    private @NonNull FeatureType diffType;

    public DiffFeatureFlattenedBuilder(@NonNull FeatureType diffType,
            @NonNull FeatureType nativeType) {
        this.diffType = diffType;
        nativeAttNames = nativeType.getDescriptors().stream().map(d -> d.getName().getLocalPart())
                .collect(Collectors.toList());
        flattenedAttNames = new ArrayList<>(nativeAttNames.size() * 2);
        for (String att : nativeAttNames) {
            flattenedAttNames.add(BulkFeatureRetriever.FLATTENED_ATTNAME_PREFIX_OLD + att);
            flattenedAttNames.add(BulkFeatureRetriever.FLATTENED_ATTNAME_PREFIX_NEW + att);
        }
    }

    public @Override Feature apply(DiffObjectInfo<RevFeature> info) {

        DiffEntry entry = info.entry();

        final String id = entry.name();

        RevFeature oldFeature = info.oldValue().orElse(null);
        RevFeature newFeature = info.newValue().orElse(null);
        final ChangeType changeType = info.entry().changeType();

        Feature diffFeature = Feature.build(id, diffType);
        diffFeature.setAttribute(BulkFeatureRetriever.DIFF_FEATURE_CHANGETYPE_ATTNAME,
                Integer.valueOf(changeType.value()));

        List<String> nativeTypeNames = this.nativeAttNames;
        for (int i = 0; i < nativeTypeNames.size(); i++) {
            String attNameFlattenedOld = flattenedAttNames.get(2 * i);
            String attNameFlattenedNew = flattenedAttNames.get(2 * i + 1);

            Object o = oldFeature == null ? null : oldFeature.get(i).orElse(null);
            Object n = newFeature == null ? null : newFeature.get(i).orElse(null);
            diffFeature.setAttribute(attNameFlattenedOld, o);
            diffFeature.setAttribute(attNameFlattenedNew, n);
        }
        return diffFeature;
    }
}
