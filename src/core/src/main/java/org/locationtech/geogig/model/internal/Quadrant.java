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

import com.vividsolutions.jts.geom.Envelope;

public enum Quadrant {
    SW(0, 0), NW(0, 1), NE(1, 1), SE(1, 0);

    private final int offsetX;

    private final int offsetY;

    Quadrant(int offsetX, int offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public int getBucketNumber() {
        int i = 0;
        for (Quadrant q : Quadrant.values()) {
            if (q.equals(this))
                return i;
            i++;
        }
        return -1;
    }

    public Envelope slice(Envelope parent) {

        double w = parent.getWidth() / 2.0;
        double h = parent.getHeight() / 2.0;

        double x1 = parent.getMinX() + offsetX * w;
        double x2 = x1 + w;
        double y1 = parent.getMinY() + offsetY * h;
        double y2 = y1 + h;

        return new Envelope(x1, x2, y1, y2);

    }
}