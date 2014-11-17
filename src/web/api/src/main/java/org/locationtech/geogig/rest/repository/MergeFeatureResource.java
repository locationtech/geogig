/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.rest.repository;

import static org.locationtech.geogig.rest.repository.RESTUtils.getGeogig;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map.Entry;
import java.util.UUID;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.WKTReader2;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureBuilder;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.storage.FieldType;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.PropertyDescriptor;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

/**
 *
 */
public class MergeFeatureResource extends Resource {

    @Override
    public boolean allowPost() {
        return true;
    }

    private Optional<NodeRef> parseID(ObjectId commitId, String path, GeoGIG geogig) {
        Optional<RevObject> object = geogig.command(RevObjectParse.class).setObjectId(commitId)
                .call();
        RevCommit commit = null;
        if (object.isPresent() && object.get() instanceof RevCommit) {
            commit = (RevCommit) object.get();
        } else {
            throw new CommandSpecException("Couldn't resolve id: " + commitId.toString()
                    + " to a commit");
        }

        object = geogig.command(RevObjectParse.class).setObjectId(commit.getTreeId()).call();

        if (object.isPresent()) {
            RevTree tree = (RevTree) object.get();
            return geogig.command(FindTreeChild.class).setParent(tree).setChildPath(path).call();
        } else {
            throw new CommandSpecException("Couldn't resolve commit's treeId");
        }
    }

