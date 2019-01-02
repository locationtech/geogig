/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.flatbuffers;

import org.locationtech.geogig.flatbuffers.generated.v1.Person;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevPerson;

import com.google.common.base.Optional;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

final @RequiredArgsConstructor class FBPerson implements RevPerson {

    private final @NonNull Person p;

    public @Override Optional<String> getName() {
        return Optional.fromNullable(p.name());
    }

    public @Override Optional<String> getEmail() {
        return Optional.fromNullable(p.email());
    }

    public @Override long getTimestamp() {
        return p.timestamp();
    }

    public @Override int getTimeZoneOffset() {
        return p.timezoneOffset();
    }

    public @Override boolean equals(Object o) {
        return (o instanceof RevPerson) && RevObjects.equals(this, ((RevPerson) o));
    }

    public @Override int hashCode() {
        return RevObjects.hashCode(this);
    }

    public @Override String toString() {
        return RevObjects.toString(this);
    }
}
