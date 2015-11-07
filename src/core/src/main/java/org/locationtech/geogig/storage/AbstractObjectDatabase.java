/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

/**
 * Provides a base implementation for different representations of the {@link ObjectDatabase}.
 * 
 * @see ObjectDatabase
 */
public abstract class AbstractObjectDatabase extends AbstractObjectStore implements ObjectDatabase {

    public AbstractObjectDatabase(final ObjectSerializingFactory serializer) {
        super(serializer);
    }

    /**
     * @return a newly constructed {@link ObjectInserter} for this database
     * @see org.locationtech.geogig.storage.ObjectDatabase#newObjectInserter()
     * @deprecated
     */
    @Override
    public ObjectInserter newObjectInserter() {
        return new ObjectInserter(this);
    }
}
