package org.locationtech.geogig.feature;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

public @Value @Builder class FeatureType {

    private @NonNull Name name;

    private @NonNull List<PropertyDescriptor> descriptors;

    public int getSize() {
        return descriptors.size();
    }

    /**
     * @throws IndexOutOfBoundsException
     */
    public PropertyDescriptor getDescriptor(int index) {
        return descriptors.get(index);
    }

    /**
     * @throws NoSuchElementException
     */
    public PropertyDescriptor getDescriptor(@NonNull String attName) {
        return descriptors.stream().filter(d -> attName.equals(d.getName().getLocalPart()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        String.format("Attribute '%s' does not exist: %s", attName,
                                descriptors.stream().map(d -> d.getName().getLocalPart())
                                        .collect(Collectors.joining(", ")))));
    }
}
