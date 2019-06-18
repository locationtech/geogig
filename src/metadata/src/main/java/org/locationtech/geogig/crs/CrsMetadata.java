package org.locationtech.geogig.crs;

import org.locationtech.jts.geom.Envelope;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
class CrsMetadata {

    private Envelope geographicExtent;

    private Envelope areaOfValidity;

    static String encode(@NonNull CrsMetadata md) {
        Envelope geographicExtent = md.getGeographicExtent();
        Envelope aov = md.getAreaOfValidity();
        if (geographicExtent == null) {
            geographicExtent = new Envelope();
        }
        if (aov == null || geographicExtent.equals(aov)) {
            aov = new Envelope();
        }
        String geogStr = encodeEnvelope(geographicExtent);
        String aovStr = encodeEnvelope(aov);

        return String.format("%s;%s", geogStr, aovStr);
    }

    static CrsMetadata decode(@NonNull String encoded) {
        String[] split = encoded.split(";");
        Envelope geogExtent = decodeEnvelope(split[0]);
        Envelope aov = decodeEnvelope(split[1]);
        if (aov.isNull()) {
            aov = geogExtent;
        }
        return CrsMetadata.builder().geographicExtent(geogExtent).areaOfValidity(aov).build();
    }

    static String encodeEnvelope(@NonNull Envelope env) {
        if (env.isNull()) {
            return "[]";
        }
        return String.format("[%f,%f,%f,%f]", //
                env.getMinX(), //
                env.getMaxX(), //
                env.getMinY(), //
                env.getMaxY());
    }

    static Envelope decodeEnvelope(@NonNull String env) {
        if (!env.startsWith("[") && !env.endsWith("]")) {
            throw new IllegalArgumentException(String.format(
                    "Invalid Envelope encoding, expected '[]' or '[x1,x2,y1,y2]', got '%s'", env));
        }
        try {
            String[] values = env.replaceAll("[\\[\\]]+", "").split(",");
            if (values.length == 0 || (values.length == 1 && values[0].trim().isEmpty())) {
                return new Envelope();
            }
            return new Envelope(//
                    Double.parseDouble(values[0]), //
                    Double.parseDouble(values[1]), //
                    Double.parseDouble(values[2]), //
                    Double.parseDouble(values[3])//
            );
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException | NullPointerException e) {
            throw new IllegalArgumentException(String.format(
                    "Invalid Envelope encoding, expected '[]' or '[x1,x2,y1,y2]', got '%s'", env),
                    e);
        }
    }
}