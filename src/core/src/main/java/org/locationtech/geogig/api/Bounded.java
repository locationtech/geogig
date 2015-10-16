/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import com.google.common.base.Optional;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Superinterface for objects pointing to another object in the revision graph
 * 
 * @see Node
 * @see Bucket
 * @see NodeRef
 */
public interface Bounded {

    /**
     * @return the id of the {@link RevObject} this object points to.
     */
    public ObjectId getObjectId();

    public boolean intersects(Envelope env);

    public void expand(Envelope env);
    
    public Optional<Envelope> bounds();
}
