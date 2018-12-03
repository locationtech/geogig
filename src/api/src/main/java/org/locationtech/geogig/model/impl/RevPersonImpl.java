/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevPerson;

import com.google.common.base.Optional;

import lombok.Value;

/**
 * The GeoGig identity of a single individual, composed of a name and email address.
 */
final @Value class RevPersonImpl implements RevPerson {

    private final Optional<String> name;

    private final Optional<String> email;

    private final long timestamp;

    private final int timeZoneOffset;

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
