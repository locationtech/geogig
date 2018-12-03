/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.internal.QuadTreeTestSupport;
import org.locationtech.jts.geom.Envelope;

public class QuadTreeBuilderTestPseudoMercator extends QuadTreeBuilderTest {

    @Override
    protected Envelope createMaxBounds() {
        return RevObjects.makePrecise(QuadTreeTestSupport.epsg3857Bounds());
    }

}
