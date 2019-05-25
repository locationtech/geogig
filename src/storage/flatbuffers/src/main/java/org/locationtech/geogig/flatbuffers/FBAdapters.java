/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.flatbuffers;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.crs.CRS;
import org.locationtech.geogig.crs.CoordinateReferenceSystem;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.FeatureType.FeatureTypeBuilder;
import org.locationtech.geogig.feature.Name;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.flatbuffers.generated.v1.QualifiedName;
import org.locationtech.geogig.flatbuffers.generated.v1.SHA;
import org.locationtech.geogig.flatbuffers.generated.v1.SimpleAttributeDescriptor;
import org.locationtech.geogig.flatbuffers.generated.v1.values.Bounds;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.jts.geom.Envelope;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

final @UtilityClass class FBAdapters {

    public static ObjectId toId(@NonNull SHA id) {
        return ObjectId.create(id.h1(), id.h2(), id.h3());
    }

    public static Optional<Envelope> toEnvelopeOpt(Bounds bounds) {
        return Optional.ofNullable(isNull(bounds) ? null
                : new Envelope(bounds.x1(), bounds.x2(), bounds.y1(), bounds.y2()));
    }

    public static void expandEnv(Envelope env, Bounds bounds) {
        if (!isNull(bounds)) {
            env.expandToInclude(bounds.x1(), bounds.y1());
            env.expandToInclude(bounds.x2(), bounds.y2());
        }
    }

    public static boolean isNull(Bounds bounds) {
        return bounds == null || bounds.x1() > bounds.x2();
    }

    public static boolean intersects(Bounds bounds, Envelope env) {
        if (env.isNull() || isNull(bounds)) {
            return false;
        }
        // make the intersects check here matching the logic in Envelope and avoid creating lots of
        // Envelope objects since this method is going to be called for each Node in a tree
        // traversal
        return !(env.getMinX() > bounds.x2() || //
                env.getMaxX() < bounds.x1() || //
                env.getMinY() > bounds.y2() || //
                env.getMaxY() < bounds.y1());
    }

    public static org.locationtech.geogig.feature.FeatureType toFeatureType(
            @NonNull org.locationtech.geogig.flatbuffers.generated.v1.SimpleFeatureType t) {

        FeatureTypeBuilder featureTypeBuilder = FeatureType.builder();
        featureTypeBuilder.name(toName(t.name()));
        final @Nullable String defaultGeometryName = t.defaultGeometryName();
        // TODO: implement default geometry setting in geogig's feature model
        // featureTypeBuilder.setDefaultGeometry(defaultGeometryName);

        final int size = t.attributesLength();
        for (int i = 0; i < size; i++) {
            SimpleAttributeDescriptor descriptor = t.attributes(i);
            final org.locationtech.geogig.flatbuffers.generated.v1.AttributeType type = descriptor
                    .type();
            final int minOccurs = descriptor.minOccurs();
            final int maxOccurs = descriptor.maxOccurs();
            final boolean nillable = descriptor.nillable();

            final boolean geometric = type.geometric();
            final int bindingOrdinal = type.binding() & 0xFF;
            final FieldType fieldType = FieldType.valueOf(bindingOrdinal);
            final Class<?> binding = fieldType.getBinding();

            final Name descriptorName = toName(descriptor.name());
            final Name attributeTypeName = toName(type.name());
            final @Nullable CoordinateReferenceSystem crs = geometric ? resolveCrs(type) : null;

            final PropertyDescriptor attributeDescriptor = PropertyDescriptor.builder()
                    .name(descriptorName).typeName(attributeTypeName).binding(binding)
                    .minOccurs(minOccurs).maxOccurs(maxOccurs).nillable(nillable)
                    .coordinateReferenceSystem(crs).build();
            featureTypeBuilder.add(attributeDescriptor);
        }
        return featureTypeBuilder.build();
    }

    private static CoordinateReferenceSystem resolveCrs(
            final org.locationtech.geogig.flatbuffers.generated.v1.AttributeType type) {
        String authorityCode = type.crsAuthorityCode();
        String wkt = type.crsWkt();
        CoordinateReferenceSystem coordSys = null;
        if (authorityCode != null) {
            try {
                coordSys = CRS.decode(authorityCode);
            } catch (NoSuchElementException unknown) {
                unknown.printStackTrace();
            }
        }
        if (coordSys == null && wkt != null) {
            coordSys = CRS.fromWKT(wkt);
        }

        return coordSys;
    }

    public static org.locationtech.geogig.feature.Name toName(@NonNull QualifiedName qname) {
        String namespaceUri = qname.namespaceUri();
        String localName = qname.localName();
        return Name.valueOf(namespaceUri, localName);
    }
}
