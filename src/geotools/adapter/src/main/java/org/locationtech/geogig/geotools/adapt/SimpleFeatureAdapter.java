package org.locationtech.geogig.geotools.adapt;

import java.util.List;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.Feature.FeatureBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import lombok.NonNull;

public class SimpleFeatureAdapter {

    public @NonNull org.locationtech.geogig.feature.Feature adapt(
            @NonNull org.locationtech.geogig.feature.FeatureType type,
            @NonNull org.opengis.feature.Feature feature) {

        SimpleFeature sf = (SimpleFeature) feature;
        FeatureBuilder builder = Feature.builder();
        List<Object> attributes = sf.getAttributes();
        Feature gfeature = builder.values(attributes).type(type).id(sf.getID()).build();
        return gfeature;
    }

    public @NonNull org.opengis.feature.simple.SimpleFeature adapt(
            @NonNull SimpleFeatureType featureType,
            @NonNull org.locationtech.geogig.feature.Feature feature) {

        List<Object> values = feature.getValues();
        SimpleFeatureBuilder gtbuilder = new SimpleFeatureBuilder(featureType);
        gtbuilder.addAll(values);
        SimpleFeature gtfeature = gtbuilder.buildFeature(feature.getId());
        return gtfeature;
    }
}
