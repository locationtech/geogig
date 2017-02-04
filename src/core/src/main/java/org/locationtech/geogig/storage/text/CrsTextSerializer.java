/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.text;

import org.geotools.referencing.CRS;
import org.geotools.referencing.CRS.AxisOrder;
import org.geotools.referencing.wkt.Formattable;
import org.locationtech.geogig.model.RevObjects;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class CrsTextSerializer {

    public static String serialize(CoordinateReferenceSystem crs) {
        String srsName;
        if (crs == null) {
            srsName = RevObjects.NULL_CRS_IDENTIFIER;
        } else {
            // use a flag to control whether the code is returned in EPSG: form instead of
            // urn:ogc:.. form irrespective of the org.geotools.referencing.forceXY System
            // property.
            final boolean longitudeFirst = CRS.getAxisOrder(crs, false) == AxisOrder.EAST_NORTH;
            boolean codeOnly = true;
            String crsCode = CRS.toSRS(crs, codeOnly);
            if (crsCode != null) {
                srsName = (longitudeFirst ? "EPSG:" : "urn:ogc:def:crs:EPSG::") + crsCode;
                // check that what we are writing is actually a valid EPSG code and we will
                // be able to decode it later. If not, we will use WKT instead
                try {
                    CRS.decode(srsName, longitudeFirst);
                } catch (NoSuchAuthorityCodeException e) {
                    srsName = null;
                } catch (FactoryException e) {
                    srsName = null;
                }
            } else {
                srsName = null;
            }
        }
        if (srsName != null) {
            return srsName;
        } else {
            String wkt;
            if (crs instanceof Formattable) {
                wkt = ((Formattable) crs).toWKT(Formattable.SINGLE_LINE);
            } else {
                wkt = crs.toWKT();
            }
            return wkt;
        }

    }

    public static CoordinateReferenceSystem deserialize(String crsText) {
        CoordinateReferenceSystem crs;
        boolean crsCode = crsText.startsWith("EPSG") || crsText.startsWith("urn:ogc:def:crs:EPSG");
        try {
            if (crsCode) {
                if (RevObjects.NULL_CRS_IDENTIFIER.equals(crsText)) {
                    crs = null;
                } else {
                    boolean forceLongitudeFirst = crsText.startsWith("EPSG:");
                    crs = CRS.decode(crsText, forceLongitudeFirst);
                }
            } else {
                crs = CRS.parseWKT(crsText);
            }
        } catch (FactoryException e) {
            throw new IllegalArgumentException("Cannot parse CRS definition: " + crsText);
        }
        return crs;
    }

}