    public void post(Representation entity) {
        InputStream input = null;

        try {
            input = getRequest().getEntity().getStream();
            final GeoGIG ggit = getGeogig(getRequest()).get();
            final Reader body = new InputStreamReader(input);
            final JsonParser parser = new JsonParser();
            final JsonElement conflictJson = parser.parse(body);

            if (conflictJson.isJsonObject()) {
                final JsonObject conflict = conflictJson.getAsJsonObject();
                String featureId = null;
                RevFeature ourFeature = null;
                RevFeatureType ourFeatureType = null;
                RevFeature theirFeature = null;
                RevFeatureType theirFeatureType = null;
                JsonObject merges = null;
                if (conflict.has("path") && conflict.get("path").isJsonPrimitive()) {
                    featureId = conflict.get("path").getAsJsonPrimitive().getAsString();
                }
                Preconditions.checkState(featureId != null);

                if (conflict.has("ours") && conflict.get("ours").isJsonPrimitive()) {
                    String ourCommit = conflict.get("ours").getAsJsonPrimitive().getAsString();
                    Optional<NodeRef> ourNode = parseID(ObjectId.valueOf(ourCommit), featureId,
                            ggit);
                    if (ourNode.isPresent()) {
                        Optional<RevObject> object = ggit.command(RevObjectParse.class)
                                .setObjectId(ourNode.get().objectId()).call();
                        Preconditions.checkState(object.isPresent()
                                && object.get() instanceof RevFeature);

                        ourFeature = (RevFeature) object.get();

                        object = ggit.command(RevObjectParse.class)
                                .setObjectId(ourNode.get().getMetadataId()).call();
                        Preconditions.checkState(object.isPresent()
                                && object.get() instanceof RevFeatureType);

                        ourFeatureType = (RevFeatureType) object.get();
                    }
                }

                if (conflict.has("theirs") && conflict.get("theirs").isJsonPrimitive()) {
                    String theirCommit = conflict.get("theirs").getAsJsonPrimitive().getAsString();
                    Optional<NodeRef> theirNode = parseID(ObjectId.valueOf(theirCommit), featureId,
                            ggit);
                    if (theirNode.isPresent()) {
                        Optional<RevObject> object = ggit.command(RevObjectParse.class)
                                .setObjectId(theirNode.get().objectId()).call();
                        Preconditions.checkState(object.isPresent()
                                && object.get() instanceof RevFeature);

                        theirFeature = (RevFeature) object.get();

                        object = ggit.command(RevObjectParse.class)
                                .setObjectId(theirNode.get().getMetadataId()).call();
                        Preconditions.checkState(object.isPresent()
                                && object.get() instanceof RevFeatureType);

                        theirFeatureType = (RevFeatureType) object.get();
                    }
                }

                if (conflict.has("merges") && conflict.get("merges").isJsonObject()) {
                    merges = conflict.get("merges").getAsJsonObject();
                }
                Preconditions.checkState(merges != null);

                Preconditions.checkState(ourFeatureType != null || theirFeatureType != null);

                SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(
                        (SimpleFeatureType) (ourFeatureType != null ? ourFeatureType.type()
                                : theirFeatureType.type()));

                ImmutableList<PropertyDescriptor> descriptors = (ourFeatureType == null ? theirFeatureType
                        : ourFeatureType).sortedDescriptors();

                for (Entry<String, JsonElement> entry : merges.entrySet()) {
                    int descriptorIndex = getDescriptorIndex(entry.getKey(), descriptors);
                    if (descriptorIndex != -1 && entry.getValue().isJsonObject()) {
                        PropertyDescriptor descriptor = descriptors.get(descriptorIndex);
                        JsonObject attributeObject = entry.getValue().getAsJsonObject();
                        if (attributeObject.has("ours")
                                && attributeObject.get("ours").isJsonPrimitive()
                                && attributeObject.get("ours").getAsBoolean()) {
                            featureBuilder.set(descriptor.getName(), ourFeature == null ? null
                                    : ourFeature.getValues().get(descriptorIndex).orNull());
                        } else if (attributeObject.has("theirs")
                                && attributeObject.get("theirs").isJsonPrimitive()
                                && attributeObject.get("theirs").getAsBoolean()) {
                            featureBuilder.set(descriptor.getName(), theirFeature == null ? null
                                    : theirFeature.getValues().get(descriptorIndex).orNull());
                        } else if (attributeObject.has("value")
                                && attributeObject.get("value").isJsonPrimitive()) {
                            JsonPrimitive primitive = attributeObject.get("value")
                                    .getAsJsonPrimitive();
                            if (primitive.isString()) {
                                try {
                                    Object object = valueFromString(
                                            FieldType.forBinding(descriptor.getType().getBinding()),
                                            primitive.getAsString());
                                    featureBuilder.set(descriptor.getName(), object);
                                } catch (Exception e) {
                                    throw new Exception("Unable to convert attribute ("
                                            + entry.getKey() + ") to required type: "
                                            + descriptor.getType().getBinding().toString());
                                }
                            } else if (primitive.isNumber()) {
                                try {
                                    Object value = valueFromNumber(
                                            FieldType.forBinding(descriptor.getType().getBinding()),
                                            primitive.getAsNumber());
                                    featureBuilder.set(descriptor.getName(), value);
                                } catch (Exception e) {
                                    throw new Exception("Unable to convert attribute ("
                                            + entry.getKey() + ") to required type: "
                                            + descriptor.getType().getBinding().toString());
                                }
                            } else if (primitive.isBoolean()) {
                                try {
                                    Object value = valueFromBoolean(
                                            FieldType.forBinding(descriptor.getType().getBinding()),
                                            primitive.getAsBoolean());
                                    featureBuilder.set(descriptor.getName(), value);
                                } catch (Exception e) {
                                    throw new Exception("Unable to convert attribute ("
                                            + entry.getKey() + ") to required type: "
                                            + descriptor.getType().getBinding().toString());
                                }
                            } else if (primitive.isJsonNull()) {
                                featureBuilder.set(descriptor.getName(), null);
                            } else {
                                throw new Exception("Unsupported JSON type for attribute value ("
                                        + entry.getKey() + ")");
                            }
                        }
                    }
                }
                SimpleFeature feature = featureBuilder
                        .buildFeature(NodeRef.nodeFromPath(featureId));
                RevFeature revFeature = RevFeatureBuilder.build(feature);
                ggit.getRepository().stagingDatabase().put(revFeature);

                getResponse().setEntity(
                        new StringRepresentation(revFeature.getId().toString(),
                                MediaType.TEXT_PLAIN));
            }

        } catch (Exception e) {
            throw new RestletException(e.getMessage(), Status.SERVER_ERROR_INTERNAL, e);
        } finally {
            if (input != null)
                Closeables.closeQuietly(input);
        }
    }

