package org.locationtech.geogig.crs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.referencing.wkt.Formattable;
import org.junit.Test;
import org.locationtech.jts.geom.Envelope;
import org.opengis.metadata.extent.Extent;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.metadata.extent.GeographicExtent;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.CoordinateOperationFactory;
import org.opengis.referencing.operation.OperationNotFoundException;
import org.opengis.referencing.operation.TransformException;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

public class GenerateCrsMetadataTest {

    private static CoordinateReferenceSystem wgs84 = DefaultGeographicCRS.WGS84;

    private @Value @Builder static class CrsInfo {
        private @NonNull String authorityCode;

        private @NonNull String wkt;

        private Envelope areaOfValidity;

        private Envelope areaOfValidityLongFirst;

        private Envelope geographicBoundingBox;

        private String wktLongFirst;

        private String error;
    }

    public Stream<CrsInfo> generateCrsMetadata() {
        Set<String> authorityCodes;
        try {
            authorityCodes = CRS.getAuthorityFactory(false)
                    .getAuthorityCodes(org.opengis.referencing.crs.CoordinateReferenceSystem.class);
        } catch (FactoryException e) {
            throw new RuntimeException(e);
        }

        return authorityCodes.stream().map(this::convert).filter(c -> c != null);

    }

    private CrsInfo convert(final @NonNull String authorityCode) {
        if (authorityCode.startsWith("CRS:")) {
            return null;
        }
        String error = null;
        final String wkt;
        String wktLongFirst = null;
        Envelope geographicBoundingBox = null;
        Envelope areaOfValidity = null;
        Envelope areaOfValidityLongFirst = null;
        try {
            CoordinateReferenceSystem crs = CRS.decode(authorityCode, false);
            CoordinateReferenceSystem crsLongFirst = CRS.decode(authorityCode, true);
            wkt = toWKT(crs);
            geographicBoundingBox = getGeographicBoundingBox(crs);
            areaOfValidity = getProjectedBoundingBox(crs, geographicBoundingBox);
            if (!CRS.equalsIgnoreMetadata(crs, crsLongFirst)) {
                wktLongFirst = toWKT(crsLongFirst);
                areaOfValidityLongFirst = getProjectedBoundingBox(crsLongFirst,
                        geographicBoundingBox);
            }
            return CrsInfo.builder().authorityCode(authorityCode).wkt(wkt)
                    .wktLongFirst(wktLongFirst).geographicBoundingBox(geographicBoundingBox)
                    .areaOfValidity(areaOfValidity).areaOfValidityLongFirst(areaOfValidityLongFirst)
                    .error(error).build();
        } catch (Exception e) {
            // error = e.getMessage();
            // if (error == null) {
            // error = "Unknown error";
            // e.printStackTrace();
            // } else {
            // error = error.replaceAll("[\\r\\n]+", "");
            // }
            return null;
        }
    }

    private String toWKT(CoordinateReferenceSystem crs) {
        String wkt = null;
        if (crs instanceof Formattable) {
            wkt = ((Formattable) crs).toWKT(Formattable.SINGLE_LINE);
        }
        if (wkt == null) {
            wkt = crs.toWKT().replaceAll("[\\r\\n]+", "");
        }
        return wkt;
    }

    public static Envelope getExtents(org.opengis.referencing.crs.CoordinateReferenceSystem crs)
            throws OperationNotFoundException, FactoryException, TransformException {

        Envelope geographicBoundingBox = getGeographicBoundingBox(crs);

        return getProjectedBoundingBox(crs, geographicBoundingBox);
    }

    private static Envelope getProjectedBoundingBox(
            org.opengis.referencing.crs.CoordinateReferenceSystem crs,
            Envelope geographicBoundingBox)
            throws OperationNotFoundException, FactoryException, TransformException {
        if (geographicBoundingBox == null) {
            return null;
        }
        double minx = geographicBoundingBox.getMinX();
        double miny = geographicBoundingBox.getMinY();
        double maxx = geographicBoundingBox.getMaxX();
        double maxy = geographicBoundingBox.getMaxY();

        CoordinateOperationFactory coordOpFactory = CRS.getCoordinateOperationFactory(true);
        CoordinateOperation op = coordOpFactory.createOperation(wgs84, crs);

        ReferencedEnvelope refEnvelope = new ReferencedEnvelope(minx, maxx, miny, maxy, wgs84);
        GeneralEnvelope genEnvelope = CRS.transform(op, refEnvelope);

        double xmax = genEnvelope.getMaximum(0);
        double ymax = genEnvelope.getMaximum(1);
        double xmin = genEnvelope.getMinimum(0);
        double ymin = genEnvelope.getMinimum(1);

        return new Envelope(xmin, xmax, ymin, ymax);
    }

