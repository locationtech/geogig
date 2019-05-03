package org.locationtech.geogig.geotools.adapt;

import org.locationtech.geogig.feature.FeatureType;
import org.opengis.feature.simple.SimpleFeatureType;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

public @UtilityClass class GT {

    private static final SimpleFeatureTypeAdapter SFT = new SimpleFeatureTypeAdapter();

    private static final SimpleFeatureAdapter SF = new SimpleFeatureAdapter();

    public static @NonNull org.locationtech.geogig.feature.Feature adapt(
            @NonNull org.opengis.feature.Feature feature) {
        FeatureType featureType = adapt(feature.getType());
        return SF.adapt(featureType, feature);
    }

    public static @NonNull org.locationtech.geogig.feature.Feature adapt(
            @NonNull org.opengis.feature.simple.SimpleFeature feature) {
        FeatureType featureType = adapt(feature.getType());
        return SF.adapt(featureType, feature);
    }

    public static @NonNull org.opengis.feature.simple.SimpleFeature adapt(
            @NonNull org.locationtech.geogig.feature.Feature feature) {

        SimpleFeatureType type = adapt(feature.getType());
        return SF.adapt(type, feature);
    }

    public static @NonNull org.locationtech.geogig.feature.FeatureType adapt(
            @NonNull org.opengis.feature.type.FeatureType type) {
        if (!(type instanceof SimpleFeatureType)) {
            throw new IllegalArgumentException("Only SimpleFeatureType is supported");
        }
        return SFT.adapt(type);
    }

    public static @NonNull org.locationtech.geogig.feature.FeatureType adapt(
            @NonNull org.opengis.feature.simple.SimpleFeatureType type) {
        return SFT.adapt(type);
    }

    public static @NonNull org.opengis.feature.simple.SimpleFeatureType adapt(
            @NonNull org.locationtech.geogig.feature.FeatureType type) {
        return SFT.adapt(type);
    }
}
