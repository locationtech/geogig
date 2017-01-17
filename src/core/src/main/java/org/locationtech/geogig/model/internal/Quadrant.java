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

import com.google.common.annotations.VisibleForTesting;
import com.vividsolutions.jts.geom.Envelope;

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
    public void slice(Envelope parent, Envelope target) {

        double w = parent.getWidth() / 2.0;
        double h = parent.getHeight() / 2.0;

        double x1 = parent.getMinX() + offsetX * w;
        double x2 = x1 + w;
        double y1 = parent.getMinY() + offsetY * h;
        double y2 = y1 + h;

        target.init(x1, x2, y1, y2);
    }
}