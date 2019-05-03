package org.locationtech.geogig.feature;

import java.util.Optional;

import org.locationtech.jts.geom.Geometry;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

public @Value @Builder class PropertyDescriptor {

    private @NonNull Name name;

    private @NonNull Name typeName;

    private @NonNull Class<?> binding;

    private @Builder.Default boolean nillable = true;

    private @Builder.Default int minOccurs = 0;

    private @Builder.Default int maxOccurs = 1;

    private CoordinateReferenceSystem coordinateReferenceSystem;

    public Optional<CoordinateReferenceSystem> getCoordinateReferenceSystem() {
        return Optional.ofNullable(coordinateReferenceSystem);
    }

    public @NonNull CoordinateReferenceSystem coordinateReferenceSystem() {
        return this.coordinateReferenceSystem == null ? CoordinateReferenceSystem.NULL
                : this.coordinateReferenceSystem;
    }

    public boolean isGeometryDescriptor() {
        return Geometry.class.isAssignableFrom(this.binding);
    }

    public @NonNull Name getTypeName() {
        return typeName == null ? name : typeName;
    }
}
