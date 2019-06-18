package org.locationtech.geogig.model.internal;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.function.Function;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import com.google.gson.Gson;

import lombok.NonNull;

public class Marshallers {

    private static final Marshaller STRING_ARRAY_MARSHALLER = new StringArrayMarshaller();

    private static final MapMarshaller MAP_MARSHALLER = new MapMarshaller();

    private static final PrimitiveArrayMarshaller PRIMITIVE_ARRAY_MARSHALLER = new PrimitiveArrayMarshaller();

    private static final Marshaller GEOMETRY_MARSHALLER = new GeometryMarshaller();

    public static Marshaller toString(Function<String, Object> unmarshaller) {
        return new ToStringMarshaller(unmarshaller);
    }

    public static Marshaller map() {
        return MAP_MARSHALLER;
    }

    public static Marshaller primitiveArray() {
        return PRIMITIVE_ARRAY_MARSHALLER;
    }

    public static Marshaller stringArray() {
        return STRING_ARRAY_MARSHALLER;
    }

    public static Marshaller geometry() {
        return GEOMETRY_MARSHALLER;
    }

    private static class StringArrayMarshaller implements Marshaller {
        private Gson gson = new Gson();

        public @Override @NonNull String marshall(@NonNull Object value) {
            String json = gson.toJson(value);
            return json;
        }

        public @Override @NonNull Object unmarshall(@NonNull String source,
                @NonNull Class<?> target) {

            Object fromJson = gson.fromJson(source, target);
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

        public @Override @NonNull Object unmarshall(@NonNull String source,
                @NonNull Class<?> target) {
            return this.unmarshallFunction.apply(source);
        }
    }

    private static class GeometryMarshaller extends ToStringMarshaller {
        private static GeometryFactory GF = new GeometryFactory();

        public @Override @NonNull Object unmarshall(@NonNull String source,
                @NonNull Class<?> target) {
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
            public @Override @NonNull Object unmarshall(@NonNull String source,
                    @NonNull Class<?> target) {
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
            public @Override @NonNull Object unmarshall(@NonNull String source,
                    @NonNull Class<?> target) {
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
            public @Override @NonNull Object unmarshall(@NonNull String source,
                    @NonNull Class<?> target) {
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
            public @Override @NonNull Object unmarshall(@NonNull String source,
                    @NonNull Class<?> target) {
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
            public @Override @NonNull Object unmarshall(@NonNull String source,
                    @NonNull Class<?> target) {
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
}