package org.locationtech.geogig.crs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Envelope;

import lombok.Cleanup;
import lombok.NonNull;

public class CRS {

    private static ConcurrentMap<Integer, CoordinateReferenceSystem> EPSG_CACHE = new ConcurrentHashMap<>();

    private static ConcurrentMap<Integer, CoordinateReferenceSystem> EPSG_CACHE_FORCE_LON_LAT = new ConcurrentHashMap<>();

    public static Set<String> getAuthorityCodes() {
        return getAuthorityCodes(false);
    }

    public static Set<String> getAuthorityCodes(boolean forceLongitudeFirst) {
        try {
            final @Cleanup BufferedReader reader = getEpsgReader(forceLongitudeFirst);
            final String prefix = forceLongitudeFirst ? "EPSG:" : "urn:ogc:def:crs:EPSG::";
            return reader.lines().parallel().map(s -> prefix + s.substring(0, s.indexOf('=')))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * @throws NoSuchElementException
     */
    public static CoordinateReferenceSystem decode(@NonNull String identifier) {
        CoordinateReferenceSystem crs = null;
        if (CoordinateReferenceSystem.NULL.getSrsIdentifier().equals(identifier)) {
            return CoordinateReferenceSystem.NULL;
        }
        if (identifier.startsWith("EPSG:") || identifier.startsWith("CRS:")) {
            crs = findEpsgForceLongitudeFirst(identifier);
        } else if (identifier.startsWith("urn:ogc:def:crs:EPSG:")) {
            crs = findEpsg(identifier);
        }
        if (crs == null) {
            throw new NoSuchElementException(
                    String.format("CRS definition for identifier %s is not found", identifier));
        }
        return crs;
    }

    public static Optional<Envelope> findGeographicBoundingBox(@NonNull String identifier) {
        return findMetadata(identifier).map(CrsMetadata::getGeographicExtent);
    }

    public static Optional<Envelope> findAreaOfValidity(@NonNull String identifier) {
        return findMetadata(identifier).map(CrsMetadata::getAreaOfValidity);
    }

    static Optional<CrsMetadata> findMetadata(@NonNull String identifier) {
        final boolean longFirst = identifier.startsWith("EPSG:");
        final Integer code = parseCode(identifier);
        final String prefix = code + "=";
        try (BufferedReader reader = getMetadataReader(longFirst)) {
            return reader.lines().filter(l -> l.startsWith(prefix)).findFirst()
                    .map(CRS::removePrefix).map(CrsMetadata::decode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String removePrefix(@NonNull String propertiesFileLine) {
        return propertiesFileLine.substring(1 + propertiesFileLine.indexOf('='));
    }

    static CoordinateReferenceSystem findEpsg(final @NonNull String identifier) {
        final Integer code = parseCode(identifier);
        ensureLoaded(false);
        return EPSG_CACHE.get(code);
    }

    static CoordinateReferenceSystem findEpsgForceLongitudeFirst(String identifier) {
        final Integer code = parseCode(identifier);
        ensureLoaded(true);
        return EPSG_CACHE_FORCE_LON_LAT.get(code);
    }

    private static void ensureLoaded(boolean forceLongitudeFirst) {
        ConcurrentMap<Integer, CoordinateReferenceSystem> cache = forceLongitudeFirst
                ? EPSG_CACHE_FORCE_LON_LAT
                : EPSG_CACHE;
        if (cache.isEmpty()) {
            try {
                final @Cleanup BufferedReader reader = getEpsgReader(forceLongitudeFirst);
                String prefix = forceLongitudeFirst ? "EPSG:" : "urn:ogc:def:crs:EPSG::";
                cache.putAll(load(reader, prefix));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Map<Integer, CoordinateReferenceSystem> load(BufferedReader reader,
            String authorityPrefix) {
        long now = System.currentTimeMillis();
        Map<Integer, CoordinateReferenceSystem> map = reader.lines().collect(
                Collectors.toMap(CRS::parseCode, line -> CRS.parseCrs(authorityPrefix, line)));
        long t = System.currentTimeMillis() - now;
        System.err.printf("Loaded %d %s CRS's in %,dms\n", map.size(), authorityPrefix, t);
        return map;
    }

    private static Integer parseCode(String crsLine) {
        String identifier = crsLine;
        int keySplitIndex = crsLine.indexOf('=');
        if (keySplitIndex != -1) {
            identifier = crsLine.substring(0, keySplitIndex);
        }
        int codeSeparatorIndex = identifier.lastIndexOf(':');
        String codeStr = identifier.substring(codeSeparatorIndex + 1);
        return Integer.valueOf(codeStr);
    }

    private static CoordinateReferenceSystem parseCrs(String authPrefix, String crsLine) {
        int keySplitIndex = crsLine.indexOf('=');
        String identifier = authPrefix + crsLine.substring(0, keySplitIndex);
        String wkt = crsLine.substring(1 + keySplitIndex);
        return new CoordinateReferenceSystem(identifier, wkt);
    }

    private static BufferedReader getMetadataReader(boolean longFirst) {
        String resourceName = longFirst ? "epsg_force_long_lat.metadata.properties"
                : "epsg.metadata.properties";
        InputStream resource = CRS.class.getResourceAsStream(resourceName);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource, StandardCharsets.UTF_8));
        return reader;
    }

    private static BufferedReader getEpsgReader(boolean longFirst) {
        String resourceName = longFirst ? "epsg_force_long_lat.properties" : "epsg.properties";
        InputStream resource = CRS.class.getResourceAsStream(resourceName);
        if (resource == null) {
            throw new IllegalStateException(String.format("resource %s.%s not found in classpath",
                    CRS.class.getPackage().getName(), resourceName));
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource, StandardCharsets.UTF_8));
        return reader;
    }

    public static CoordinateReferenceSystem fromWKT(@NonNull String wkt) {
        return new CoordinateReferenceSystem(null, wkt);
    }
}
