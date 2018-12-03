/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ObjectStore;

/**
 * Gets the object type of the object that matches the given {@link ObjectId}.
 */
public class ResolveObjectType extends AbstractGeoGigOp<RevObject.TYPE> {

    private ObjectId oid;

    private ObjectStore source = null;

    /**
     * @param oid the {@link ObjectId object id} of the object to check
     * @return {@code this}
     */
    public ResolveObjectType setObjectId(ObjectId oid) {
        this.oid = oid;
        return this;
    }

    /**
     * @param source the object store to use
     * @return {@code this}
     */
    public ResolveObjectType setSource(final ObjectStore source) {
        this.source = source;
        return this;
    }

    /**
     * Executes the command.
     * 
     * @return the type of the object specified by the object id.
     * @throws IllegalArgumentException if the object doesn't exist
     */
    @Override
    protected TYPE _call() {
        @SuppressWarnings("resource")
        ObjectStore store = this.source == null ? objectDatabase() : this.source;
        RevObject o = store.get(oid);
        return o.getType();
    }
}
