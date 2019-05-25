package org.locationtech.geogig.geotools.adapt;

import java.util.List;
import java.util.stream.Collectors;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.FeatureType.FeatureTypeBuilder;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.feature.PropertyDescriptor.PropertyDescriptorBuilder;
import org.locationtech.geogig.model.RevFeatureType;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.FeatureTypeFactory;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyType;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.util.InternationalString;

import lombok.NonNull;

public class SimpleFeatureTypeAdapter extends BaseAdapter {

    private static final FeatureTypeFactory FTF = CommonFactoryFinder.getFeatureTypeFactory(null);

    public @NonNull org.locationtech.geogig.feature.FeatureType adapt(
            org.opengis.feature.type.FeatureType type) {

        return adapt((SimpleFeatureType) type);
    }

    public @NonNull org.locationtech.geogig.feature.FeatureType adapt(
            org.opengis.feature.simple.SimpleFeatureType type) {

        FeatureTypeBuilder builder = FeatureType.builder();
        builder.name(adapt(type.getName()));
        List<PropertyDescriptor> descriptors = type.getDescriptors().stream().map(this::adapt)
                .collect(Collectors.toList());
        builder.descriptors(descriptors);
        return builder.build();
    }

    public @NonNull org.opengis.feature.simple.SimpleFeatureType adapt(
            org.locationtech.geogig.feature.FeatureType type) {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder(FTF);
        builder.setName(adapt(type.getName()));
        type.getDescriptors().forEach(d -> builder.add(adapt(d)));
        return builder.buildFeatureType();
    }

    public @NonNull org.opengis.feature.simple.SimpleFeatureType adapt(RevFeatureType type) {
        return adapt(type.type());
    }

    public @NonNull org.opengis.feature.type.AttributeDescriptor adapt(
            org.locationtech.geogig.feature.PropertyDescriptor d) {

        AttributeType type = createAttributeType(d);
        Name name = adapt(d.getName());
        int minOccurs = d.getMinOccurs();
        int maxOccurs = d.getMaxOccurs();
        boolean isNillable = d.isNillable();
        Object defaultValue = null;
        if (d.isGeometryDescriptor()) {
            return FTF.createGeometryDescriptor((GeometryType) type, name, minOccurs, maxOccurs,
                    isNillable, defaultValue);
        }
        return FTF.createAttributeDescriptor(type, name, minOccurs, maxOccurs, isNillable,
                defaultValue);
    }

    private @NonNull AttributeType createAttributeType(PropertyDescriptor d) {
        Name name = adapt(d.getTypeName());
        Class<?> binding = d.getBinding();
        boolean isIdentifiable = false;
        boolean isAbstract = false;
        List<Filter> restrictions = null;
        AttributeType superType = null;
        InternationalString description = null;
        if (d.isGeometryDescriptor()) {
            CoordinateReferenceSystem crs = adapt(d.getCoordinateReferenceSystem());
            return FTF.createGeometryType(name, binding, crs, isIdentifiable, isAbstract,
                    restrictions, superType, description);
        }
        return FTF.createAttributeType(name, binding, isIdentifiable, isAbstract, restrictions,
                superType, description);
    }

    public @NonNull org.locationtech.geogig.feature.PropertyDescriptor adapt(
            @NonNull org.opengis.feature.type.PropertyDescriptor d) {

        PropertyType type = d.getType();

        PropertyDescriptorBuilder builder = PropertyDescriptor.builder();
        builder.name(adapt(d.getName())).minOccurs(d.getMinOccurs()).maxOccurs(d.getMaxOccurs())
                .nillable(d.isNillable()).typeName(adapt(type.getName()))
                .binding(type.getBinding());

        org.locationtech.geogig.crs.CoordinateReferenceSystem crs = null;
        if (d instanceof GeometryDescriptor) {
            crs = adapt(((GeometryDescriptor) d).getCoordinateReferenceSystem());
        }
        builder.coordinateReferenceSystem(crs);
        return builder.build();
    }
}
