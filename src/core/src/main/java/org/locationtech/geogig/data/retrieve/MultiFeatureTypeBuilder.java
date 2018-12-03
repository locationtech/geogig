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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.factory.Hints;
import org.locationtech.geogig.data.FeatureBuilder;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.base.Function;

class MultiFeatureTypeBuilder implements Function<ObjectInfo<RevFeature>, SimpleFeature> {

    Map<ObjectId, FeatureBuilder> cache = new HashMap<ObjectId, FeatureBuilder>();

    ObjectStore odb;

    public MultiFeatureTypeBuilder(ObjectStore odb) {
        this.odb = odb;
    }

    public synchronized FeatureBuilder get(ObjectId metadataId) {
        FeatureBuilder featureBuilder = cache.get(metadataId);
        if (featureBuilder == null) {
            RevFeatureType revFtype = odb.getFeatureType(metadataId);
            featureBuilder = new FeatureBuilder(revFtype);
            cache.put(metadataId, featureBuilder);
        }
        return featureBuilder;
    }

    @Override
    public SimpleFeature apply(ObjectInfo<RevFeature> info) {
        FeatureBuilder featureBuilder = get(info.ref().getMetadataId());
        return build(featureBuilder, info, null);
    }

    public SimpleFeature build(ObjectId metadataId, String id, RevFeature revFeature,
            @Nullable GeometryFactory geometryFactory) {
     
        FeatureBuilder featureBuilder = get(metadataId);
        return (SimpleFeature) featureBuilder.build(id, revFeature, geometryFactory);
    }

    public static SimpleFeature build(FeatureBuilder featureBuilder, ObjectInfo<RevFeature> info,
            @Nullable GeometryFactory geometryFactory) {

        String fid = info.node().getName();
        RevFeature revFeature = info.object();
        SimpleFeature feature = (SimpleFeature) featureBuilder.build(fid, revFeature,
                geometryFactory);
        feature.getUserData().put(Hints.USE_PROVIDED_FID, Boolean.TRUE);
        feature.getUserData().put(Hints.PROVIDED_FID, fid);
        feature.getUserData().put(RevFeature.class, revFeature);
        feature.getUserData().put(RevFeatureType.class, featureBuilder.getType());
        return feature;
    }
}
