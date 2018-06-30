 /* Copyright (c) 2018 Boundless and others.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Distribution License v1.0
  * which accompanies this distribution, and is available at
  * https://www.eclipse.org/org/documents/edl-v10.html
  *
  * Contributors:
  * David Blasby (Boundless) - initial implementation
  */

 package org.locationtech.geogig.model;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

//simple class that clones a geometry, and resets the CoordinateSequences  to
// CoordinateArraySequences
class GeometryCloner {

        static GeometryFactory gf = new GeometryFactory();

        public static Geometry clone(Geometry g) {
                return gf.createGeometry(g);
        }
}
