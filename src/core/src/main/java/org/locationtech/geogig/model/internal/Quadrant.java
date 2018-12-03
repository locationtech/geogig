/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import org.locationtech.geogig.model.RevObjects;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

import com.google.common.annotations.VisibleForTesting;

enum Quadrant {
    SW(0, 0), NW(0, 1), NE(1, 1), SE(1, 0);

    // there's some overhead in calling Quadrant.values() repeatedly so cache it
    static final Quadrant[] VALUES = values();

    private final int offsetX;

    private final int offsetY;

    Quadrant(int offsetX, int offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public int getBucketNumber() {
        return this.ordinal();
    }

    @VisibleForTesting
    Envelope slice(Envelope parent) {
        Envelope target = new Envelope();
        slice(parent, target);
        return target;
    }

    /**
     * Given a parent's quadrant envelope, computes the bounds of this quadrant and initializes the
     * {@code target} envelope with it.
     * 
     * @param parent the bounds of the parent quadrant
     * @param target output argument where to store the bounds of the child quadrant this
     *        {@code Quadrant} represents
     */
    public void slice(Envelope maxBounds, Envelope target) {
        final Coordinate centre = maxBounds.centre();

        double x1;
        double y1;
        double x2;
        double y2;
        if (0 == offsetX) {
            x1 = maxBounds.getMinX();
            x2 = centre.x;
        } else {
            x2 = maxBounds.getMaxX();
            x1 = centre.x;
        }
        if (0 == offsetY) {
            y1 = maxBounds.getMinY();
            y2 = centre.y;
        } else {
            y2 = maxBounds.getMaxY();
            y1 = centre.y;
        }

        target.init(x1, x2, y1, y2);
        Envelope precise = RevObjects.makePrecise(target);
        target.init(precise);
    }

    public static int findMaxDepth(Envelope maxBounds, final int absoluteMaxDepth) {
        // choose the quad that tends to the biggest abs value
        maxBounds = RevObjects.makePrecise(maxBounds);
        final Quadrant testQuad = findBiggestMagnitudeQuad(maxBounds);

        Envelope parent = new Envelope(maxBounds);
        Envelope child = new Envelope();
        Envelope float32Center = new Envelope();

        for (int d = 0; d < absoluteMaxDepth; d++) {
            testQuad.slice(parent, child);
            Coordinate center = child.centre();
            toFloat32(center, float32Center);
            if (!child.contains(float32Center)) {
                return d;
            }
            parent.init(child);
        }
        return absoluteMaxDepth;
    }

    /**
     * @param maxBounds
     * @return
     */
    @VisibleForTesting
    static Quadrant findBiggestMagnitudeQuad(Envelope maxBounds) {
        boolean west = Math.abs(maxBounds.getMinX()) > Math.abs(maxBounds.getMaxX());
        boolean south = Math.abs(maxBounds.getMinY()) > Math.abs(maxBounds.getMaxY());
        Quadrant q;
        if (west) {
            q = south ? SW : NW;
        } else {
            q = south ? SE : NE;
        }
        return q;
    }

    private static void toFloat32(Coordinate center, Envelope target) {
        float centerX = (float) center.x;
        float centerY = (float) center.y;

        float xmin = Math.nextAfter(centerX, Double.NEGATIVE_INFINITY);
        float ymin = Math.nextAfter(centerY, Double.NEGATIVE_INFINITY);
        float xmax = Math.nextAfter(centerX, Double.POSITIVE_INFINITY);
        float ymax = Math.nextAfter(centerY, Double.POSITIVE_INFINITY);

        target.init(xmin, xmax, ymin, ymax);
    }
}