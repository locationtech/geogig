package org.locationtech.geogig.geotools.adapt;

import static org.geotools.referencing.crs.DefaultGeographicCRS.WGS84;

import java.util.NoSuchElementException;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.feature.NameImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.AbstractIdentifiedObject;
import org.geotools.referencing.CRS;
import org.geotools.referencing.wkt.Formattable;
import org.locationtech.geogig.feature.Name;
import org.locationtech.jts.geom.Envelope;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import lombok.NonNull;

class BaseAdapter {

    public Envelope adapt(BoundingBox opengisBbox) {
        if (opengisBbox == null) {
            return null;
        }
        return new Envelope(opengisBbox.getMinX(), opengisBbox.getMaxX(), opengisBbox.getMinY(),
                opengisBbox.getMaxY());
    }

    public ReferencedEnvelope adapt(CoordinateReferenceSystem ogcCrs, Envelope env) {
        if (env == null) {
            return null;
        }
        ReferencedEnvelope envelope = new ReferencedEnvelope(ogcCrs);
        envelope.init(env);
        return envelope;
    }

    public @NonNull org.opengis.feature.type.Name adapt(
            @NonNull org.locationtech.geogig.feature.Name name) {
        return new NameImpl(name.getNamespaceURI(), name.getLocalPart());
    }

    public @NonNull org.locationtech.geogig.feature.Name adapt(
            @NonNull org.opengis.feature.type.Name name) {
        return Name.valueOf(name.getNamespaceURI(), name.getLocalPart());
    }

    public @Nullable org.opengis.referencing.crs.CoordinateReferenceSystem adapt(
            org.locationtech.geogig.crs.CoordinateReferenceSystem gigCrs) {
        if (gigCrs == null) {
            return null;
        }
        CoordinateReferenceSystem opengisCrs = null;
        if (gigCrs.isNull()) {
            return null;
        }
        if (gigCrs.getSrsIdentifier() != null) {
            try {
                opengisCrs = CRS.decode(gigCrs.getSrsIdentifier(), true);
            } catch (FactoryException e) {
                throw new IllegalArgumentException(e);
            }
        }
        if (opengisCrs == null && gigCrs.getWKT() != null) {
            try {
                opengisCrs = CRS.parseWKT(gigCrs.getWKT());
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
    public @NonNull org.locationtech.geogig.crs.CoordinateReferenceSystem adapt(
            @Nullable org.opengis.referencing.crs.CoordinateReferenceSystem gtCrs) {

        if (gtCrs == null) {
            return null;
        }
        String srsName = null;
        String wkt = null;
        final boolean compareMetadata = false;
        if (gtCrs instanceof AbstractIdentifiedObject
                && WGS84.equals((AbstractIdentifiedObject) gtCrs, compareMetadata)) {
            srsName = "EPSG:4326";
        } else {
            srsName = CRS.toSRS(gtCrs);
            if (srsName == null) {
                // fall back to WKT
                if (gtCrs instanceof Formattable) {
                    wkt = ((Formattable) gtCrs).toWKT(Formattable.SINGLE_LINE);
                } else {
                    wkt = gtCrs.toWKT();
                }
            }
        }
        org.locationtech.geogig.crs.CoordinateReferenceSystem crs;
        if (srsName == null) {
            crs = org.locationtech.geogig.crs.CRS.fromWKT(wkt);
        } else {
            try {
                crs = org.locationtech.geogig.crs.CRS.decode(srsName);
            } catch (NoSuchElementException e) {
                // fall back to WKT
                if (gtCrs instanceof Formattable) {
                    wkt = ((Formattable) gtCrs).toWKT(Formattable.SINGLE_LINE);
                } else {
                    wkt = gtCrs.toWKT();
                }
                crs = org.locationtech.geogig.crs.CRS.fromWKT(wkt);
            }
        }
        return crs;
    }

}
