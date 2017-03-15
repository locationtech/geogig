/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;


public class Float32Bounds {

    boolean isNull = true;
    float xmin, xmax, ymin, ymax;

    public Float32Bounds() {

    }

    public Float32Bounds(int[] serializedform) {
        set(serializedform);
    }


    public Float32Bounds(Envelope doublePrecisionEnv) {
        if ((doublePrecisionEnv == null) || (doublePrecisionEnv.isNull()))
            return; //done!
        set(doublePrecisionEnv);
    }

    public Float32Bounds(double x, double y) {
        set(new Envelope(new Coordinate(x, y)));
    }

    private void set(Envelope doublePrecisionEnv) {
        if ((doublePrecisionEnv == null) || (doublePrecisionEnv.isNull())) {
            isNull = true;
            xmin = 0;
            xmax = -1;
            ymin = 0;
            ymax = -1;
            return;
        }

        isNull = false;

        //convert to float32, but ensure that the new bounds contain the old bounds
        //NOTE: every float32 can be exactly expressed as a double

        xmin = (float) doublePrecisionEnv.getMinX();
        if (((double) xmin) > doublePrecisionEnv.getMinX()) {
            xmin = Math.nextAfter(xmin, Double.NEGATIVE_INFINITY);
        }

        ymin = (float) doublePrecisionEnv.getMinY();
        if (((double) ymin) > doublePrecisionEnv.getMinY()) {
            ymin = Math.nextAfter(ymin, Double.NEGATIVE_INFINITY);
        }

        xmax = (float) doublePrecisionEnv.getMaxX();
        if (((double) xmax) < doublePrecisionEnv.getMaxX()) {
            xmax = Math.nextAfter(xmax, Double.POSITIVE_INFINITY);
        }

        ymax = (float) doublePrecisionEnv.getMaxY();
        if (((double) ymax) < doublePrecisionEnv.getMaxY()) {
            ymax = Math.nextAfter(ymax, Double.POSITIVE_INFINITY);
        }
    }


    public Envelope asEnvelope() {
        if (isNull)
            return new Envelope();
        return new Envelope(xmin, xmax, ymin, ymax);
    }

    public boolean intersects(Envelope env) {
        if (env.isNull()) {
            return false;
        }
        return asEnvelope().intersects(env);
    }

    //This will likely return a double (NON-float32) envelope
    // Be careful using it!
    public void expand(Envelope env) {
        if (isNull)
            return;
        env.expandToInclude(xmin, ymin);
        env.expandToInclude(xmax, ymax);
    }

    public boolean isNull() {
        return isNull;
    }

    // serialized form is 4 ints - representing the bounding box
    // int[0] - direct representation of xmin (Float.floatToRawIntBits)
    // int[1] - offset (in raw int) between xmin and xmax
    // int[2] - direct representation of ymin (Float.floatToRawIntBits)
    // int[3]-  offset (in raw int) between ymin and ymax
    //
    // we use the offset so that varint representation can more effectively compress
    public int[] toSerializedForm() {
        int[] result = new int[4];

        if (isNull) {
            // xmin,ymin=0   xmax,ymax=-1
            result[0] = Float.floatToRawIntBits(0);
            result[1] = Float.floatToRawIntBits(-1) - Float.floatToRawIntBits(0);
            result[2] = Float.floatToRawIntBits(0);
            result[3] = Float.floatToRawIntBits(-1) - Float.floatToRawIntBits(0);
            return result;
        }

        result[0] = Float.floatToRawIntBits(xmin);
        result[1] = Float.floatToRawIntBits(xmax) - Float.floatToRawIntBits(xmin);
        result[2] = Float.floatToRawIntBits(ymin);
        result[3] = Float.floatToRawIntBits(ymax) - Float.floatToRawIntBits(ymin);
        return result;
    }

    private void set(int[] serializedform) {
        xmin = Float.intBitsToFloat(serializedform[0]);
        xmax = Float.intBitsToFloat(serializedform[0] + serializedform[1]);
        ymin = Float.intBitsToFloat(serializedform[2]);
        ymax = Float.intBitsToFloat(serializedform[2] + serializedform[3]);
        isNull = xmin > xmax;
    }

    @Override
    public String toString() {
        return "[" + xmin + "," + xmax + "," + ymin + "," + ymax + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Float32Bounds)) {
            return false;
        }
        Float32Bounds other = (Float32Bounds) obj;
        if (other.isNull && this.isNull)
            return true;

        if (other.isNull != this.isNull)
            return false;

        return other.xmin == this.xmin &&
                other.ymin == this.ymin &&
                other.xmax == this.xmax &&
                other.ymax == this.ymax;

    }

    @Override
    public int hashCode() {
        if (isNull)
            return 1;
        return Float.floatToRawIntBits(xmin) ^ Float.floatToRawIntBits(ymin) ^ Float.floatToRawIntBits(xmax) ^ Float.floatToRawIntBits(ymax);
    }
}

