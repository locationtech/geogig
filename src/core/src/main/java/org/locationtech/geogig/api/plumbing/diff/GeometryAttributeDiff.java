/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.plumbing.diff;

import org.locationtech.geogig.storage.FieldType;
import org.locationtech.geogig.storage.text.TextValueSerializer;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Geometry;

/**
 * An implementation of AttributeDiff to be used with attributes containing geometries
 * 
 */
public class GeometryAttributeDiff implements AttributeDiff {

    private TYPE type;

    private Optional<Geometry> oldGeometry;

    private Optional<Geometry> newGeometry;

    private LCSGeometryDiffImpl diff;

    public GeometryAttributeDiff(Optional<Geometry> oldGeom, Optional<Geometry> newGeom) {
        Preconditions.checkArgument(oldGeom != null || newGeom != null);
        oldGeometry = oldGeom;
        newGeometry = newGeom;
        if (newGeom == null || !newGeom.isPresent()) {
            type = TYPE.REMOVED;
        } else if (oldGeom == null || !oldGeom.isPresent()) {
            type = TYPE.ADDED;
        } else if (oldGeom.equals(newGeom)) {
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
            newGeometry = Optional.fromNullable((Geometry) TextValueSerializer.fromString(
                    FieldType.forBinding(Geometry.class), tokens[1]));
        } else if (tokens[0].equals("R")) {
            Preconditions.checkArgument(tokens.length == 3);
            type = TYPE.REMOVED;
            oldGeometry = Optional.fromNullable((Geometry) TextValueSerializer.fromString(
                    FieldType.forBinding(Geometry.class), tokens[1]));
        } else {
            throw new IllegalArgumentException("Wrong difference definition:" + s);
        }

    }

    @Override
    public Optional<?> getOldValue() {
        return oldGeometry;
    }

    @Override
    public Optional<?> getNewValue() {
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

    @SuppressWarnings("unchecked")
    @Override
    public Optional<?> applyOn(Optional<?> obj) {
        Preconditions.checkState(canBeAppliedOn(obj));
        switch (type) {
        case ADDED:
            return newGeometry;
        case REMOVED:
            return null;
        case MODIFIED:
        default:
            return diff.applyOn((Optional<Geometry>) obj);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean canBeAppliedOn(Optional<?> obj) {
        switch (type) {
        case ADDED:
            return obj == null;
        case REMOVED:
            return obj.equals(oldGeometry);
        case MODIFIED:
        default:
            return diff.canBeAppliedOn((Optional<Geometry>) obj);
        }

    }

    public String toString() {
        switch (type) {
        case ADDED:
            return "[MISSING] -> "
                    + TextValueSerializer.asString(Optional.fromNullable((Object) newGeometry
                            .orNull()));
        case REMOVED:
            return TextValueSerializer
                    .asString(Optional.fromNullable((Object) oldGeometry.orNull()))
                    + " -> [MISSING]";
        case MODIFIED:
        default:
            return diff.toString();
        }
    }

    @Override
    public String asText() {
        switch (type) {
        case ADDED:
            return type.name().toCharArray()[0]
                    + "\t"
                    + TextValueSerializer.asString(Optional.fromNullable((Object) newGeometry
                            .orNull()));
        case REMOVED:
            return type.name().toCharArray()[0]
                    + "\t"
                    + TextValueSerializer.asString(Optional.fromNullable((Object) oldGeometry
                            .orNull()));
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
            return Objects.equal(oldGeometry, oldGeometry)
                    && Objects.equal(newGeometry, d.newGeometry) && Objects.equal(type, d.type);
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
        GeometryAttributeDiff gad = (GeometryAttributeDiff) ad;
        if (TYPE.REMOVED.equals(ad.getType()) && TYPE.REMOVED.equals(getType())) {
            return false;
        }
        if (TYPE.MODIFIED.equals(ad.getType()) && TYPE.MODIFIED.equals(getType())) {
            if (gad.diff.equals(diff)) {
                return false;
            } else {
                return !gad.canBeAppliedOn(newGeometry);
            }
        }
        if (TYPE.ADDED.equals(ad.getType()) && TYPE.ADDED.equals(getType())) {
            return !gad.newGeometry.equals(newGeometry);
        }

        return true;
    }

}
