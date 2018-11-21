/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.postgresql.v9.PGConfigDatabase;
import org.locationtech.geogig.storage.postgresql.v9.PGConflictsDatabase;
import org.locationtech.geogig.storage.postgresql.v9.PGIndexDatabase;
import org.locationtech.geogig.storage.postgresql.v9.PGObjectDatabase;
import org.locationtech.geogig.storage.postgresql.v9.PGRefDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

/**
 * Module for the PostgreSQL storage backend.
 */
public class PGStorageModule extends AbstractModule {

    static Logger LOG = LoggerFactory.getLogger(PGStorageModule.class);

    @Override
    protected void configure() {
        bind(ConfigDatabase.class).to(PGConfigDatabase.class).in(Scopes.SINGLETON);
        bind(RefDatabase.class).to(PGRefDatabase.class).in(Scopes.SINGLETON);
        bind(ObjectDatabase.class).to(PGObjectDatabase.class).in(Scopes.SINGLETON);
        bind(IndexDatabase.class).to(PGIndexDatabase.class).in(Scopes.SINGLETON);
        bind(ConflictsDatabase.class).to(PGConflictsDatabase.class).in(Scopes.SINGLETON);
    }

}
