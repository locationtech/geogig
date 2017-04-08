/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test;

import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.impl.SynchronizedGraphDatabase;
import org.locationtech.geogig.storage.memory.HeapGraphDatabase;
import org.locationtech.geogig.storage.memory.HeapIndexDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapRefDatabase;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Scopes;

/**
 * @see HeapObjectDatabase
 * @see HeapRefDatabase
 * @see HeapGraphDatabase
 * @see HeapIndexDatabase
 */
public class MemoryModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ObjectDatabase.class).to(HeapObjectDatabase.class).in(Scopes.SINGLETON);
        bind(RefDatabase.class).to(HeapRefDatabase.class).in(Scopes.SINGLETON);
        bind(GraphDatabase.class).toProvider(GraphDatabaseProvider.class).in(Scopes.SINGLETON);
        bind(IndexDatabase.class).to(HeapIndexDatabase.class).in(Scopes.SINGLETON);
    }

    /**
     * Provider that wraps a HeapGraphDatabase in a SynchronizedGraphDatabase.
     */
    private static class GraphDatabaseProvider implements Provider<GraphDatabase> {

        private final Platform platform;

        @Inject
        GraphDatabaseProvider(Platform platform) {
            this.platform = platform;
        }

        @Override
        public GraphDatabase get() {
            return new SynchronizedGraphDatabase(new HeapGraphDatabase(platform));
        }

    }
}
