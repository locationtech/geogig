/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.text;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.util.Converters;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;

/**
 * A text serializer for attribute values
 * 
 */
public class TextValueSerializer {

    static interface ValueSerializer {

        public Object fromString(@Nullable String in) throws ParseException;

        public String toString(@Nullable Object value);

    }

    static abstract class DefaultValueSerializer implements ValueSerializer {
        @Override
        public String toString(Object value) {
            return value.toString();
        }
    }

    static abstract class ArraySerializer implements ValueSerializer {
        @Override
        public String toString(Object value) {
            if (value.getClass().getComponentType().isPrimitive()) {
                return Converters.convert(value, String.class);
            }
            return "[" + Joiner.on(" ").join((Object[]) value) + "]";
        }
    }

    static Map<FieldType, ValueSerializer> serializers = new HashMap<FieldType, ValueSerializer>();
    static {
        serializers.put(FieldType.NULL, new DefaultValueSerializer() {
            @Override
            public Object fromString(String in) {
                return null;
            }

            @Override
            public String toString(Object value) {
                return "";
            }
        });
        serializers.put(FieldType.BOOLEAN, new DefaultValueSerializer() {
            @Override
            public Object fromString(String in) {
                return new Boolean(in);
            }

        });
        serializers.put(FieldType.BYTE, new DefaultValueSerializer() {
            @Override
            public Object fromString(String in) {
                return new Byte(in);
            }
        });
        serializers.put(FieldType.SHORT, new DefaultValueSerializer() {
            @Override
            public Object fromString(String in) {
                return new Short(in);
            }
        });
        serializers.put(FieldType.INTEGER, new DefaultValueSerializer() {
            @Override
            public Object fromString(String in) {
                return new Integer(in);
            }
        });
        serializers.put(FieldType.LONG, new DefaultValueSerializer() {
            @Override
            public Object fromString(String in) {
                return new Long(in);
            }
        });
        serializers.put(FieldType.FLOAT, new DefaultValueSerializer() {
            @Override
            public Object fromString(String in) {
                return new Float(in);
            }
        });
        serializers.put(FieldType.DOUBLE, new DefaultValueSerializer() {
            @Override
            public Object fromString(String in) {
                return new Double(in);
            }
        });
        serializers.put(FieldType.STRING, new DefaultValueSerializer() {
            @Override
            public Object fromString(String in) {
                return in;
            }
        });

        serializers.put(FieldType.BOOLEAN_ARRAY, new ArraySerializer() {
            @Override
            public Object fromString(String in) {
                String[] s = in.split(" ");
                List<Boolean> list = Lists.newArrayList();
                for (String token : s) {
                    list.add(new Boolean(token));
                }
                return list.toArray(new Boolean[0]);
            }
        });
        serializers.put(FieldType.BYTE_ARRAY, new ArraySerializer() {
            @Override
            public Object fromString(String in) {
                String[] s = in.split(" ");
                List<Byte> list = Lists.newArrayList();
                for (String token : s) {
                    list.add(new Byte(token));
                }
                return list.toArray(new Byte[0]);
            }
        });
        serializers.put(FieldType.SHORT_ARRAY, new ArraySerializer() {
            @Override
            public Object fromString(String in) {
                String[] s = in.replace("[", "").replace("]", "").split(" ");
                List<Short> list = Lists.newArrayList();
                for (String token : s) {
                    list.add(new Short(token));
                }
                return list.toArray(new Short[0]);
            }
        });
        serializers.put(FieldType.INTEGER_ARRAY, new ArraySerializer() {
            @Override
            public Object fromString(String in) {
                String[] s = in.split(" ");
                List<Integer> list = Lists.newArrayList();
                for (String token : s) {
                    list.add(new Integer(token));
                }
                return list.toArray(new Integer[0]);
            }
        });
        serializers.put(FieldType.LONG_ARRAY, new ArraySerializer() {
            @Override
            public Object fromString(String in) {
                String[] s = in.split(" ");
                List<Long> list = Lists.newArrayList();
                for (String token : s) {
                    list.add(new Long(token));
                }
                return list.toArray(new Long[0]);
            }
        });
        serializers.put(FieldType.FLOAT_ARRAY, new ArraySerializer() {
            @Override
            public Object fromString(String in) {
                String[] s = in.split(" ");
                List<Float> list = Lists.newArrayList();
                for (String token : s) {
                    list.add(new Float(token));
                }
                return list.toArray(new Float[0]);
            }
        });
        serializers.put(FieldType.DOUBLE_ARRAY, new ArraySerializer() {
            @Override
            public Object fromString(String in) {
                String[] s = in.split(" ");
                List<Double> list = Lists.newArrayList();
                for (String token : s) {
                    list.add(new Double(token));
                }
                return list.toArray(new Byte[0]);
            }
        });
        ValueSerializer geometry = new ValueSerializer() {

            @Override
            public Object fromString(String in) throws ParseException {
                return new WKTReader().read(in);
            }

            @Override
            public String toString(Object value) {
                return ((Geometry) value).toText();
            }

        };
        serializers.put(FieldType.GEOMETRY, geometry);
        serializers.put(FieldType.POINT, geometry);
        serializers.put(FieldType.LINESTRING, geometry);
        serializers.put(FieldType.POLYGON, geometry);
        serializers.put(FieldType.MULTIPOINT, geometry);
        serializers.put(FieldType.MULTILINESTRING, geometry);
        serializers.put(FieldType.MULTIPOLYGON, geometry);
        serializers.put(FieldType.GEOMETRYCOLLECTION, geometry);
        serializers.put(FieldType.UUID, new DefaultValueSerializer() {
            @Override
            public Object fromString(String in) {
                return UUID.fromString(in);
            }

        });
        serializers.put(FieldType.BIG_INTEGER, new DefaultValueSerializer() {
            @Override
            public Object fromString(String in) {
                return new BigInteger(in);
            }

        });
        serializers.put(FieldType.BIG_DECIMAL, new DefaultValueSerializer() {
            @Override
            public Object fromString(String in) {
                return new BigDecimal(in);
            }
        });
        serializers.put(FieldType.DATETIME, new ValueSerializer() {
            @Override
            public Object fromString(String in) {
                return new java.util.Date(Long.parseLong(in));
            }

            @Override
            public String toString(Object value) {
                return String.valueOf(((java.util.Date) value).getTime());
            }
        });
        serializers.put(FieldType.DATE, new ValueSerializer() {
            @Override
            public Object fromString(String in) {
                return new java.sql.Date(Long.parseLong(in));
            }

            @Override
            public String toString(Object value) {
                return String.valueOf(((java.sql.Date) value).getTime());
            }
        });
        serializers.put(FieldType.TIME, new ValueSerializer() {
            @Override
            public Object fromString(String in) {
                return new java.sql.Time(Long.parseLong(in));
            }

            @Override
            public String toString(Object value) {
                return String.valueOf(((java.sql.Time) value).getTime());
            }
        });
        serializers.put(FieldType.TIMESTAMP, new ValueSerializer() {
            @Override
            public Object fromString(String in) {
                String[] millisnanos = in.split(" ");
                java.sql.Timestamp ts = new java.sql.Timestamp(Long.parseLong(millisnanos[0]));
                ts.setNanos(Integer.parseInt(millisnanos[1]));
                return ts;
            }

            @Override
            public String toString(Object value) {
                java.sql.Timestamp ts = (java.sql.Timestamp) value;
                return new StringBuilder().append(ts.getTime()).append(' ').append(ts.getNanos())
                        .toString();
            }
        });
        serializers.put(FieldType.MAP, new ValueSerializer() {

            @Override
            public String toString(Object value) {
                return Converters.convert(value, String.class);
            }

            @Override
            public Object fromString(String in) throws ParseException {
                return Converters.convert(in, Map.class);
            }
        });
    }

    /**
     * Returns a string representation of the passed field value
     * 
     * @param opt
     */
    public static String asString(Optional<Object> opt) {
        return asString(opt.orNull());
    }

    public static String asString(@Nullable Object value) {
        final FieldType type = FieldType.forValue(value);
        if (serializers.containsKey(type)) {
            return serializers.get(type).toString(value);
        } else {
            throw new IllegalArgumentException("The specified type is not supported: " + type);
        }
    }

    /**
     * Creates a value object from its string representation
     * 
     * @param type
     * @param in
     * @return
     */
    public static Object fromString(FieldType type, String in) {
        if (serializers.containsKey(type)) {
            try {
                return serializers.get(type).fromString(in);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Unable to parse wrong value: " + in);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unable to parse wrong value: " + in);
            }
        } else {
            throw new IllegalArgumentException("The specified type is not supported");
        }
    }
}
