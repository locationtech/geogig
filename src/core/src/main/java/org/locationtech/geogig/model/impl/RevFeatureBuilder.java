/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.vividsolutions.jts.algorithm.CGAlgorithms;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateArrays;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.CoordinateSequenceFactory;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.plumbing.HashObject;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

/**
 * Builder for {@link RevFeature} instances.
 * 
 * <p>
 * Use the {@link #addValue(Object)} method as many times as needed to provide the sequence of
 * property values, then call {@link #build()} to get the final {@link RevFeature}.
 * 
 * @see RevFeature
 * @see Feature
 */
public final class RevFeatureBuilder {

    private ArrayList</* @Nullable */Object> values = new ArrayList<>(5);

    CoordinateSequenceFactory factory1D = new PackedCoordinateSequenceFactory(0,1);
    CoordinateSequenceFactory factory2D = new PackedCoordinateSequenceFactory(0,2);
    CoordinateSequenceFactory factory3D = new PackedCoordinateSequenceFactory(0,3);

    private RevFeatureBuilder() {
        //
    }

    public static RevFeatureBuilder builder() {
        return new RevFeatureBuilder();
    }

    public RevFeature build() {
        ObjectId id = HashObject.hashFeature(values);
        return new RevFeatureImpl(id, new ArrayList<>(values));
    }

    public RevFeature build(ObjectId id) {
        return new RevFeatureImpl(id, new ArrayList<>(values));
    }

    public RevFeatureBuilder reset() {
        this.values.clear();
        return this;
    }

    public RevFeatureBuilder addProperty(Property featureProp) {
        checkNotNull(featureProp);
        // This is where we might handle complex properties if ever supported
        addValue(featureProp.getValue());
        return this;
    }

    /**
     * Adds the provided value to the tail of the sequence of attribute values that compose the
     * {@link RevFeature} being built.
     * <p>
     * In order to preserve the {@link RevFeature}'s immutability, a safe copy of the value will be
     * assigned if it's a mutable type.
     * 
     * @see FieldType#safeCopy(Object)
     */
    public RevFeatureBuilder addValue(@Nullable Object value) {
        return addValueNoCopy(safeCopy(value));
    }

    /**
     * Use with caution, this object takes ownership of {@code value} without making a safe copy of
     * it.
     */
    public RevFeatureBuilder addValueNoCopy(@Nullable Object value) {
        if (value instanceof Geometry) {
            value = normalizeIfNeeded((Geometry) value);
        }
        this.values.add(value);
        return this;
    }

    // JTS doesn't properly normalize polygons if they use PackedCoordinateSequences
    Polygon normalizePolygon(Polygon p) {
        GeometryFactory gf_geogig = p.getFactory();
        LinearRing outer = normalize(p.getExteriorRing(),gf_geogig);
        LinearRing[] holes = new LinearRing[p.getNumInteriorRing()];
        for (int t=0;t<p.getNumInteriorRing();t++)
            holes[t] = normalize(p.getInteriorRingN(t),gf_geogig);

        return gf_geogig.createPolygon(outer,holes);
    }

    MultiPolygon normalizeMultiPolygon(MultiPolygon mp) {
        GeometryFactory gf_geogig = mp.getFactory();

        Polygon[] ps = new Polygon[mp.getNumGeometries()];
        for (int t=0;t<mp.getNumGeometries();t++)
            ps[t] = normalizePolygon((Polygon)mp.getGeometryN(t));

        return gf_geogig.createMultiPolygon(ps);
    }

    LinearRing normalize(LineString line,GeometryFactory gf) {
        // no change required
        if (!CGAlgorithms.isCCW(line.getCoordinates()))
            return (LinearRing) line;

        Coordinate[] coords = line.getCoordinateSequence().toCoordinateArray();
        CoordinateArrays.reverse(coords);
        CoordinateSequence cs;
        int dim =  line.getCoordinateSequence().getDimension();
        if (dim ==2)
            cs = factory2D.create(coords);
        else if (dim==3)
            cs = factory3D.create(coords);
        else
            cs = factory1D.create(coords);
        return gf.createLinearRing(cs);
    }

    //This will normalize the geometry -- it ONLY makes sure the rings are CW
    // This will create a new CoordinateSequence for the geometry
    // (and make new polygons/multipolygon/geometrycollections).  These will be
    // PackedCoordinateSequences of the same dimension as the geometry's coord sequence
    Geometry normalizeIfNeeded(Geometry value) {
        if (value instanceof Polygon) {
           return normalizePolygon((Polygon) value);
        } else if (value instanceof MultiPolygon) {
            return normalizeMultiPolygon((MultiPolygon) value);
        } else if ( GeometryCollection.class.equals(value.getClass())) {
           return normalize((GeometryCollection) value);
        }
        return value; // point,line,multipoint,multiline
    }

    private GeometryCollection normalize(GeometryCollection col) {
        GeometryFactory gf_geogig = col.getFactory();

        Geometry[] gs = new Geometry[col.getNumGeometries()];
        for (int t=0;t<col.getNumGeometries();t++)
            gs[t] = normalizeIfNeeded(col.getGeometryN(t));

        return gf_geogig.createGeometryCollection(gs);
    }

    private Object safeCopy(@Nullable Object value) {
        FieldType fieldType = FieldType.forValue(value);
        if (FieldType.UNKNOWN.equals(fieldType)) {
            throw new IllegalArgumentException(String.format(
                    "Objects of class %s are not supported as RevFeature attributes: ",
                    value.getClass().getName()));
        }
        value = fieldType.safeCopy(value);
        return value;
    }

    public RevFeatureBuilder addAll(List<Object> values) {
        checkNotNull(values);
        for (Object v : values) {
            addValue(v);
        }
        return this;
    }

    public RevFeatureBuilder addAll(Object... values) {
        checkNotNull(values);
        for (Object v : values) {
            addValue(v);
        }
        return this;
    }

    /**
     * Constructs a new {@link RevFeature} from the provided {@link Feature}.
     * 
     * @param feature the feature to build from
     * @return the newly constructed RevFeature
     */
    public static RevFeature build(Feature feature) {
        if (feature == null) {
            throw new IllegalStateException("No feature set");
        }

        RevFeatureBuilder builder = RevFeatureBuilder.builder();

        if (feature instanceof SimpleFeature) {
            // Just
            SimpleFeature sf = (SimpleFeature) feature;
            int attributeCount = sf.getAttributeCount();
            for (int i = 0; i < attributeCount; i++) {
                builder.addValue(sf.getAttribute(i));
            }
        } else {
            Collection<Property> props = feature.getProperties();
            props.forEach((p) -> builder.addProperty(p));
        }
        return builder.build();
    }
}