    private static Envelope getGeographicBoundingBox(
            org.opengis.referencing.crs.CoordinateReferenceSystem crs) {
        if (crs == null) {
            return null;
        }

        final Extent areaOfValidity = crs.getDomainOfValidity();

        if (null == areaOfValidity)
            return null;

        Collection<? extends GeographicExtent> geographicElements = areaOfValidity
                .getGeographicElements();
        if (geographicElements.isEmpty()) {
            return null;
        }
        GeographicExtent geographicExtent = geographicElements.iterator().next();
        GeographicBoundingBox geographicBoundingBox = (GeographicBoundingBox) geographicExtent;

        double minx = geographicBoundingBox.getWestBoundLongitude();
        double miny = geographicBoundingBox.getSouthBoundLatitude();
        double maxx = geographicBoundingBox.getEastBoundLongitude();
        double maxy = geographicBoundingBox.getNorthBoundLatitude();

        return new Envelope(minx, maxx, miny, maxy);
    }

    private void generateResources() throws IOException {
        System.err.println("Generating reosurces for all EPSG CRS's, this may take a while...");
        List<CrsInfo> md = generateCrsMetadata().collect(Collectors.toList());

        Stream<CharSequence> epsgWktLines = md.stream().filter(c -> c.getWkt() != null)
                .map(c -> String.format("%s=%s", parseCode(c.getAuthorityCode()), c.getWkt()));

        Stream<CharSequence> epsgLonLatWktLines = md.stream()
                .filter(c -> c.getWktLongFirst() != null).map(c -> String.format("%d=%s",
                        parseCode(c.getAuthorityCode()), c.getWktLongFirst()));

        Path targetDir = Paths.get("target/generated-resources/org/locationtech/geogig/crs");
        Path epsgPropertiesFile = targetDir.resolve("epsg.properties");
        Path epsgLongFirstPropertiesFile = targetDir.resolve("epsg_force_long_lat.properties");

        Files.createDirectories(targetDir);
        Files.write(epsgPropertiesFile, () -> epsgWktLines.iterator(), StandardCharsets.UTF_8);
        Files.write(epsgLongFirstPropertiesFile, () -> epsgLonLatWktLines.iterator());

        Path mdFile = targetDir.resolve("epsg.metadata.properties");
        Path mdLonLatFile = targetDir.resolve("epsg_force_long_lat.metadata.properties");

        Stream<CharSequence> epsgMdLines = md.stream()
                .filter(c -> c.getGeographicBoundingBox() != null).map(c -> toMdLine(c, false));
        Stream<CharSequence> epsgLonLatMdLines = md.stream()
                .filter(c -> c.getGeographicBoundingBox() != null).map(c -> toMdLine(c, true));
        Files.write(mdFile, () -> epsgMdLines.iterator(), StandardCharsets.UTF_8);
        Files.write(mdLonLatFile, () -> epsgLonLatMdLines.iterator());
    }

    private String toMdLine(CrsInfo i, boolean longFirst) {
        int code = parseCode(i.getAuthorityCode());
        CrsMetadata md = CrsMetadata.builder().geographicExtent(i.getGeographicBoundingBox())
                .areaOfValidity(longFirst ? i.getAreaOfValidityLongFirst() : i.getAreaOfValidity())
                .build();
        return String.format("%d=%s", code, CrsMetadata.encode(md));
    }

    private int parseCode(String authorityCode) {
        return Integer.parseInt(authorityCode.substring(1 + authorityCode.lastIndexOf(':')));
    }

    public @Test void doGenerateResources() throws IOException {
        this.generateResources();
    }

    public static void main(String... args) throws IOException {
        new GenerateCrsMetadataTest().generateResources();
    }
}
