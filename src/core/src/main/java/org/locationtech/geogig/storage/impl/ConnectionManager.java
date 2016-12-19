/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A connection manager for ensuring that connections are acquired or released in a threadsafe way.
 * The manager is parametric in A, the type of Address specifying a connection, and C, the actual
 * connection type.
 * 
 * The address type A should be suitable for use as a map key (that is, have value-based equals()
 * and hashCode() implementations which are consistent with each other.)
 * 
 * Implementors should use the @Singleton scope with this class when configuring Guice.
 */
public abstract class ConnectionManager<A, C> {
    protected abstract C connect(A address);

    protected abstract void disconnect(C connection);

    private static class PoolEntry<C> {
        public final C connection;

        public int clients;

        public PoolEntry(C connection) {
            this.connection = connection;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[clients:" + clients + ", connection: "
                    + connection + "]";
        }
    }

    private Map<A, PoolEntry<C>> pool = new ConcurrentHashMap<>();

    private Map.Entry<A, PoolEntry<C>> lookupConnection(C connection) {
        for (Map.Entry<A, PoolEntry<C>> entry : pool.entrySet()) {
            if (entry.getValue().connection == connection) {
                return entry;
            }
        }
        throw new IllegalStateException(
                "Attempted to retrieve connection that is not managed by this manager");
    }

    public final synchronized C acquire(A address) {
        PoolEntry<C> entry = pool.get(address);
        if (entry == null) {
            C connection = connect(address);
            entry = new PoolEntry<C>(connection);
            pool.put(address, entry);
        }
        entry.clients += 1;
        return entry.connection;
    }

    public final synchronized boolean release(C connection) {
        Map.Entry<A, PoolEntry<C>> record = lookupConnection(connection);
        A address = record.getKey();
        PoolEntry<C> poolentry = record.getValue();
        poolentry.clients -= 1;
        if (poolentry.clients < 0)
            throw new IllegalStateException("Negative client count for connection pool entry!");
        if (poolentry.clients == 0) {
            try {
                disconnect(poolentry.connection);
            } finally {
                pool.remove(address);
            }
            return true;
        }
        return false;
    }
}
