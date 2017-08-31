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

import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.storage.memory.HeapConfigDatabase;
import org.locationtech.geogig.storage.memory.HeapConflictsDatabase;
import org.locationtech.geogig.storage.memory.HeapIndexDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapRefDatabase;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

/**
 * @see HeapConfigDatabase
 * @see HeapObjectDatabase
 * @see HeapRefDatabase
 * @see HeapIndexDatabase
 */
public class MemoryModule extends AbstractModule {

    @Override
    protected void configure() {
        // TODO: bind to HeapConfigDatabase
        // bind(ConfigDatabase.class).to(HeapConfigDatabase.class).in(Scopes.SINGLETON);
        bind(ConfigDatabase.class).to(IniFileConfigDatabase.class).in(Scopes.SINGLETON);
        bind(ObjectDatabase.class).to(HeapObjectDatabase.class).in(Scopes.SINGLETON);
        bind(RefDatabase.class).to(HeapRefDatabase.class).in(Scopes.SINGLETON);
        bind(IndexDatabase.class).to(HeapIndexDatabase.class).in(Scopes.SINGLETON);
        bind(ConflictsDatabase.class).to(HeapConflictsDatabase.class).in(Scopes.SINGLETON);
    }
}
