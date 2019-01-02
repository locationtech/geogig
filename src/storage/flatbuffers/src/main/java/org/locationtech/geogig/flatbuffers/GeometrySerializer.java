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

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.flatbuffers.generated.v1.values.ENCODEDGEOMETRY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.FLATGEOMETRY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.GEOMETRY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.WKBGEOMETRY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;

import com.google.flatbuffers.FlatBufferBuilder;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

final @UtilityClass class GeometrySerializer {

    private static final GeometryFactory defaultGF = new GeometryFactory(
            new PackedCoordinateSequenceFactory(PackedCoordinateSequenceFactory.DOUBLE));

    public static int encode(@NonNull Geometry geom, @NonNull FlatBufferBuilder builder) {
        final byte geomType;
        final int geomOffset;
        if (geom instanceof GeometryCollection || geom instanceof Polygon) {
            geomType = ENCODEDGEOMETRY.WKBGEOMETRY;
            geomOffset = GeometryWKBSerializer.encode(geom, builder);
        } else {
            geomType = ENCODEDGEOMETRY.FLATGEOMETRY;
            geomOffset = GeometryFlatSerializer.encode(geom, builder);
        }
        return GEOMETRY.createGEOMETRY(builder, geomType, geomOffset);
    }

    public static @Nullable Geometry decode(@NonNull GEOMETRY gval, @Nullable GeometryFactory gf) {
        final byte gtype = gval.valueType();
        final GeometryFactory geomFac = gf == null ? defaultGF : gf;
        if (ENCODEDGEOMETRY.WKBGEOMETRY == gtype) {
            WKBGEOMETRY wkb = (WKBGEOMETRY) gval.value(new WKBGEOMETRY());
            return GeometryWKBSerializer.decode(wkb, geomFac);
        }
        if (ENCODEDGEOMETRY.FLATGEOMETRY == gtype) {
            FLATGEOMETRY fg = (FLATGEOMETRY) gval.value(new FLATGEOMETRY());
            return GeometryFlatSerializer.decode(fg, geomFac);
        }
        throw new IllegalStateException("Uknown ENCODEDGEOMETRY enum value: " + gtype);
    }
}
