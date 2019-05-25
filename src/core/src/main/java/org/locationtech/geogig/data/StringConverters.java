package org.locationtech.geogig.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import lombok.Getter;
import lombok.NonNull;

public class StringConverters {

    private static ConcurrentMap<Class<?>, Marshaller> MARSHALLERS = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T unmarshall(String value, @NonNull Class<T> targetClass) {
        if (value == null || Void.class.equals(targetClass)) {
            return null;
        }
        Marshaller m = MARSHALLERS.computeIfAbsent(targetClass, StringConverters::findMarshaller);
        if (m == null) {
            throw new UnsupportedOperationException(String.format(
                    "Unimplemented conversion from %s to %s", String.class.getName(), targetClass));

        }
        return (T) m.unmarshall(value, targetClass);
    }

    public static String marshall(Object value) {
        if (value == null) {
            return null;
        }
        Class<? extends Object> valueClass = value.getClass();
        Marshaller m = MARSHALLERS.computeIfAbsent(valueClass, StringConverters::findMarshaller);
        if (m == null) {
            throw new UnsupportedOperationException(
                    String.format("Unimplemented conversion from %s to %s",
                            value.getClass().getName(), String.class.getName()));

        }
        return m.marshall(value);
    }

    private static Marshaller findMarshaller(@NonNull Class<?> valueClass) {
        List<Marshaller> marshallers = findMarshallers();
        for (Marshaller m : marshallers) {
            if (m.canHandle(valueClass)) {
                return m;
            }
        }
        return null;
    }

    private static List<Marshaller> marshallers = new ArrayList<>();

    private static List<Marshaller> findMarshallers() {
        if (marshallers.isEmpty()) {
            List<Marshaller> list = loadMarshallers();
            marshallers = list;
        }
        return marshallers;
    }

    private static List<Marshaller> loadMarshallers() {
        return Arrays.asList(//
                new MapMarshaller(), //
                new PrimitiveArrayMarshaller(), //
                new GeometryMarshaller(), //
                new ToStringMarshaller(String.class, s -> s), //
                new ToStringMarshaller(Boolean.class, Boolean::valueOf), //
                new ToStringMarshaller(Short.class, Short::valueOf), //
                new ToStringMarshaller(Integer.class, Integer::valueOf), //
                new ToStringMarshaller(Long.class, Long::valueOf), //
                new ToStringMarshaller(Float.class, Float::valueOf), //
                new ToStringMarshaller(Double.class, Double::valueOf), //
                new ToStringMarshaller(Character.class, s -> Character.valueOf(s.charAt(0))), //
                new ToStringMarshaller(Integer.class, Integer::valueOf)
        //
        );
    }

    private static class ToStringMarshaller implements Marshaller {

        private @Getter Class<?> valueType;

        private Function<String, Object> unmarshallFunction;

        protected ToStringMarshaller(Class<?> valueType) {
            this(valueType, null);
        }

        protected ToStringMarshaller(Class<?> valueType, Function<String, Object> unmarshal) {
            this.valueType = valueType;
            this.unmarshallFunction = unmarshal;
        }

        public @Override @NonNull String marshall(@NonNull Object value) {
            return value.toString();
        }

        public @Override @NonNull Object unmarshall(@NonNull String source,
                @NonNull Class<?> target) {
            if (this.unmarshallFunction == null) {
                throw new UnsupportedOperationException(
                        "An unmarshalling function shall be supplied or this method overridden");
            }
            return this.unmarshallFunction.apply(source);
        }
    }

    private static class GeometryMarshaller extends ToStringMarshaller {
        private static GeometryFactory GF = new GeometryFactory();

        protected GeometryMarshaller() {
            super(null);
        }

        public @Override boolean canHandle(@NonNull Class<?> valueClass) {
            return Geometry.class.isAssignableFrom(valueClass);
        }

        public @Override @NonNull Object unmarshall(@NonNull String source,
                @NonNull Class<?> target) {
            try {
                return new WKTReader(GF).read(source);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

    }
}