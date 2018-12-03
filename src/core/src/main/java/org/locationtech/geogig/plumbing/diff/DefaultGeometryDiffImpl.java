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

import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.storage.text.TextValueSerializer;
import org.locationtech.jts.geom.Geometry;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * An implementation of GeometryDiff that just stores both the new and the old value, so it actually
 * has the least compact representation, but can be used in all cases.
 * 
 * 
 */
public class DefaultGeometryDiffImpl {

    private Optional<Geometry> oldGeom;

    private Optional<Geometry> newGeom;

    public DefaultGeometryDiffImpl(Optional<Geometry> oldGeom, Optional<Geometry> newGeom) {
        this.oldGeom = oldGeom;
        this.newGeom = newGeom;
    }

    public DefaultGeometryDiffImpl(String s) {
        String[] tokens = s.split("\t");
        Preconditions.checkArgument(tokens.length == 2, "Wrong difference definition:", s);
        oldGeom = Optional.fromNullable((Geometry) TextValueSerializer
                .fromString(FieldType.forBinding(Geometry.class), tokens[0]));
        newGeom = Optional.fromNullable((Geometry) TextValueSerializer
                .fromString(FieldType.forBinding(Geometry.class), tokens[1]));

    }

    private CharSequence geometryValueAsString(Optional<Geometry> value) {
        return TextValueSerializer.asString(Optional.fromNullable((Object) value.orNull()));
    }

    public DefaultGeometryDiffImpl reversed() {
        return new DefaultGeometryDiffImpl(newGeom, oldGeom);
    }

    public boolean canBeAppliedOn(Optional<Geometry> obj) {
        return obj.equals(oldGeom);
    }

    public Optional<Geometry> applyOn(Optional<Geometry> obj) {
        Preconditions.checkArgument(canBeAppliedOn(obj));
        return newGeom;
    }

    public String toString() {
        return geometryValueAsString(oldGeom) + " -> " + geometryValueAsString(newGeom);
    }

    public String asText() {
        return geometryValueAsString(oldGeom) + "\t" + geometryValueAsString(newGeom);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultGeometryDiffImpl)) {
            return false;
        }
        DefaultGeometryDiffImpl d = (DefaultGeometryDiffImpl) o;
        return d.oldGeom.equals(oldGeom) && d.newGeom.equals(newGeom);
    }

}
