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
import org.locationtech.geogig.flatbuffers.generated.v1.values.WKBGEOMETRY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.InStream;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import com.google.flatbuffers.FlatBufferBuilder;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

final @UtilityClass class GeometryWKBSerializer {

    public static int encode(@NonNull Geometry geom, @NonNull FlatBufferBuilder builder) {
        byte[] data = new WKBWriter().write(geom);
        int valueVector = WKBGEOMETRY.createValueVector(builder, data);
        return WKBGEOMETRY.createWKBGEOMETRY(builder, valueVector);
    }

    public static @Nullable Geometry decode(@NonNull WKBGEOMETRY wkb,
            @NonNull GeometryFactory geomFac) {

        ByteBuffer bb = wkb.valueAsByteBuffer();
        WKBReader reader = new WKBReader(geomFac);
        try {
            return reader.read(new ByteBufferInStream(bb));
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static @RequiredArgsConstructor class ByteBufferInStream implements InStream {
        private final ByteBuffer bb;

        public @Override void read(byte[] buf) throws IOException {
            bb.get(buf);
        }
    }
}
