package org.locationtech.geogig.feature;

import org.locationtech.geogig.crs.CoordinateReferenceSystem;
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

    private FeatureType complexBindingType;

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

    public @NonNull String getLocalName() {
        return getName().getLocalPart();
    }

    public static class PropertyDescriptorBuilder {
        public PropertyDescriptor build() {
            CoordinateReferenceSystem crs = this.coordinateReferenceSystem;
            if (Geometry.class.isAssignableFrom(this.binding) && crs == null) {
                crs = CoordinateReferenceSystem.NULL;
            }
            if (Feature.class.equals(binding)) {
                if (null == complexBindingType) {
                    throw new IllegalStateException(
                            "Feature binding requires complexBindingType to be set");
                }
            }
            return new PropertyDescriptor(name, typeName, binding, nillable, minOccurs, maxOccurs,
                    crs, complexBindingType);
        }
    }
}
