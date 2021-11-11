/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage;

import java.util.Optional;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.RevObject;

import lombok.NonNull;

public class DiffObjectInfo<T extends RevObject> {

    private final DiffEntry diffEntry;

    private final T oldValue;

    private final T newValue;

    public DiffObjectInfo(@NonNull DiffEntry diffEntry, T oldValue, T newValue) {
        Preconditions.checkArgument(oldValue != null || newValue != null);
        this.diffEntry = diffEntry;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public DiffEntry entry() {
        return diffEntry;
    }

    public Optional<T> oldValue() {
        return Optional.ofNullable(oldValue);
    }

    public Optional<T> newValue() {
        return Optional.ofNullable(newValue);
    }

    public static <T extends RevObject> DiffObjectInfo<T> of(DiffEntry entry, T oldObject,
            T newObject) {
        return new DiffObjectInfo<T>(entry, oldObject, newObject);
    }
}
