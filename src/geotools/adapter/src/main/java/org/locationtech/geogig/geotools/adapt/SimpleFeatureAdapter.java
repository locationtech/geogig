package org.locationtech.geogig.geotools.adapt;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.util.factory.Hints;
import org.locationtech.geogig.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import lombok.NonNull;

public class SimpleFeatureAdapter {

    public @NonNull org.locationtech.geogig.feature.Feature adapt(
            @NonNull org.locationtech.geogig.feature.FeatureType gigType,
            @NonNull org.opengis.feature.Feature gtFeature) {

        SimpleFeature sf = (SimpleFeature) gtFeature;
        String fid = sf.getID();
        if (Boolean.TRUE.equals(sf.getUserData().get(Hints.USE_PROVIDED_FID))) {
            Object providedFid = sf.getUserData().get(Hints.PROVIDED_FID);
            if (null != providedFid) {
                fid = String.valueOf(providedFid);
            }
        }

        Feature feature = Feature.build(fid, gigType);
        final int attributeCount = sf.getAttributeCount();
        for (int i = 0; i < attributeCount; i++) {
            feature.setAttribute(i, sf.getAttribute(i));
        }
        return feature;
    }

    public @NonNull org.opengis.feature.simple.SimpleFeature adapt(
            @NonNull SimpleFeatureType gtFeatureType,
            @NonNull org.locationtech.geogig.feature.Feature gigFeature) {

        SimpleFeatureBuilder gtbuilder = new SimpleFeatureBuilder(gtFeatureType);
        for (int i = 0; i < gigFeature.getAttributeCount(); i++) {
            gtbuilder.set(i, gigFeature.getAttribute(i));
        }
        SimpleFeature gtfeature = gtbuilder.buildFeature(gigFeature.getId());
        return gtfeature;
    }
}
