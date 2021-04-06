package org.locationtech.geogig.crs;

import static org.junit.Assert.assertTrue;

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
import org.geotools.referencing.CRS.AxisOrder;
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

    private @Value @Builder static class CrsInfo {
        private @NonNull String authorityCode;

        private Crs crs;

        private Crs crsLongFirst;

        private String error;
    }

    private @Value @Builder static class Crs {
        private @NonNull String wkt;

        private Envelope areaOfValidity;

        private Envelope geographicBoundingBox;
    }

    public Stream<CrsInfo> generateCrsMetadata() {
        Set<String> authorityCodes;
        try {
            final boolean longitudeFirst = false;
            authorityCodes = CRS.getAuthorityFactory(longitudeFirst)
                    .getAuthorityCodes(org.opengis.referencing.crs.CoordinateReferenceSystem.class);
        } catch (FactoryException e) {
            throw new RuntimeException(e);
        }

        Stream<String> stream = authorityCodes.stream();
        return stream.map(this::convert).filter(c -> c != null);

    }

    private CrsInfo convert(final @NonNull String authorityCode) {
        if (authorityCode.startsWith("CRS:")) {
            return null;
        }
        Crs crsmeta = null;
        Crs crsmetaLongFirst = null;
        String error = null;
        try {
            CoordinateReferenceSystem crs = CRS.decode(authorityCode, false);
            crsmeta = crsMetadata(crs);

            CoordinateReferenceSystem crsLongFirst = CRS.decode(authorityCode, true);
            if (!CRS.equalsIgnoreMetadata(crs, crsLongFirst)) {
                String wkt = toWKT(crsLongFirst);
                Envelope geographicBoundingBox = crsmeta.getGeographicBoundingBox();
                Envelope aov = crsmeta.getAreaOfValidity();
                Envelope areaOfValidity = new Envelope(aov.getMinY(), aov.getMaxY(), aov.getMinX(),
                        aov.getMaxX());
                crsmetaLongFirst = Crs.builder()//
                        .wkt(wkt)//
                        .geographicBoundingBox(geographicBoundingBox)//
                        .areaOfValidity(areaOfValidity)//
                        .build();
            }
        } catch (Exception e) {
            error = e.getMessage();
            if (error == null) {
                error = "Unknown error";
            } else {
                error = error.replaceAll("[\\r\\n]+", "");
            }
        }
        return CrsInfo.builder()//
                .authorityCode(authorityCode)//
                .crs(crsmeta)//
                .crsLongFirst(crsmetaLongFirst)//
                .error(error)//
                .build();
    }

    private Crs crsMetadata(CoordinateReferenceSystem crs) throws Exception {

        String wkt = toWKT(crs);
        Envelope geographicBoundingBox = getGeographicBoundingBox(crs);
        Envelope areaOfValidity = getProjectedBoundingBox(crs, geographicBoundingBox);
        Crs crsmeta = Crs.builder()//
                .wkt(wkt)//
                .geographicBoundingBox(geographicBoundingBox)//
                .areaOfValidity(areaOfValidity)//
                .build();
        return crsmeta;
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

    private static Envelope getProjectedBoundingBox(
            org.opengis.referencing.crs.CoordinateReferenceSystem crs, Envelope gbbox)
            throws OperationNotFoundException, FactoryException, TransformException {
        if (gbbox == null) {
            return null;
        }

        final CoordinateReferenceSystem wgs84 = DefaultGeographicCRS.WGS84;
        final boolean longFirst = CRS.getAxisOrder(wgs84) == AxisOrder.EAST_NORTH;
        assertTrue(longFirst);

        double minx = gbbox.getMinX();
        double maxx = gbbox.getMaxX();

        double miny = gbbox.getMinY();
        double maxy = gbbox.getMaxY();

        final boolean lenient = true;
        CoordinateOperationFactory coordOpFactory = CRS.getCoordinateOperationFactory(lenient);
        CoordinateOperation op = coordOpFactory.createOperation(wgs84, crs);

        ReferencedEnvelope refEnvelope = new ReferencedEnvelope(minx, maxx, miny, maxy, wgs84);
        GeneralEnvelope genEnvelope = CRS.transform(op, refEnvelope);

        double xmin = genEnvelope.getMinimum(0);
        double xmax = genEnvelope.getMaximum(0);
        double ymin = genEnvelope.getMinimum(1);
        double ymax = genEnvelope.getMaximum(1);

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
        double maxx = geographicBoundingBox.getEastBoundLongitude();

        double miny = geographicBoundingBox.getSouthBoundLatitude();
        double maxy = geographicBoundingBox.getNorthBoundLatitude();

        return new Envelope(minx, maxx, miny, maxy);
    }

    private void generateResources() throws IOException {
        System.err.println("Generating reosurces for all EPSG CRS's, this may take a while...");
        List<CrsInfo> md = generateCrsMetadata().collect(Collectors.toList());

        Stream<CharSequence> epsgWktLines = md.stream().filter(c -> c.getCrs() != null).map(
                c -> String.format("%s=%s", parseCode(c.getAuthorityCode()), c.getCrs().getWkt()));

        Stream<CharSequence> epsgLonLatWktLines = md.stream()
                .filter(c -> c.getCrsLongFirst() != null).map(c -> String.format("%d=%s",
                        parseCode(c.getAuthorityCode()), c.getCrsLongFirst().getWkt()));

        Path targetDir = Paths.get("target/generated-resources/org/locationtech/geogig/crs");
        Path epsgPropertiesFile = targetDir.resolve("epsg.properties");
        Path epsgLongFirstPropertiesFile = targetDir.resolve("epsg_force_long_lat.properties");

        Files.createDirectories(targetDir);
        Files.write(epsgPropertiesFile, () -> epsgWktLines.iterator(), StandardCharsets.UTF_8);
        Files.write(epsgLongFirstPropertiesFile, () -> epsgLonLatWktLines.iterator());

        Path mdFile = targetDir.resolve("epsg.metadata.properties");
        Path mdLonLatFile = targetDir.resolve("epsg_force_long_lat.metadata.properties");

        Stream<CharSequence> epsgMdLines = md.stream()//
                .filter(c -> c.getCrs() != null)//
                .map(c -> toMdLine(c.getAuthorityCode(), c.getCrs()));

        Stream<CharSequence> epsgLonLatMdLines = md.stream()//
                .filter(c -> c.getCrsLongFirst() != null)//
                .map(c -> toMdLine(c.getAuthorityCode(), c.getCrsLongFirst()));

        Files.write(mdFile, () -> epsgMdLines.iterator(), StandardCharsets.UTF_8);
        Files.write(mdLonLatFile, () -> epsgLonLatMdLines.iterator());
    }

    private String toMdLine(String authorityCode, Crs i) {
        int code = parseCode(authorityCode);
        CrsMetadata md = CrsMetadata.builder().geographicExtent(i.getGeographicBoundingBox())
                .areaOfValidity(i.getAreaOfValidity()).build();
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
