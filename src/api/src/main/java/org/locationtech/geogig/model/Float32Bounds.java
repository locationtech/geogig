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


/**
 * This represents a bounds - much like a JTS Envelope.
 * However, to save space, this uses Float32 numbers instead of Float64.
 * The original Envelope will be contained by the Float32Bounds.
 *
 * NOTE:
 *     * this will usually (not always) larger than the original envelope
 *     * This bounds will always contain (or be equal to) the original envelope
 *     * for every Float32 number, there is an exact Float64 representation
 */
class Float32Bounds {

    boolean isNull = true;
    //defaults - xmin > xmax (no area)
    float xmin = Float.MIN_VALUE;
    float xmax = 0;
    float ymin = Float.MIN_VALUE;
    float ymax = 0;


    public Float32Bounds(Envelope doublePrecisionEnv) {
        if ((doublePrecisionEnv == null) || (doublePrecisionEnv.isNull())) {
            return; //done!
        }
        set(doublePrecisionEnv);
    }

    public Float32Bounds(double x, double y) {
        set(new Envelope(new Coordinate(x, y)));
    }

    public Float32Bounds(float xmin, float xmax, float ymin, float ymax) {
        this.xmin = xmin;
        this.xmax = xmax;
        this.ymin = ymin;
        this.ymax = ymax;
        this.isNull = (xmin > xmax);
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

    //To be careful, the resulting envelope is aligned with the float32 envelope!
    public void expand(Envelope env) {
        if (isNull)
            return;
        env.expandToInclude(xmin, ymin);
        env.expandToInclude(xmax, ymax);
        Float32Bounds newEnv = new Float32Bounds(env);
        Envelope float32Version = newEnv.asEnvelope();
        env.init(float32Version);
    }

    public boolean isNull() {
        return isNull;
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

