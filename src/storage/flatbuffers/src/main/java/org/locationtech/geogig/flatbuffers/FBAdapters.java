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
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.crs.DefaultEngineeringCRS;
import org.locationtech.geogig.flatbuffers.generated.SHA;
import org.locationtech.geogig.flatbuffers.generated.SimpleAttributeDescriptor;
import org.locationtech.geogig.flatbuffers.generated.SimpleFeatureType;
import org.locationtech.geogig.flatbuffers.generated.values.Bounds;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.FeatureTypeFactory;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;

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

    public static FeatureType toFeatureType(@NonNull SimpleFeatureType t) {
        SimpleFeatureTypeBuilder b = new SimpleFeatureTypeBuilder(ftfactory);
        b.setName(t.name());
        final @Nullable String defaultGeometryName = t.defaultGeometryName();
        b.setDefaultGeometry(defaultGeometryName);

        final int size = t.attributesLength();
        for (int i = 0; i < size; i++) {
            String name;
            Class<?> binding;

            SimpleAttributeDescriptor descriptor = t.attributes(i);
            name = descriptor.name();
            int bindingOrdinal = descriptor.binding() & 0xFF;
            FieldType fieldType = FieldType.valueOf(bindingOrdinal);
            binding = fieldType.getBinding();
            final boolean geometric = descriptor.geometric();
            if (geometric) {
                String authorityCode = descriptor.crsAuthorityCode();
                String wkt = descriptor.crsWkt();
                CoordinateReferenceSystem coordSys;
                try {
                    if (authorityCode != null) {
                        coordSys = org.geotools.referencing.CRS.decode(authorityCode);
                    } else if (wkt != null) {
                        coordSys = org.geotools.referencing.CRS.parseWKT(wkt);
                    } else {
                        coordSys = DefaultEngineeringCRS.CARTESIAN_2D;
                    }
                } catch (FactoryException e) {
                    throw new RuntimeException(e);
                }
                b.add(name, binding, coordSys);
            } else {
                b.add(name, binding);
            }
        }
        return b.buildFeatureType();
    }
}
