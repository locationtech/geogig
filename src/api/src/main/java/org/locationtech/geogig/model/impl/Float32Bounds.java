/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

/**
 * This represents a bounds - much like a JTS Envelope. However, to save space, this uses Float32
 * numbers instead of Float64. The original Envelope will be contained by the Float32Bounds.
 *
 * NOTE: * this will usually (not always) larger than the original envelope * This bounds will
 * always contain (or be equal to) the original envelope * for every Float32 number, there is an
 * exact Float64 representation
 */
public class Float32Bounds {

    /**
     * The "null object" to represent an empty node
     * 
     * @see #valueOf(Envelope)
     */
    private static final Float32Bounds EMPTY = new Float32Bounds(new Envelope());

    // defaults - xmin > xmax (no area)
    float xmin = Float.MIN_VALUE;

    float xmax = 0;

    float ymin = Float.MIN_VALUE;

    float ymax = 0;

    private Float32Bounds(Envelope doublePrecisionEnv) {
        if ((doublePrecisionEnv == null) || (doublePrecisionEnv.isNull())) {
            return; // done!
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
    }

    private void set(Envelope doublePrecisionEnv) {
        if ((doublePrecisionEnv == null) || (doublePrecisionEnv.isNull())) {
            xmin = 0;
            xmax = -1;
            ymin = 0;
            ymax = -1;
            return;
        }

        // convert to float32, but ensure that the new bounds contain the old bounds
        // NOTE: every float32 can be exactly expressed as a double

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
        if (isNull())
            return new Envelope();
        return new Envelope(xmin, xmax, ymin, ymax);
    }

    public boolean intersects(Envelope env) {
        if (isNull() || env.isNull()) {
            return false;
        }
        // make the intersects check here matching the logic in Envelope and avoid creating lots of
        // Envelope objects since this method is going to be called for each Node in a tree
        // traversal
        return !(env.getMinX() > xmax || //
                env.getMaxX() < xmin || //
                env.getMinY() > ymax || //
                env.getMaxY() < ymin);
    }

    // To be careful, the resulting envelope is aligned with the float32 envelope!
    public void expand(Envelope env) {
        if (isNull())
            return;
        env.expandToInclude(xmin, ymin);
        env.expandToInclude(xmax, ymax);
        Float32Bounds newEnv = Float32Bounds.valueOf(env);
        Envelope float32Version = newEnv.asEnvelope();
        env.init(float32Version);
    }

    public boolean isNull() {
        return xmin > xmax;
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
        if (obj == this) {
            return true;
        }
        Float32Bounds other = (Float32Bounds) obj;
        if (other.isNull() && this.isNull())
            return true;

        if (other.isNull() != this.isNull())
            return false;

        return other.xmin == this.xmin && other.ymin == this.ymin && other.xmax == this.xmax
                && other.ymax == this.ymax;

    }

    @Override
    public int hashCode() {
        if (isNull())
            return 1;
        return Float.floatToRawIntBits(xmin) ^ Float.floatToRawIntBits(ymin)
                ^ Float.floatToRawIntBits(xmax) ^ Float.floatToRawIntBits(ymax);
    }

    /**
     * Factory method, returns an empty Float32Bounds if {@code bounds} is {@code null} or
     * {@link Envelope#isNull() empty}, otherwise a Float32Bounds that's guaranteed to contain
     * {@code bounds}
     */
    public static Float32Bounds valueOf(@Nullable Envelope bounds) {
        return bounds == null || bounds.isNull() ? EMPTY : new Float32Bounds(bounds);
    }

    static Float32Bounds valueOf(float x1, float x2, float y1, float y2) {
        return new Float32Bounds(x1, x2, y1, y2);
    }
}
