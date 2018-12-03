/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;

import lombok.NonNull;

/**
 * Base object type accessed during revision walking.
 * 
 * @see RevCommit
 * @see RevTree
 * @see RevFeature
 * @see RevFeatureType
 * @see RevTag
 */
public abstract class AbstractRevObject implements RevObject {

    private final int h1;

    private final long h2;

    private final long h3;

    public AbstractRevObject(final @NonNull ObjectId id) {
        this.h1 = RevObjects.h1(id);
        this.h2 = RevObjects.h2(id);
        this.h3 = RevObjects.h3(id);
    }

    /**
     * Get the name of this object.
     * 
     * @return unique hash of this object.
     */
    public final ObjectId getId() {
        return ObjectId.create(h1, h2, h3);
    }

    public final @Override boolean equals(Object o) {
        return RevObjects.equals(this, o);
    }

    public final @Override int hashCode() {
        return h1;
    }
}