    private Object valueFromNumber(FieldType type, Number value) throws Exception {
        switch (type) {
        case NULL:
            return null;
        case BOOLEAN:
            return new Boolean(value.doubleValue() != 0);
        case BYTE:
            return new Byte(value.byteValue());
        case SHORT:
            return new Short(value.shortValue());
        case INTEGER:
            return new Integer(value.intValue());
        case LONG:
            return new Long(value.longValue());
        case FLOAT:
            return new Float(value.floatValue());
        case DOUBLE:
            return new Double(value.doubleValue());
        case STRING:
            return value.toString();
        case BOOLEAN_ARRAY:
            boolean boolArray[] = { value.doubleValue() != 0 };
            return boolArray;
        case BYTE_ARRAY:
            byte byteArray[] = { value.byteValue() };
            return byteArray;
        case SHORT_ARRAY:
            short shortArray[] = { value.shortValue() };
            return shortArray;
        case INTEGER_ARRAY:
            int intArray[] = { value.intValue() };
            return intArray;
        case LONG_ARRAY:
            long longArray[] = { value.longValue() };
            return longArray;
        case FLOAT_ARRAY:
            float floatArray[] = { value.floatValue() };
            return floatArray;
        case DOUBLE_ARRAY:
            double doubleArray[] = { value.doubleValue() };
            return doubleArray;
        case STRING_ARRAY:
            String stringArray[] = { value.toString() };
            return stringArray;
        case DATETIME:
            return new Date(value.longValue());
        case DATE:
            return new java.sql.Date(value.longValue());
        case TIME:
            return new java.sql.Time(value.longValue());
        case TIMESTAMP:
            return new java.sql.Timestamp(value.longValue());
        case POINT:
        case LINESTRING:
        case POLYGON:
        case MULTIPOINT:
        case MULTILINESTRING:
        case MULTIPOLYGON:
        case GEOMETRYCOLLECTION:
        case GEOMETRY:
        case UUID:
        case BIG_INTEGER:
        case BIG_DECIMAL:
        default:
            break;
        }
        throw new IOException();
    }

    private Object valueFromBoolean(FieldType type, boolean value) throws Exception {
        switch (type) {
        case NULL:
            return null;
        case BOOLEAN:
            return new Boolean(value);
        case BYTE:
            return new Byte((byte) (value ? 1 : 0));
        case SHORT:
            return new Short((short) (value ? 1 : 0));
        case INTEGER:
            return new Integer((int) (value ? 1 : 0));
        case LONG:
            return new Long((long) (value ? 1 : 0));
        case FLOAT:
            return new Float((float) (value ? 1 : 0));
        case DOUBLE:
            return new Double((double) (value ? 1 : 0));
        case STRING:
            return Boolean.toString(value);
        case BOOLEAN_ARRAY:
            boolean boolArray[] = { value };
            return boolArray;
        case BYTE_ARRAY:
            byte byteArray[] = { (byte) (value ? 1 : 0) };
            return byteArray;
        case SHORT_ARRAY:
            short shortArray[] = { (short) (value ? 1 : 0) };
            return shortArray;
        case INTEGER_ARRAY:
            int intArray[] = { (int) (value ? 1 : 0) };
            return intArray;
        case LONG_ARRAY:
            long longArray[] = { (long) (value ? 1 : 0) };
            return longArray;
        case FLOAT_ARRAY:
            float floatArray[] = { (float) (value ? 1 : 0) };
            return floatArray;
        case DOUBLE_ARRAY:
            double doubleArray[] = { (double) (value ? 1 : 0) };
            return doubleArray;
        case STRING_ARRAY:
            String stringArray[] = { Boolean.toString(value) };
            return stringArray;
        case POINT:
        case LINESTRING:
        case POLYGON:
        case MULTIPOINT:
        case MULTILINESTRING:
        case MULTIPOLYGON:
        case GEOMETRYCOLLECTION:
        case GEOMETRY:
        case UUID:
        case BIG_INTEGER:
        case BIG_DECIMAL:
        case DATETIME:
        case DATE:
        case TIME:
        case TIMESTAMP:
        default:
            break;
        }
        throw new IOException();
    }

