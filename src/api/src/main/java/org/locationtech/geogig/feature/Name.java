package org.locationtech.geogig.feature;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

public @Value @AllArgsConstructor class Name {

    private String namespaceURI;

    private @NonNull String localPart;

    public Name(@NonNull String localPart) {
        this(null, localPart);
    }
}
