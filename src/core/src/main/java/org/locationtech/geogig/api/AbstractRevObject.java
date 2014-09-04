/* Copyright (c) 2012-2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

/**
 * Base object type accessed during revision walking.
 * 
 * @see RevCommit
 * @see RevTree
 * @see RevFeature
 * @see RevTag
 */
public abstract class AbstractRevObject implements RevObject {
    private final ObjectId id;

    public AbstractRevObject(final ObjectId id) {
        this.id = id;
    }

    /**
     * Get the name of this object.
     * 
     * @return unique hash of this object.
     */
    public final ObjectId getId() {
        return id;
    }

    /**
     * Equality is based on id
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AbstractRevObject)) {
            return false;
        }
        return id.equals(((AbstractRevObject) o).getId());
    }
}
