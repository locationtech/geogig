package org.locationtech.geogig.geotools.adapt;

import org.geotools.util.factory.Hints;
import org.locationtech.geogig.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import lombok.NonNull;

public class GTSimpleFeatureAdapter {

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

        return new SimpleFeatureAdapter(gtFeatureType, gigFeature);

        //
        // SimpleFeatureBuilder gtbuilder = new SimpleFeatureBuilder(gtFeatureType,
        // new ValidatingFeatureFactoryImpl());
        // gtbuilder.setValidating(true);
        // for (int i = 0; i < gigFeature.getAttributeCount(); i++) {
        // // HACK: Converters.convert returns null when it can't convert.
        // // SimpleFeatureImpl.setAttribute calls validate(null), which in turn omits validation
        // // if the value is null. Same for SimpleFeatureBuilder
        // Object value = gigFeature.getAttribute(i);
        // AttributeDescriptor descriptor = gtFeatureType.getDescriptor(i);
        // Class<?> binding = descriptor.getType().getBinding();
        // Object converted = Converters.convert(value, binding);
        // if (value != null && converted == null) {
        // Types.validate(descriptor, value);// use value instead of converted to force failure
        // }
        // gtbuilder.set(i, converted);
        // }
        // SimpleFeature gtfeature = gtbuilder.buildFeature(gigFeature.getId());
        // return gtfeature;
    }
}
