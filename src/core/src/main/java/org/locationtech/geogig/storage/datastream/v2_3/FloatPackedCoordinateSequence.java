/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_3;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;

/**
 * Seralized form is a int[][] serializedForm[0] -- x coords serializedForm[1] -- y coords
 *
 * serializedForm[0][0] - int representation of first X (Float.intBitsToFloat) serializedForm[0][1]
 * - 2nd X. Float.intBitsToFloat(serializedForm[0][0] + serializedForm[0][1])
 *
 * The ordinate list is a delta list on the Int32 form of the Float ordinate. This allows for exact
 * representation as well as good VarInt encoding.
 */
public class FloatPackedCoordinateSequence extends PackedCoordinateSequence.Float {
    private static final long serialVersionUID = -681428525580890229L;

    public static final CoordinateSequence EMPTY_2D = new FloatPackedCoordinateSequence(2, 0);

    public FloatPackedCoordinateSequence(final int dimensions, List<Coordinate> coords) {
        super(coords.toArray(new Coordinate[coords.size()]), dimensions);
    }

    public FloatPackedCoordinateSequence(final int dimensions, final int initialSize) {
        super(initialSize, dimensions, 0);
    }

    public FloatPackedCoordinateSequence(int[][] serializedForm) {
        super(deserializeCoords(serializedForm), serializedForm.length, 0);
    }

    public int[][] toSerializedForm() {
        int dims = this.getDimension();
        boolean hasZ = dims > 2;
        int nCoords = size();
        int[] Xs = new int[nCoords];
        int[] Ys = new int[nCoords];
        int[] Zs = null;
        if (hasZ)
            Zs = new int[nCoords];

        int X = 0;
        int Y = 0;
        int Z = 0;

        float[] allOrdinates = getRawCoordinates();

        for (int t = 0; t < nCoords; t++) {
            int currentX = java.lang.Float.floatToRawIntBits(allOrdinates[t * dims]);
            int currentY = java.lang.Float.floatToRawIntBits(allOrdinates[t * dims + 1]);
            Xs[t] = currentX - X;
            Ys[t] = currentY - Y;

            X = currentX;
            Y = currentY;

            if (hasZ) {
                int currentZ = java.lang.Float.floatToRawIntBits(allOrdinates[t * dims + 1]);
                Zs[t] = currentZ - Z;
                Z = currentZ;
            }
        }
        if (hasZ) {
            return new int[][] { Xs, Ys, Zs };
        }
        return new int[][] { Xs, Ys };
    }

    private static float[] deserializeCoords(int[][] serializedForm) {
        int nCoords = serializedForm[0].length;
        int dims = serializedForm.length;
        boolean hasZ = dims > 2;
        float[] coordBuff = new float[nCoords * 2];
        if (nCoords == 0)
            return coordBuff; // empty

        int X = 0;
        int Y = 0;
        for (int t = 0; t < nCoords; t++) {
            X += serializedForm[0][t];
            Y += serializedForm[1][t];
            coordBuff[t * dims] = java.lang.Float.intBitsToFloat(X);
            coordBuff[t * dims + 1] = java.lang.Float.intBitsToFloat(Y);
            if (hasZ) {
                coordBuff[t * dims + 2] = java.lang.Float.intBitsToFloat(Y);
            }
        }
        return coordBuff;
    }

}
