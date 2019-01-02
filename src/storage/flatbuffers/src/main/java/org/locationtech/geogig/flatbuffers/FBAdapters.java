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

import javax.annotation.Nullable;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.geogig.flatbuffers.generated.v1.QualifiedName;
import org.locationtech.geogig.flatbuffers.generated.v1.SHA;
import org.locationtech.geogig.flatbuffers.generated.v1.SimpleAttributeDescriptor;
import org.locationtech.geogig.flatbuffers.generated.v1.values.Bounds;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.FeatureTypeFactory;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

final @UtilityClass class FBAdapters {

    private static final FeatureTypeFactory ftfactory = CommonFactoryFinder
            .getFeatureTypeFactory(null);

    public static ObjectId toId(@NonNull SHA id) {
        return ObjectId.create(id.h1(), id.h2(), id.h3());
    }

    public static Optional<Envelope> toEnvelopeOpt(Bounds bounds) {
        return Optional.fromNullable(isNull(bounds) ? null
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

    public static org.opengis.feature.type.FeatureType toFeatureType(
            @NonNull org.locationtech.geogig.flatbuffers.generated.v1.SimpleFeatureType t) {

        SimpleFeatureTypeBuilder featureTypeBuilder = new SimpleFeatureTypeBuilder(ftfactory);
        featureTypeBuilder.setName(toName(t.name()));
        final @Nullable String defaultGeometryName = t.defaultGeometryName();
        featureTypeBuilder.setDefaultGeometry(defaultGeometryName);

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
            final AttributeDescriptor attributeDescriptor;
            if (geometric) {
                final @Nullable CoordinateReferenceSystem crs = resolveCrs(type);
                GeometryType attributeType = ftfactory.createGeometryType(attributeTypeName,
                        binding, crs, type.identified(), false, null, null, null);
                Object defaultValue = null;
                attributeDescriptor = ftfactory.createGeometryDescriptor(attributeType,
                        descriptorName, minOccurs, maxOccurs, nillable, defaultValue);
            } else {
                AttributeType attributeType = ftfactory.createAttributeType(attributeTypeName,
                        binding, type.identified(), false, null, null, null);
                Object defaultValue = null;
                attributeDescriptor = ftfactory.createAttributeDescriptor(attributeType,
                        descriptorName, minOccurs, maxOccurs, nillable, defaultValue);
            }
            featureTypeBuilder.add(attributeDescriptor);
        }
        return featureTypeBuilder.buildFeatureType();
    }

    private static CoordinateReferenceSystem resolveCrs(
            final org.locationtech.geogig.flatbuffers.generated.v1.AttributeType type) {
        String authorityCode = type.crsAuthorityCode();
        String wkt = type.crsWkt();
        CoordinateReferenceSystem coordSys;
        try {
            if (authorityCode != null) {
                coordSys = org.geotools.referencing.CRS.decode(authorityCode);
            } else if (wkt != null) {
                coordSys = org.geotools.referencing.CRS.parseWKT(wkt);
            } else {
                coordSys = null;
            }
        } catch (FactoryException e) {
            throw new RuntimeException(e);
        }
        return coordSys;
    }

    public static org.opengis.feature.type.Name toName(@NonNull QualifiedName qname) {
        String namespaceUri = qname.namespaceUri();
        String localName = qname.localName();
        if (Strings.isNullOrEmpty(namespaceUri)) {
            return new NameImpl(localName);
        }
        return new NameImpl(namespaceUri, localName);
    }
}
