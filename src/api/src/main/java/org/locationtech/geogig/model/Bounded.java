/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;

import lombok.NonNull;

/**
 * Super-interface for objects pointing to another object (nodes or buckets) in a {@link RevTree}.
 * 
 * @see Node
 * @see Bucket
 * @see NodeRef
 * @since 1.0
 */
public interface Bounded {

    /**
     * @return the id of the {@link RevObject} this object points to.
     */
    public @NonNull ObjectId getObjectId();

    /**
     * TODO: move to {@link RevObjects} to keep the model objects clear of implementation details
     */
    public boolean intersects(Envelope env);

    /**
     * TODO: move to {@link RevObjects} to keep the model objects clear of implementation details
     */
    public void expand(Envelope env);

    /**
     * @return the spatial envelope of the revision object this object points to, if any.
     */
    public Optional<Envelope> bounds();
}
