package org.locationtech.geogig.storage.text;

import static org.locationtech.geogig.model.FieldType.BIG_DECIMAL;
import static org.locationtech.geogig.model.FieldType.BIG_INTEGER;
import static org.locationtech.geogig.model.FieldType.BOOLEAN;
import static org.locationtech.geogig.model.FieldType.BOOLEAN_ARRAY;
import static org.locationtech.geogig.model.FieldType.BYTE;
import static org.locationtech.geogig.model.FieldType.BYTE_ARRAY;
import static org.locationtech.geogig.model.FieldType.CHAR;
import static org.locationtech.geogig.model.FieldType.CHAR_ARRAY;
import static org.locationtech.geogig.model.FieldType.DATE;
import static org.locationtech.geogig.model.FieldType.DATETIME;
import static org.locationtech.geogig.model.FieldType.DOUBLE;
import static org.locationtech.geogig.model.FieldType.DOUBLE_ARRAY;
import static org.locationtech.geogig.model.FieldType.ENVELOPE_2D;
import static org.locationtech.geogig.model.FieldType.FLOAT;
import static org.locationtech.geogig.model.FieldType.FLOAT_ARRAY;
import static org.locationtech.geogig.model.FieldType.GEOMETRY;
import static org.locationtech.geogig.model.FieldType.GEOMETRYCOLLECTION;
import static org.locationtech.geogig.model.FieldType.INTEGER;
import static org.locationtech.geogig.model.FieldType.INTEGER_ARRAY;
import static org.locationtech.geogig.model.FieldType.LINESTRING;
import static org.locationtech.geogig.model.FieldType.LONG;
import static org.locationtech.geogig.model.FieldType.LONG_ARRAY;
import static org.locationtech.geogig.model.FieldType.MAP;
import static org.locationtech.geogig.model.FieldType.MULTILINESTRING;
import static org.locationtech.geogig.model.FieldType.MULTIPOINT;
import static org.locationtech.geogig.model.FieldType.MULTIPOLYGON;
import static org.locationtech.geogig.model.FieldType.NULL;
import static org.locationtech.geogig.model.FieldType.POINT;
import static org.locationtech.geogig.model.FieldType.POLYGON;
import static org.locationtech.geogig.model.FieldType.SHORT;
import static org.locationtech.geogig.model.FieldType.SHORT_ARRAY;
import static org.locationtech.geogig.model.FieldType.STRING;
import static org.locationtech.geogig.model.FieldType.STRING_ARRAY;
import static org.locationtech.geogig.model.FieldType.TIME;
import static org.locationtech.geogig.model.FieldType.TIMESTAMP;
import static org.locationtech.geogig.model.FieldType.UNKNOWN;
import static org.locationtech.geogig.model.FieldType.UUID;
import static org.locationtech.geogig.model.FieldType.forBinding;
import static org.locationtech.geogig.model.FieldType.forValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import com.google.gson.Gson;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public class Marshallers {

    private static final Marshaller NULL_MARSHALLER = new Marshaller() {
        public @Override Object unmarshall(String source) {
            return null;
        }

        public @Override String marshall(@Nullable Object value) {
            return null;
        }
    };

    private static final Marshaller STRING_ARRAY_MARSHALLER = new StringArrayMarshaller();

    private static final MapMarshaller MAP_MARSHALLER = new MapMarshaller();

    private static final EnumMap<FieldType, Marshaller> MARSHALLERS = new EnumMap<>(
            FieldType.class);

    static {
        MARSHALLERS.put(NULL, NULL_MARSHALLER);
        MARSHALLERS.put(BOOLEAN, toString(Boolean::valueOf));
        MARSHALLERS.put(BYTE, toString(Byte::valueOf));
        MARSHALLERS.put(SHORT, toString(Short::valueOf));
        MARSHALLERS.put(INTEGER, toString(Integer::valueOf));
        MARSHALLERS.put(LONG, toString(Long::valueOf));
        MARSHALLERS.put(FLOAT, toString(Float::valueOf));
        MARSHALLERS.put(DOUBLE, toString(Double::valueOf));
        MARSHALLERS.put(STRING, toString(s -> s));
        MARSHALLERS.put(BOOLEAN_ARRAY, primitiveArray(boolean[].class));
        MARSHALLERS.put(BYTE_ARRAY, primitiveArray(byte[].class));
        MARSHALLERS.put(SHORT_ARRAY, primitiveArray(short[].class));
        MARSHALLERS.put(INTEGER_ARRAY, primitiveArray(int[].class));
        MARSHALLERS.put(LONG_ARRAY, primitiveArray(long[].class));
        MARSHALLERS.put(FLOAT_ARRAY, primitiveArray(float[].class));
        MARSHALLERS.put(DOUBLE_ARRAY, primitiveArray(double[].class));
        MARSHALLERS.put(STRING_ARRAY, stringArray());
        MARSHALLERS.put(POINT, geometry(Point.class));
        MARSHALLERS.put(LINESTRING, geometry(LineString.class));
        MARSHALLERS.put(POLYGON, geometry(Polygon.class));
        MARSHALLERS.put(MULTIPOINT, geometry(MultiPoint.class));
        MARSHALLERS.put(MULTILINESTRING, geometry(MultiLineString.class));
        MARSHALLERS.put(MULTIPOLYGON, geometry(MultiPolygon.class));
        MARSHALLERS.put(GEOMETRYCOLLECTION, geometry(GeometryCollection.class));
        MARSHALLERS.put(GEOMETRY, geometry(Geometry.class));
        MARSHALLERS.put(UUID, toString(java.util.UUID::fromString));
        MARSHALLERS.put(BIG_INTEGER, toString(BigInteger::new));
        MARSHALLERS.put(BIG_DECIMAL, toString(BigDecimal::new));
        MARSHALLERS.put(DATETIME, dateTime());
        MARSHALLERS.put(DATE, date());
        MARSHALLERS.put(TIME, time());
        MARSHALLERS.put(TIMESTAMP, timestamp());
        MARSHALLERS.put(MAP, map());
        MARSHALLERS.put(CHAR, toString(s -> s.charAt(0)));
        MARSHALLERS.put(CHAR_ARRAY, primitiveArray(char[].class));
        MARSHALLERS.put(ENVELOPE_2D, Marshallers.envelope());
        MARSHALLERS.put(UNKNOWN, new Marshaller() {

            public @Override Object unmarshall(@NonNull String source) {
                throw new UnsupportedOperationException(
                        "UNKNOWN FieldType does not support unmarshalling");
            }

            public @Override String marshall(@NonNull Object value) {
                throw new UnsupportedOperationException(
                        "UNKNOWN FieldType does not support marshalling");
            }
        });
    }

    static Marshaller toString(Function<String, Object> unmarshaller) {
        return new ToStringMarshaller(unmarshaller);
    }

    public static Marshaller map() {
        return MAP_MARSHALLER;
    }

    public static Marshaller primitiveArray(Class<?> targetArrayClass) {
        return new PrimitiveArrayMarshaller(targetArrayClass);
    }

    public static Marshaller stringArray() {
        return STRING_ARRAY_MARSHALLER;
    }

    public static Marshaller geometry(@NonNull Class<? extends Geometry> target) {
        return new GeometryMarshaller(target);
    }

    private static class StringArrayMarshaller implements Marshaller {
        private Gson gson = new Gson();

        public @Override @NonNull String marshall(@NonNull Object value) {
            String json = gson.toJson(value);
            return json;
        }

        public @Override @NonNull Object unmarshall(@NonNull String source) {
            Object fromJson = gson.fromJson(source, String[].class);
            return fromJson;
        }

    }

    private static class ToStringMarshaller implements Marshaller {

        private final Function<String, Object> unmarshallFunction;

        protected ToStringMarshaller() {
            this(s -> {
                throw new UnsupportedOperationException();
            });
        }

        protected ToStringMarshaller(@NonNull Function<String, Object> unmarshal) {
            this.unmarshallFunction = unmarshal;
        }

        public @Override @NonNull String marshall(@NonNull Object value) {
            return value.toString();
        }

        public @Override @NonNull Object unmarshall(@NonNull String source) {
            return this.unmarshallFunction.apply(source);
        }
    }

    @RequiredArgsConstructor
    private static class GeometryMarshaller extends ToStringMarshaller {
        private static GeometryFactory GF = new GeometryFactory();

        private final Class<? extends Geometry> target;

        public @Override @NonNull Object unmarshall(@NonNull String source) {
            Geometry geometry;
            try {
                geometry = new WKTReader(GF).read(source);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            if (!target.isAssignableFrom(geometry.getClass())) {
                // TODO: create collection wrapper if given a simple geometry and target is
                // collection?
                throw new IllegalArgumentException(String.format("%s is not assignable to %s",
                        geometry.getClass().getName(), target.getName()));
            }
            return geometry;
        }
    }

    public static Marshaller dateTime() {
        return new Marshaller() {

            public @Override @NonNull Object unmarshall(@NonNull String source) {
                Instant parsed = Instant.parse(source);
                java.util.Date time = java.util.Date.from(parsed);
                return time;
            }

            public @Override @NonNull String marshall(@NonNull Object value) {
                java.util.Date date = (java.util.Date) value;
                Instant localTime = date.toInstant();
                String formatted = localTime.toString();
                return formatted;
            }
        };
    }

    public static Marshaller date() {
        return new Marshaller() {
            public @Override @NonNull Object unmarshall(@NonNull String source) {
                LocalDate parsed = LocalDate.parse(source);
                java.sql.Date time = Date.valueOf(parsed);
                return time;
            }

            public @Override @NonNull String marshall(@NonNull Object value) {
                java.sql.Date date = (Date) value;
                LocalDate localTime = date.toLocalDate();
                String formatted = localTime.toString();
                return formatted;
            }
        };
    }

    public static Marshaller time() {
        return new Marshaller() {
            public @Override @NonNull Object unmarshall(@NonNull String source) {
                LocalTime parsed = LocalTime.parse(source);
                Time time = Time.valueOf(parsed);
                return time;
            }

            public @Override @NonNull String marshall(@NonNull Object value) {
                java.sql.Time time = (Time) value;
                LocalTime localTime = time.toLocalTime();
                String formatted = localTime.toString();
                return formatted;
            }
        };
    }

    public static Marshaller timestamp() {
        return new Marshaller() {
            public @Override @NonNull Object unmarshall(@NonNull String source) {
                LocalDateTime parsed = LocalDateTime.parse(source);
                return Timestamp.valueOf(parsed);
            }

            public @Override @NonNull String marshall(@NonNull Object value) {
                java.sql.Timestamp ts = (Timestamp) value;
                LocalDateTime localDateTime = ts.toLocalDateTime();
                return localDateTime.toString();
            }
        };
    }

    public static Marshaller envelope() {
        return new Marshaller() {
            public @Override @NonNull Object unmarshall(@NonNull String source) {
                String[] values = source.replaceAll("[\\[\\]]+", "").split(",");
                if (values.length == 0 || (values.length == 1 && values[0].trim().isEmpty())) {
                    return new Envelope();
                }
                return new Envelope(//
                        Double.parseDouble(values[0]), //
                        Double.parseDouble(values[2]), //
                        Double.parseDouble(values[1]), //
                        Double.parseDouble(values[3])//
                );
            }

            public @Override @NonNull String marshall(@NonNull Object value) {
                Envelope env = (Envelope) value;
                return String.format("[%f,%f,%f,%f]", env.getMinX(), env.getMinY(), env.getMaxX(),
                        env.getMaxY());
            }
        };
    }

    public static @Nullable <T> T unmarshall(@Nullable String value,
            @NonNull Class<T> targetValueClass) {
        if (value == null)
            return null;
        FieldType fieldType = forBinding(targetValueClass);
        return unmarshall(value, fieldType);
    }

    @SuppressWarnings("unchecked")
    public static @Nullable <T> T unmarshall(@Nullable String value, @NonNull FieldType fieldType) {
        if (value == null)
            return null;
        if (FieldType.UNKNOWN.equals(fieldType)) {
            throw new IllegalArgumentException(
                    String.format("Type is not a recognized FieldType: %s", fieldType));
        }
        Marshaller marshaller = MARSHALLERS.get(fieldType);
        return (T) marshaller.unmarshall(value);
    }

    public static @Nullable String marshall(@Nullable Object value) {
        FieldType fieldType = forValue(value);
        if (FieldType.UNKNOWN.equals(fieldType)) {
            // value.getClass() is NPE safe at this point
            throw new IllegalArgumentException("Unable to infer FieldType for value of type "
                    + value.getClass().getCanonicalName());
        }
        Marshaller marshaller = MARSHALLERS.get(fieldType);
        return marshaller.marshall(value);
    }
}