    private Object valueFromString(FieldType type, String value) throws Exception {
        Geometry geom;
        switch (type) {
        case NULL:
            return null;
        case BOOLEAN:
            return new Boolean(value);
        case BYTE:
            return new Byte(value);
        case SHORT:
            return new Short(value);
        case INTEGER:
            return new Integer(value);
        case LONG:
            return new Long(value);
        case FLOAT:
            return new Float(value);
        case DOUBLE:
            return new Double(value);
        case STRING:
            return value;
        case BOOLEAN_ARRAY:
            boolean boolArray[] = { Boolean.parseBoolean(value) };
            return boolArray;
        case BYTE_ARRAY:
            byte byteArray[] = { Byte.parseByte(value) };
            return byteArray;
        case SHORT_ARRAY:
            short shortArray[] = { Short.parseShort(value) };
            return shortArray;
        case INTEGER_ARRAY:
            int intArray[] = { Integer.parseInt(value) };
            return intArray;
        case LONG_ARRAY:
            long longArray[] = { Long.parseLong(value) };
            return longArray;
        case FLOAT_ARRAY:
            float floatArray[] = { Float.parseFloat(value) };
            return floatArray;
        case DOUBLE_ARRAY:
            double doubleArray[] = { Double.parseDouble(value) };
            return doubleArray;
        case STRING_ARRAY:
            String stringArray[] = { value };
            return stringArray;
        case UUID:
            return UUID.fromString(value);
        case BIG_INTEGER:
            return new BigInteger(value);
        case BIG_DECIMAL:
            return new BigDecimal(value);
        case DATETIME:
            return new SimpleDateFormat().parse(value);
        case DATE:
            return java.sql.Date.valueOf(value);
        case TIME:
            return java.sql.Time.valueOf(value);
        case TIMESTAMP:
            return new java.sql.Timestamp(new SimpleDateFormat().parse(value).getTime());
        case POINT:
            geom = new WKTReader2().read(value);
            if (geom instanceof Point) {
                return (Point) geom;
            }
            break;
        case LINESTRING:
            geom = new WKTReader2().read(value);
            if (geom instanceof LineString) {
                return (LineString) geom;
            }
            break;
        case POLYGON:
            geom = new WKTReader2().read(value);
            if (geom instanceof Polygon) {
                return (Polygon) geom;
            }
            break;
        case MULTIPOINT:
            geom = new WKTReader2().read(value);
            if (geom instanceof MultiPoint) {
                return (MultiPoint) geom;
            }
            break;
        case MULTILINESTRING:
            geom = new WKTReader2().read(value);
            if (geom instanceof MultiLineString) {
                return (MultiLineString) geom;
            }
            break;
        case MULTIPOLYGON:
            geom = new WKTReader2().read(value);
            if (geom instanceof MultiPolygon) {
                return (MultiPolygon) geom;
            }
            break;
        case GEOMETRYCOLLECTION:
            geom = new WKTReader2().read(value);
            if (geom instanceof GeometryCollection) {
                return (GeometryCollection) geom;
            }
            break;
        case GEOMETRY:
            return new WKTReader2().read(value);
        default:
            break;
        }
        throw new IOException();
    }

    private int getDescriptorIndex(String key, ImmutableList<PropertyDescriptor> properties) {
        for (int i = 0; i < properties.size(); i++) {
            PropertyDescriptor prop = properties.get(i);
            if (prop.getName().toString().equals(key)) {
                return i;
            }
        }
        return -1;
    }
}
