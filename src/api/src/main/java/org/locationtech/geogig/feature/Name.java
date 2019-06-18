package org.locationtech.geogig.feature;

import com.google.common.base.Strings;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

public @Value @AllArgsConstructor(access = AccessLevel.PRIVATE) class Name {

    private String namespaceURI;

    private @NonNull String localPart;

    public Name(@NonNull String localPart) {
        this(null, localPart);
    }

    public @Override String toString() {
        return namespaceURI == null ? localPart : namespaceURI + "#" + localPart;
    }

    public static Name valueOf(final @NonNull String qname) {
        String namespace = null;
        String name = qname;
        if (qname.contains("#")) {
            namespace = qname.substring(0, name.indexOf('#'));
            name = qname.substring(name.indexOf('#') + 1);
        }
        return new Name(namespace, name);
    }

    public static Name valueOf(String namespaceURI, final @NonNull String localName) {
        String namespace = Strings.isNullOrEmpty(namespaceURI) ? null : namespaceURI;
        return new Name(namespace, localName);
    }
}
