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

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObjects;

import com.google.flatbuffers.Table;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

abstract @RequiredArgsConstructor class FBRevObject<T extends Table> implements RevObject {

    private int h1;

    private long h2;

    private long h3;

    private final @NonNull T table;

    public @Override ObjectId getId() {
        return ObjectId.create(h1, h2, h3);
    }

    protected T getTable() {
        return table;
    }
    public final @Override boolean equals(java.lang.Object o) {
        return RevObjects.equals(this, o);
    }

    public final @Override int hashCode() {
        return h1;
    }

    void setId(@NonNull ObjectId id) {
        h1 = RevObjects.h1(id);
        h2 = RevObjects.h2(id);
        h3 = RevObjects.h3(id);
    }
}
