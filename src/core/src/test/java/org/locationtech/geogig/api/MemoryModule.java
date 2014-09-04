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

import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.StagingDatabase;
import org.locationtech.geogig.storage.memory.HeapGraphDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectDatabse;
import org.locationtech.geogig.storage.memory.HeapRefDatabase;
import org.locationtech.geogig.storage.memory.HeapStagingDatabase;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

/**
 * @see HeapObjectDatabse
 * @see HeapStagingDatabase
 * @see HeapRefDatabase
 * @see HeapGraphDatabase
 */
public class MemoryModule extends AbstractModule {

    private Platform testPlatform;

    /**
     * @param testPlatform
     */
    public MemoryModule(Platform testPlatform) {
        this.testPlatform = testPlatform;
    }

    @Override
    protected void configure() {
        if (testPlatform != null) {
            bind(Platform.class).toInstance(testPlatform);
        }
        bind(ObjectDatabase.class).to(HeapObjectDatabse.class).in(Scopes.SINGLETON);
        bind(StagingDatabase.class).to(HeapStagingDatabase.class).in(Scopes.SINGLETON);
        bind(RefDatabase.class).to(HeapRefDatabase.class).in(Scopes.SINGLETON);
        bind(GraphDatabase.class).to(HeapGraphDatabase.class).in(Scopes.SINGLETON);
    }

}
