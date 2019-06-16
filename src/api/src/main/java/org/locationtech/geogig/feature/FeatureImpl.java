package org.locationtech.geogig.feature;

import java.util.Arrays;
import java.util.Iterator;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class FeatureImpl extends Feature {

    protected @NonNull Object[] values;

    FeatureImpl(@NonNull String id, @NonNull FeatureType type) {
        super(id, type, null);
        this.values = new Object[type.getSize()];
    }

    public @Override Object getAttribute(int index) {
        return values[index];
    }

    public @Override void setAttribute(int index, Object value) {
        value = validate(index, value);
        this.values[index] = value;
    }

    public @Override Iterator<Object> iterator() {
        return Arrays.asList(this.values).iterator();
    }

    public @Override Feature createCopy(@NonNull String newId) {
        FeatureImpl copy = new FeatureImpl(newId, getType());
        copy.values = this.values.clone();
        return copy;
    }

}
