package org.locationtech.geogig.crs;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
public @Value class CoordinateReferenceSystem {

    public static final CoordinateReferenceSystem NULL = new CoordinateReferenceSystem(
            "urn:ogc:def:crs:EPSG::0", null);

    public static final CoordinateReferenceSystem WGS84 = new CoordinateReferenceSystem("EPSG:4326",
            null);

    private String srsIdentifier;

    private String WKT;

    public boolean isNull() {
        return NULL.srsIdentifier.equals(this.srsIdentifier);
    }
}
