/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

import static org.locationtech.geogig.storage.datastream.Varint.readSignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.readUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeSignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarInt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.locationtech.geogig.storage.datastream.DataStreamValueSerializerV2.ValueSerializer;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.CoordinateSequenceFilter;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.impl.PackedCoordinateSequenceFactory;

/**
 * This is work in progress and not ready for production. It'll be a serialization format for JTS
 * geometries more compact than WKB, and ideally would support different precision models.
 */
class GeometrySerializer implements ValueSerializer {

    private static final int POINT = 0x01;

    private static final int LINESTRING = 0x02;

    private static final int POLYGON = 0x03;

    private static final int MULTIPOINT = 0x04;

    private static final int MULTILINESTRING = 0x05;

    private static final int MULTIPOLYGON = 0x06;

    private static final int GEOMETRYCOLLECTION = 0x07;

    private static final double FIXED_PRECISION_FACTOR = 1e7;

    private static final GeometryFactory GEOMFAC = new GeometryFactory(
            new PackedCoordinateSequenceFactory());

    private static abstract class GeometryEncoder {

        abstract void write(DataOutput out) throws IOException;

        abstract Geometry read(DataInput in) throws IOException;
    }

    private static GeometryEncoder[] ENCODERS = new GeometryEncoder[] {//

    };

    @Override
    public void write(Object obj, final DataOutput out) throws IOException {

        final Geometry geom = (Geometry) obj;
        final int geometryType = getGeometryType(geom);
        final int typeAndMasks = geometryType;

        writeUnsignedVarInt(typeAndMasks, out);
        geom.apply(new EncodingSequenceFilter(out, true));
    }

    @Override
    public Geometry read(DataInput in) throws IOException {

        final int typeAndMasks = readUnsignedVarInt(in);

        Geometry geom;
        if ((typeAndMasks & POINT) == POINT) {
            geom = GEOMFAC.createPoint(EncodingSequenceFilter.readCoordinate(in));
        } else if ((typeAndMasks & LINESTRING) == LINESTRING) {
            CoordinateSequence cs = EncodingSequenceFilter.read(in);
            geom = GEOMFAC.createLineString(cs);
        } else {
            throw new UnsupportedOperationException();
        }
        return geom;
    }

    private static final class EncodingSequenceFilter implements CoordinateSequenceFilter {

        private DataOutput out;

        private boolean writeLength;

        public EncodingSequenceFilter(DataOutput out, boolean writeLength) {
            this.out = out;
            this.writeLength = writeLength;
        }

        @Override
        public boolean isGeometryChanged() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public void filter(CoordinateSequence seq, int index) {
            double ordinate1 = seq.getOrdinate(index, 0);
            double ordinate2 = seq.getOrdinate(index, 1);
            int fixedO1 = toFixedPrecision(ordinate1);
            int fixedO2 = toFixedPrecision(ordinate2);
            try {
                if (writeLength) {
                    writeUnsignedVarInt(seq.size(), out);
                    writeLength = false;
                }
                writeSignedVarInt(fixedO1, out);
                writeSignedVarInt(fixedO2, out);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }

        public static CoordinateSequence read(DataInput in) throws IOException {
            final int len = readUnsignedVarInt(in);

            CoordinateSequence cs = GEOMFAC.getCoordinateSequenceFactory().create(len, 2);
            for (int i = 0; i < len; i++) {
                cs.setOrdinate(i, 0, toDoublePrecision(readSignedVarInt(in)));
                cs.setOrdinate(i, 1, toDoublePrecision(readSignedVarInt(in)));
            }
            return cs;
        }

        public static CoordinateSequence readCoordinate(DataInput in) throws IOException {
            CoordinateSequence cs = GEOMFAC.getCoordinateSequenceFactory().create(1, 2);

            cs.setOrdinate(0, 0, toDoublePrecision(readSignedVarInt(in)));
            cs.setOrdinate(0, 1, toDoublePrecision(readSignedVarInt(in)));

            return cs;
        }
    }

    private int getGeometryType(Geometry geom) {
        Preconditions.checkNotNull(geom, "null geometry");
        if (geom instanceof Point)
            return POINT;
        if (geom instanceof LineString)
            return LINESTRING;
        if (geom instanceof Polygon)
            return POLYGON;
        if (geom instanceof MultiPoint)
            return MULTIPOINT;
        if (geom instanceof MultiLineString)
            return MULTILINESTRING;
        if (geom instanceof MultiPolygon)
            return MULTIPOLYGON;
        if (geom instanceof GeometryCollection)
            return GEOMETRYCOLLECTION;
        throw new IllegalArgumentException("Unknown geometry type: " + geom.getClass());
    }

    /**
     * Converts the requested coordinate from double to fixed precision.
     */
    public static int toFixedPrecision(double ordinate) {
        int fixedPrecisionOrdinate = (int) Math.round(ordinate * FIXED_PRECISION_FACTOR);
        return fixedPrecisionOrdinate;
    }

    /**
     * Converts the requested coordinate from fixed to double precision.
     */
    public static double toDoublePrecision(int fixedPrecisionOrdinate) {
        double ordinate = fixedPrecisionOrdinate / FIXED_PRECISION_FACTOR;
        return ordinate;
    }
}
