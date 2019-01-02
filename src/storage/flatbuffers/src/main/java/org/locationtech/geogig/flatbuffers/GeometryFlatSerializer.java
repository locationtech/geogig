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

import java.io.IOException;
import java.nio.ByteBuffer;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.flatbuffers.generated.v1.values.FLATGEOMETRY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.GeometryType;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.InStream;

import com.google.flatbuffers.FlatBufferBuilder;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

final @UtilityClass class GeometryFlatSerializer {

    public static int encode(@NonNull Geometry geom, @NonNull FlatBufferBuilder builder) {
        final byte type;
        switch (geom.getGeometryType()) {
        case "Point":
            type = GeometryType.Point;
            break;
        case "LineString":
            type = GeometryType.LineString;
            break;
        default:
            throw new IllegalArgumentException(geom.getGeometryType());
        }

        final int dimension = 2;
        final int numOrdinates = geom.getNumPoints() * dimension;
        FLATGEOMETRY.startOrdinatesVector(builder, numOrdinates);
        if (type == GeometryType.Point) {
            // don't even bother createing a coordseqfilter. Reverse order, see comment bellow
            builder.addDouble(((Point) geom).getY());
            builder.addDouble(((Point) geom).getX());
        } else {
            geom.apply(new CoordinateSequenceFilter() {
                public @Override boolean isGeometryChanged() {
                    return false;
                }

                public @Override boolean isDone() {
                    // return true right away, we only need the first call to filter(...)
                    return true;
                }

                // Write a coordinate sequence backwards as a single double array, which ends up
                // being in the correct order in flatbuffers, as it writes vectors bottom up
                public @Override void filter(final CoordinateSequence seq, final int i) {
                    final int size = seq.size();
                    for (int o = size - 1; o >= 0; o--) {
                        builder.addDouble(seq.getOrdinate(o, 1));// Y
                        builder.addDouble(seq.getOrdinate(o, 0));// then X
                    }
                }
            });
        }
        int ordinatesOffset = builder.endVector();

        return FLATGEOMETRY.createFLATGEOMETRY(builder, dimension, type, ordinatesOffset);
    }

    public static @Nullable Geometry decode(@NonNull FLATGEOMETRY fg,
            @NonNull GeometryFactory geomFac) {
        
        final int dimension = fg.dimension();
        final byte geometryType = fg.type();
        final int numOrdinates = fg.ordinatesLength();
        final int numCoordinates = numOrdinates / dimension;
        CoordinateSequence coordSeq = new FlatGeomCoordSequence(fg, dimension, numCoordinates);
        Geometry geom;
        switch (geometryType) {
        case GeometryType.Point:
            geom = geomFac.createPoint(coordSeq);
            break;
        case GeometryType.LineString:
            geom = geomFac.createLineString(coordSeq);
            break;
        default:
            throw new IllegalArgumentException(
                    "Unrecognized encoded GeometryType enum: " + geometryType);
        }
        return geom;
    }

    private static @RequiredArgsConstructor class ByteBufferInStream implements InStream {
        private final ByteBuffer bb;

        public @Override void read(byte[] buf) throws IOException {
            bb.get(buf);
        }
    }

    private static @RequiredArgsConstructor class FlatGeomCoordSequence
            implements CoordinateSequence {

        private final FLATGEOMETRY fg;

        private final int dimension;

        private final int numCoordinates;

        public @Override int getDimension() {
            return dimension;
        }

        public @Override Coordinate getCoordinate(int i) {
            return getCoordinateCopy(i);
        }

        public @Override Coordinate getCoordinateCopy(int i) {
            Coordinate c = new Coordinate();
            getCoordinate(i, c);
            return c;
        }

        public @Override void getCoordinate(int index, Coordinate coord) {
            coord.setX(getX(index));
            coord.setY(getY(index));
        }

        public @Override double getX(int index) {
            return getOrdinate(index, 0);
        }

        public @Override double getY(int index) {
            return getOrdinate(index, 1);
        }

        public @Override double getOrdinate(int index, int ordinateIndex) {
            int idx = index * dimension + ordinateIndex;
            return fg.ordinates(idx);
        }

        public @Override int size() {
            return numCoordinates;
        }

        public @Override void setOrdinate(int index, int ordinateIndex, double value) {
            throw new UnsupportedOperationException();
        }

        public @Override Coordinate[] toCoordinateArray() {
            int size = size();
            Coordinate[] coords = new Coordinate[size];
            for (int i = 0; i < size; i++) {
                coords[i] = getCoordinate(i);
            }
            return coords;
        }

        public @Override Envelope expandEnvelope(Envelope env) {
            for (int i = 0; i < size(); i++) {
                env.expandToInclude(getOrdinate(i, 0), getOrdinate(i, 1));
            }
            return env;
        }

        public @Override CoordinateSequence copy() {
            return new FlatGeomCoordSequence(fg, dimension, numCoordinates);
        }

        public @Override Object clone() {
            return copy();
        }
    }

}
