package org.locationtech.geogig.feature;

import java.util.Iterator;
import java.util.Optional;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.ValueArray;
import org.locationtech.jts.geom.GeometryFactory;

import lombok.NonNull;
import lombok.ToString;

@ToString
class FeatureDelegate extends Feature {

    @SuppressWarnings("unchecked")
    private static final Optional<Object>[] UNINITIALIZED_MUTABLE_STATE = new Optional[0];

    private final @NonNull ValueArray immutableValues;

    private Optional<Object>[] mutatedValues = UNINITIALIZED_MUTABLE_STATE;

    private GeometryFactory geometryFactory;

    public FeatureDelegate(@NonNull String id, @NonNull FeatureType type, ObjectId revision,
            @NonNull ValueArray values) {
        super(id, type, revision);
        this.immutableValues = values;
    }

    public FeatureDelegate(@NonNull String id, @NonNull FeatureType type, ObjectId oid,
            @NonNull ValueArray values, GeometryFactory geometryFactory) {
        super(id, type, oid);
        this.immutableValues = values;
        this.geometryFactory = geometryFactory;
    }

    private boolean mutable() {
        return this.mutatedValues != UNINITIALIZED_MUTABLE_STATE;
    }

    public @Override String getVersion() {
        if (this.immutableValues instanceof RevFeature) {
            return ((RevFeature) this.immutableValues).getId().toString();
        }
        return null;
    }

    public @Override Object getAttribute(int index) {
        Optional<? extends Object> value = mutable() ? mutatedValues[index] : null;
        if (value == null) {
            if (geometryFactory != null && getType().getDescriptor(index).isGeometryDescriptor()) {
                value = immutableValues.get(index, geometryFactory);
            } else {
                value = immutableValues.get(index);
            }
        }
        return value.orElse(null);
    }

    @SuppressWarnings("unchecked")
    public @Override void setAttribute(int index, Object value) {
        value = validate(index, value);
        if (!mutable()) {
            this.mutatedValues = new Optional[this.immutableValues.size()];
        }
        this.mutatedValues[index] = Optional.ofNullable(value);
    }

    public @Override Iterator<Object> iterator() {
        if (!mutable()) {
            return this.immutableValues.iterator();
        }
        return new Iterator<Object>() {
            final int size = FeatureDelegate.this.getAttributeCount();

            int curr = 0;

            public @Override boolean hasNext() {
                return curr < size;
            }

            public @Override Object next() {
                Object val = getAttribute(curr);
                curr++;
                return val;
            }
        };
    }

    public @Override Feature createCopy(@NonNull String newId) {
        FeatureDelegate copy = new FeatureDelegate(newId, getType(), getRevision(),
                this.immutableValues, this.geometryFactory);
        if (mutable()) {
            copy.mutatedValues = this.mutatedValues.clone();
        }
        return copy;
    }
}
