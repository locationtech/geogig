/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.locationtech.geogig.plumbing.diff.AttributeDiff.TYPE.ADDED;
import static org.locationtech.geogig.plumbing.diff.AttributeDiff.TYPE.MODIFIED;
import static org.locationtech.geogig.plumbing.diff.AttributeDiff.TYPE.NO_CHANGE;
import static org.locationtech.geogig.plumbing.diff.AttributeDiff.TYPE.REMOVED;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.storage.text.TextValueSerializer;
import org.locationtech.jts.geom.Geometry;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/**
 * An implementation of AttributeDiff to be used with attributes containing geometries
 * 
 */
public class GeometryAttributeDiff implements AttributeDiff {

    private TYPE type;

    private @Nullable Geometry oldGeometry;

    private @Nullable Geometry newGeometry;

    private LCSGeometryDiffImpl diff;

    public GeometryAttributeDiff(@Nullable Geometry oldGeom, @Nullable Geometry newGeom) {
        Preconditions.checkArgument(oldGeom != null || newGeom != null);
        oldGeometry = oldGeom;
        newGeometry = newGeom;
        if (newGeom == null) {
            type = TYPE.REMOVED;
        } else if (oldGeom == null) {
            type = TYPE.ADDED;
        } else if (oldGeom.equalsExact(newGeom)) {
            // Note the use of Geometry.equalsExact() instead of equals() (which defers to
            // equalsTopo() and may throw a TopologyException, while equalsExact() just compares
            // coordinate by coordinate, which is what we intend to do here. Not doing topology
            // validation which is up to the application.
            type = TYPE.NO_CHANGE;
            diff = new LCSGeometryDiffImpl(oldGeom, newGeom);
        } else {
            type = TYPE.MODIFIED;
            diff = new LCSGeometryDiffImpl(oldGeom, newGeom);
        }

    }

    public GeometryAttributeDiff(LCSGeometryDiffImpl diff) {
        type = TYPE.MODIFIED;
        this.diff = diff;
    }

    public GeometryAttributeDiff(String s) {
        String[] tokens = s.split("\t");
        if (tokens[0].equals("M")) {
            type = TYPE.MODIFIED;
            diff = new LCSGeometryDiffImpl(s.substring(s.indexOf("\t") + 1));
        } else if (tokens[0].equals("A")) {
            Preconditions.checkArgument(tokens.length == 3);
            type = TYPE.ADDED;
            String wkt = tokens[1];
            newGeometry = (Geometry) TextValueSerializer
                    .fromString(FieldType.forBinding(Geometry.class), wkt);
        } else if (tokens[0].equals("R")) {
            Preconditions.checkArgument(tokens.length == 3);
            type = TYPE.REMOVED;
            String wkt = tokens[1];
            oldGeometry = (Geometry) TextValueSerializer
                    .fromString(FieldType.forBinding(Geometry.class), wkt);
        } else {
            throw new IllegalArgumentException("Wrong difference definition:" + s);
        }

    }

    @Override
    public Geometry getOldValue() {
        return oldGeometry;
    }

    @Override
    public Geometry getNewValue() {
        return newGeometry;
    }

    @Override
    public TYPE getType() {
        return type;
    }

    @Override
    public AttributeDiff reversed() {
        if (type == TYPE.MODIFIED) {
            return new GeometryAttributeDiff(this.diff.reversed());
        } else {
            return new GeometryAttributeDiff(oldGeometry, newGeometry);
        }
    }

    @Override
    public Geometry applyOn(@Nullable Object value) {
        Preconditions.checkState(canBeAppliedOn(value));
        switch (type) {
        case ADDED:
            return newGeometry;
        case REMOVED:
            return null;
        case MODIFIED:
        default:
            return diff.applyOn((Geometry) value);
        }
    }

    @Override
    public boolean canBeAppliedOn(@Nullable Object value) {
        switch (this.type) {
        case ADDED:
            return value == null;
        case REMOVED:
            checkNotNull(oldGeometry);
            checkNotNull(value);
            Geometry geom = (Geometry) value;
            return geom.equalsExact(oldGeometry);
        case MODIFIED:
        default:
            return diff.canBeAppliedOn((Geometry) value);
        }

    }

    public String toString() {
        switch (type) {
        case ADDED:
            return "[MISSING] -> " + TextValueSerializer.asString(newGeometry);
        case REMOVED:
            return TextValueSerializer.asString(oldGeometry) + " -> [MISSING]";
        case MODIFIED:
        default:
            return diff.toString();
        }
    }

    @Override
    public String asText() {
        switch (type) {
        case ADDED:
            return type.name().toCharArray()[0] + "\t" + TextValueSerializer.asString(newGeometry);
        case REMOVED:
            return type.name().toCharArray()[0] + "\t" + TextValueSerializer.asString(oldGeometry);
        case MODIFIED:
        default:
            return type.name().toCharArray()[0] + "\t" + diff.asText();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GeometryAttributeDiff)) {
            return false;
        }
        GeometryAttributeDiff d = (GeometryAttributeDiff) o;
        if (oldGeometry == null && newGeometry == null) {
            return d.oldGeometry == null && d.newGeometry == null && Objects.equal(type, d.type);
        } else {
            return diff.equals(d.diff);
        }
    }

    /**
     * Returns the difference corresponding to the case of a modified attributed. If the attribute
     * is of type ADDED or REMOVED, this method will return null
     */
    public LCSGeometryDiffImpl getDiff() {
        return diff;
    }

    @Override
    public boolean conflicts(AttributeDiff ad) {
        if (!(ad instanceof GeometryAttributeDiff)) {
            return true;
        }
        final GeometryAttributeDiff gad = (GeometryAttributeDiff) ad;
        final TYPE myType = getType();
        final TYPE otherType = ad.getType();
        // if either side of the diff is a no change, then there's no conflict, regardless of the
        // type of the other side
        if (NO_CHANGE == myType || NO_CHANGE == otherType) {
            return false;
        }

        if (REMOVED == myType && REMOVED == otherType) {
            return false;
        }
        if (MODIFIED == myType && MODIFIED == otherType) {
            if (gad.diff.equals(diff)) {
                return false;
            } else {
                return true;
            }
        }
        if (ADDED == myType && ADDED == otherType) {
            return !gad.newGeometry.equalsExact(newGeometry);
        }

        return true;
    }

}
