/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.data.retrieve;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.geogig.storage.ObjectStore;

import lombok.NonNull;

class MultiFeatureTypeBuilder implements Function<ObjectInfo<RevFeature>, Feature> {

    private ConcurrentMap<ObjectId, FeatureType> cache = new ConcurrentHashMap<>();

    private ObjectStore odb;

    public MultiFeatureTypeBuilder(@NonNull ObjectStore odb) {
        this.odb = odb;
    }

    FeatureType get(ObjectId metadataId) {
        FeatureType featureType = cache.computeIfAbsent(metadataId, this::load);
        return featureType;
    }

    private FeatureType load(ObjectId metadataId) {
        RevFeatureType revFtype = odb.getFeatureType(metadataId);
        return revFtype.type();
    }

    public @Override Feature apply(ObjectInfo<RevFeature> info) {
        FeatureType featureType = get(info.ref().getMetadataId());
        String id = info.node().getName();
        RevFeature values = info.object();
        Feature feature = Feature.build(id, featureType, values);
        // feature.getUserData().put(Hints.USE_PROVIDED_FID, Boolean.TRUE);
        // feature.getUserData().put(Hints.PROVIDED_FID, fid);
        // feature.getUserData().put(RevFeature.class, revFeature);
        // feature.getUserData().put(RevFeatureType.class, featureBuilder.getType());
        return feature;
    }
}
