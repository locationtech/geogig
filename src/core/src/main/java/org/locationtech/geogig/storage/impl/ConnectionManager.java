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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.NonNull;

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
        public final @NonNull C connection;

        private final AtomicInteger clients = new AtomicInteger();

        public PoolEntry(C connection) {
            this.connection = connection;
        }

        public void acquired() {
            this.clients.incrementAndGet();
        }

        public int released() {
            return this.clients.decrementAndGet();
        }

        public @Override String toString() {
            return getClass().getSimpleName() + "[clients:" + clients.get() + ", connection: "
                    + connection + "]";
        }
    }

    private ConcurrentHashMap<A, PoolEntry<C>> pool = new ConcurrentHashMap<>();

    public final C acquire(A address) {
        PoolEntry<C> entry = pool.computeIfAbsent(address, this::connectInternal);
        entry.acquired();
        return entry.connection;
    }

    private PoolEntry<C> connectInternal(A address) {
        C connection = connect(address);
        return new PoolEntry<C>(connection);
    }

    public final void releaseAll() {
        ConcurrentHashMap<A, PoolEntry<C>> pool = this.pool;
        this.pool = new ConcurrentHashMap<>();
        pool.values().forEach(v -> disconnect(v.connection));
    }

    public final boolean release(C connection) {
        return pool.entrySet().removeIf(e -> {
            if (e.getValue().connection == connection) {
                int remaining = e.getValue().released();
                if (remaining < 0) {
                    throw new IllegalStateException(
                            "Negative client count for connection pool entry!");
                }
                if (remaining == 0) {
                    disconnect(e.getValue().connection);
                    return true;
                }
            }
            return false;
        });
    }
}
