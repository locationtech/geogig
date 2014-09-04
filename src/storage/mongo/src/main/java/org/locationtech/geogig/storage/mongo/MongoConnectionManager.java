/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.mongo;

import java.net.UnknownHostException;

import org.locationtech.geogig.storage.ConnectionManager;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

/**
 * A connection manager for MongoDB-backed storage objects.
 */
public final class MongoConnectionManager extends
        ConnectionManager<MongoAddress, MongoClient> {
    @Override
    protected MongoClient connect(MongoAddress address) {
        try {
            MongoClientURI uri = new MongoClientURI(address.getUri());
            return new MongoClient(uri);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void disconnect(MongoClient client) {
        client.close();
    }
}
