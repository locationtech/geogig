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

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.crs.CRS;
import org.locationtech.geogig.crs.CoordinateReferenceSystem;

import lombok.NonNull;

public class CrsTextSerializer {

    public static @NonNull String serialize(@Nullable CoordinateReferenceSystem crs) {
        if (crs == null) {
            return org.locationtech.geogig.crs.CoordinateReferenceSystem.NULL.getSrsIdentifier();
        }
        if (crs.getSrsIdentifier() != null) {
            return crs.getSrsIdentifier();
        }
        return crs.getWKT().replaceAll("\n", "");
    }

    public static @Nullable CoordinateReferenceSystem deserialize(String crsText) {
        boolean crsCode = crsText.startsWith("EPSG") || crsText.startsWith("urn:ogc:def:crs:EPSG");
        CoordinateReferenceSystem crs;
        if (crsCode) {
            crs = CRS.decode(crsText);
        } else {
            crs = CRS.fromWKT(crsText);
        }
        return crs.isNull() ? null : crs;
    }

}
