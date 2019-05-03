package org.locationtech.geogig.feature;

import java.util.List;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Delegate;

public @Value @Builder class Feature {

    private @NonNull String id;

    private @NonNull FeatureType type;

    private @NonNull @Delegate List<Object> values;

    public int getAttributeCount() {
        return values.size();
    }

    public Object getAttribute(int index) {
        return values.get(index);
    }
}
