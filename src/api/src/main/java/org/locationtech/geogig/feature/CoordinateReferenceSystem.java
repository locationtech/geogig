package org.locationtech.geogig.feature;

import lombok.Builder;
import lombok.Value;

public @Value @Builder class CoordinateReferenceSystem {

    public static final CoordinateReferenceSystem NULL = new CoordinateReferenceSystem(
            "urn:ogc:def:crs:EPSG::0", null);

    private String srsIdentifier;

    private String WKT;

    public boolean isNull() {
        return NULL.srsIdentifier.equals(this.srsIdentifier);
    }
}
