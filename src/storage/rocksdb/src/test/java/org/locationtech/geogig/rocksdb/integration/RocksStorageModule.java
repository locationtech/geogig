/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb.integration;

import java.io.File;
import java.net.URI;

import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.di.HintsModule;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.rocksdb.RocksdbConflictsDatabase;
import org.locationtech.geogig.rocksdb.RocksdbIndexDatabase;
import org.locationtech.geogig.rocksdb.RocksdbObjectDatabase;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.fs.FileRefDatabase;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.test.TestPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;

/**
 * Module for the Rocksdb storage backend.
 */
public class RocksStorageModule extends AbstractModule {

    static Logger LOG = LoggerFactory.getLogger(RocksStorageModule.class);

    @Override
    protected void configure() {
        bind(ConfigDatabase.class).to(IniFileConfigDatabase.class).in(Scopes.SINGLETON);
        bind(RefDatabase.class).to(FileRefDatabase.class).in(Scopes.SINGLETON);
        bind(ObjectDatabase.class).to(RocksdbObjectDatabase.class).in(Scopes.SINGLETON);
        bind(IndexDatabase.class).to(RocksdbIndexDatabase.class).in(Scopes.SINGLETON);
        bind(ConflictsDatabase.class).to(RocksdbConflictsDatabase.class).in(Scopes.SINGLETON);
    }

    static Context createContext(File repositoryDirectory) {
        Platform platform = new TestPlatform(repositoryDirectory);
        URI uri = repositoryDirectory.getAbsoluteFile().toURI();
        Hints hints = new Hints().uri(uri).platform(platform);

        return Guice.createInjector(Modules.override(new GeogigModule())
                .with(new HintsModule(hints), new RocksStorageModule())).getInstance(Context.class);
    }

}
