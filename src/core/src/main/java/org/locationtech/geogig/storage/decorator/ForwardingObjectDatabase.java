/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.decorator;

import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;

import lombok.NonNull;

public class ForwardingObjectDatabase extends ForwardingObjectStore implements ObjectDatabase {

    public ForwardingObjectDatabase(@NonNull ObjectDatabase odb) {
        super(odb);
    }

    protected ObjectDatabase subject() {
        return (ObjectDatabase) super.subject();
    }

    public @Override GraphDatabase getGraphDatabase() {
        return subject().getGraphDatabase();
    }

    public @Override BlobStore getBlobStore() {
        return subject().getBlobStore();
    }
}
