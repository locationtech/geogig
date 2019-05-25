package org.locationtech.geogig.feature;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.feature.FeatureType.FeatureTypeBuilder;
import org.locationtech.geogig.feature.PropertyDescriptor.PropertyDescriptorBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import lombok.NonNull;

public class FeatureTypes {

    public static @NonNull FeatureType createType(@NonNull String name,
            @NonNull String... attrDefs) {

        FeatureTypeBuilder builder = FeatureType.builder().name(parseName(name));
        List<PropertyDescriptor> descriptors = Arrays.asList(attrDefs).stream()
                .map(FeatureTypes::createDescriptor).collect(Collectors.toList());
        builder.descriptors(descriptors);
        return builder.build();
    }

    /**
     * Format:
     * <ul>
     * <li>{@code <descriptor> = <name>:<type>[:<property>[,<property>]+]}
     * <li>{@code <name> = [<namespaceuri>#]<string literal>
     * 
    <li>{@code <namespaceurl> = <string literal>
     * <li>{@code <property> = <property name>:<property value>}
     * <li>{@code <property name> = "nillable" | "srid" | "minOccurs" | "maxOccurs"}
     * <li>{@code <property value> = <string literal>}
     * </ul>
     */
    public static @NonNull PropertyDescriptor createDescriptor(@NonNull String attrDef) {
        String[] parts = attrDef.split(":");
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("illegal attribute descriptor def " + attrDef);
        }
        PropertyDescriptorBuilder builder = PropertyDescriptor.builder();
        Map<String, String> props = Collections.emptyMap();
        if (parts.length == 3) {
            Stream<String> stream = Arrays.asList(parts[2].split(",")).stream();
            props = stream.collect(Collectors.toMap(s -> s.substring(0, s.indexOf('=')),
                    s -> s.substring(s.indexOf('=') + 1)));

        }

        Name descriptorName = parseName(parts[0]);
        Class<?> binding = toBinding(parts[1]);
        CoordinateReferenceSystem crs = toCrs(props.get("srid"));
        int maxOccurs = Integer.valueOf(props.getOrDefault("maxOccurs", "1"));
        int minOccurs = Integer.valueOf(props.getOrDefault("minOccurs", "0"));
        boolean nillable = Boolean.valueOf(props.getOrDefault("nillable", "true"));

        builder.name(descriptorName);
        builder.typeName(descriptorName);
        builder.binding(binding);
        builder.coordinateReferenceSystem(crs);
        builder.maxOccurs(maxOccurs);
        builder.minOccurs(minOccurs);
        builder.nillable(nillable);

        return builder.build();
    }

    private static Name parseName(String name) {
        Name descriptorName;
        String namespace = null;
        if (name.contains("#")) {
            namespace = name.substring(0, name.indexOf('#'));
            name = name.substring(name.indexOf('#') + 1);
        }
        descriptorName = new Name(namespace, name);
        return descriptorName;
    }

    private static @Nullable CoordinateReferenceSystem toCrs(@Nullable String srid) {
        CoordinateReferenceSystem crs = null;
        if (srid != null) {
            Integer id = Integer.parseInt(srid);
            String crsId = "EPSG:" + id;
            CoordinateReferenceSystem.builder().srsIdentifier(crsId);
        }
        return crs;
    }

    private static @NonNull Class<?> toBinding(@NonNull String s) {
        Class<?> binding = typeMap.get(s.toLowerCase());
        if (binding == null) {
            try {
                binding = Class.forName(s);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return binding;
    }

    static Map<String, Class<?>> typeMap = new HashMap<>();

    static {
        typeMap.put("string", String.class);
        typeMap.put("int", Integer.class);
        typeMap.put("integer", Integer.class);
        typeMap.put("double", Double.class);
        typeMap.put("float", Float.class);
        typeMap.put("boolean", Boolean.class);
        typeMap.put("uuid", UUID.class);
        typeMap.put("geometry", Geometry.class);
        typeMap.put("point", Point.class);
        typeMap.put("linestring", LineString.class);
        typeMap.put("polygon", Polygon.class);
        typeMap.put("multipoint", MultiPoint.class);
        typeMap.put("multilineString", MultiLineString.class);
        typeMap.put("multipolygon", MultiPolygon.class);
        typeMap.put("geometrycollection", GeometryCollection.class);
        typeMap.put("date", Date.class);
    }
}
