package org.locationtech.geogig.geotools.adapt;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.Name;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

public @UtilityClass class GT {

    private static final GTSimpleFeatureTypeAdapter SFT = new GTSimpleFeatureTypeAdapter();

    private static final GTSimpleFeatureAdapter SF = new GTSimpleFeatureAdapter();

    public static Envelope adapt(BoundingBox opengisBbox) {
        return SFT.adapt(opengisBbox);
    }

    public ReferencedEnvelope adapt(CoordinateReferenceSystem ogcCrs, Envelope env) {
        return SFT.adapt(ogcCrs, env);
    }

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

    public static @NonNull org.locationtech.geogig.feature.Feature adapt(
            @NonNull FeatureType featureType,
            @NonNull org.opengis.feature.simple.SimpleFeature feature) {
        return SF.adapt(featureType, feature);
    }

    public static @NonNull org.opengis.feature.simple.SimpleFeature adapt(
            @NonNull org.locationtech.geogig.feature.Feature feature) {

        SimpleFeatureType type = adapt(feature.getType());
        return SF.adapt(type, feature);
    }

    public @NonNull org.opengis.feature.simple.SimpleFeature adapt(
            @NonNull SimpleFeatureType featureType,
            @NonNull org.locationtech.geogig.feature.Feature feature) {

        return SF.adapt(featureType, feature);
    }

    public @NonNull org.opengis.feature.simple.SimpleFeature[] adapt(
            @NonNull org.locationtech.geogig.feature.Feature... features) {

        SimpleFeature[] sfs = new SimpleFeature[features.length];
        for (int i = 0; i < features.length; i++) {
            Feature feature = features[i];
            SimpleFeature sf = null;
            if (feature != null) {
                SimpleFeatureType type = adapt(feature.getType());
                sf = SF.adapt(type, feature);
            }
            sfs[i] = sf;
        }
        return sfs;
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

    public @Nullable CoordinateReferenceSystem adapt(
            org.locationtech.geogig.crs.CoordinateReferenceSystem crs) {
        return SFT.adapt(crs);
    }

    public @NonNull org.locationtech.geogig.crs.CoordinateReferenceSystem adapt(
            @Nullable CoordinateReferenceSystem crs) {
        return SFT.adapt(crs);
    }

    public static Name adapt(org.opengis.feature.type.Name name) {
        return SFT.adapt(name);
    }

    public static org.opengis.feature.type.Name adapt(Name name) {
        return SFT.adapt(name);
    }

    public static CoordinateReferenceSystem findKnownCrs(CoordinateReferenceSystem crs)
            throws FactoryException {
        String srs = CRS.toSRS(crs);
        if (srs != null && !srs.startsWith("EPSG:")) {
            boolean fullScan = true;
            String knownIdentifier;
            knownIdentifier = CRS.lookupIdentifier(crs, fullScan);
            if (knownIdentifier != null) {
                boolean longitudeFirst = CRS.getAxisOrder(crs).equals(CRS.AxisOrder.EAST_NORTH);
                crs = CRS.decode(knownIdentifier, longitudeFirst);
            } else {
                throw new IllegalArgumentException(
                        "Could not find identifier associated with the defined CRS: \n" + crs);
            }
        }
        return crs;
    }
}
