package org.locationtech.geogig.geotools.adapt;

import static org.geotools.referencing.crs.DefaultGeographicCRS.WGS84;

import javax.annotation.Nullable;

import org.geotools.feature.NameImpl;
import org.geotools.referencing.AbstractIdentifiedObject;
import org.geotools.referencing.CRS;
import org.geotools.referencing.wkt.Formattable;
import org.locationtech.geogig.feature.Name;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import lombok.NonNull;

class BaseAdapter {
    public @NonNull org.opengis.feature.type.Name adapt(
            @NonNull org.locationtech.geogig.feature.Name name) {
        return new NameImpl(name.getNamespaceURI(), name.getLocalPart());
    }

    public @NonNull org.locationtech.geogig.feature.Name adapt(
            @NonNull org.opengis.feature.type.Name name) {
        return new Name(name.getNamespaceURI(), name.getLocalPart());
    }

    public @Nullable org.opengis.referencing.crs.CoordinateReferenceSystem adapt(
            @NonNull org.locationtech.geogig.feature.CoordinateReferenceSystem crs) {

        CoordinateReferenceSystem opengisCrs = null;

        if (crs.getSrsIdentifier() != null) {
            try {
                opengisCrs = CRS.decode(crs.getSrsIdentifier(), true);
            } catch (FactoryException e) {
                throw new IllegalArgumentException(e);
            }
        }
        if (opengisCrs == null && crs.getWKT() != null) {
            try {
                opengisCrs = CRS.parseWKT(crs.getWKT());
            } catch (FactoryException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return opengisCrs;
    }

    // GeoTools treats DefaultGeographic.WGS84 as a special case when calling the
    // CRS.toSRS() method, and that causes the parsed RevFeatureType to hash differently.
    // To compensate that, we replace any instance of it with a CRS built using the
    // EPSG:4326 code, which works consistently when storing it and later recovering it from
    // the database.
    public @NonNull org.locationtech.geogig.feature.CoordinateReferenceSystem adapt(
            @Nullable org.opengis.referencing.crs.CoordinateReferenceSystem crs) {

        String srsName = null;
        String wkt = null;
        if (crs != null) {
            final boolean compareMetadata = false;
            if (crs instanceof AbstractIdentifiedObject
                    && WGS84.equals((AbstractIdentifiedObject) crs, compareMetadata)) {
                srsName = "EPSG:4326";
            } else {
                srsName = CRS.toSRS(crs);
                if (srsName == null) {
                    // fall back to WKT
                    if (crs instanceof Formattable) {
                        wkt = ((Formattable) crs).toWKT(Formattable.SINGLE_LINE);
                    } else {
                        wkt = crs.toWKT();
                    }
                }
            }

        }
        return org.locationtech.geogig.feature.CoordinateReferenceSystem.builder()
                .srsIdentifier(srsName).WKT(wkt).build();

    }

}
