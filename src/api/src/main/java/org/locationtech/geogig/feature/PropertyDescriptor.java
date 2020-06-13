package org.locationtech.geogig.feature;

import org.locationtech.geogig.crs.CoordinateReferenceSystem;
import org.locationtech.jts.geom.Geometry;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

public @Value @Builder class PropertyDescriptor {

    private @NonNull Name name;

    private @NonNull Name typeName;

    private @NonNull Class<?> binding;

    private boolean nillable;

    private int minOccurs;

    private int maxOccurs;

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

    public static @Accessors(fluent = true, chain = true) class PropertyDescriptorBuilder {
        private Name name;

        private Name typeName;

        private Class<?> binding;

        private boolean nillable = true;

        private int minOccurs = 0;

        private int maxOccurs = 1;

        private CoordinateReferenceSystem coordinateReferenceSystem;

        private FeatureType complexBindingType;

        public PropertyDescriptor build() {
            if (Geometry.class.isAssignableFrom(binding) && coordinateReferenceSystem == null) {
                coordinateReferenceSystem = CoordinateReferenceSystem.NULL;
            }
            if (Feature.class.equals(binding)) {
                if (null == complexBindingType) {
                    throw new IllegalStateException(
                            "Feature binding requires complexBindingType to be set");
                }
            }
            return new PropertyDescriptor(name, typeName, binding, nillable, minOccurs, maxOccurs,
                    coordinateReferenceSystem, complexBindingType);
        }
    }
}
