/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.cli;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ContextBuilder;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.di.PluginDefaults;
import org.locationtech.geogig.di.PluginsModule;
import org.locationtech.geogig.di.VersionedFormat;
import org.locationtech.geogig.di.caching.CachingModule;
import org.locationtech.geogig.metrics.MetricsModule;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.StagingDatabase;
import org.locationtech.geogig.storage.bdbje.JEGraphDatabase_v0_1;
import org.locationtech.geogig.storage.bdbje.JEGraphDatabase_v0_2;
import org.locationtech.geogig.storage.bdbje.JEObjectDatabase_v0_1;
import org.locationtech.geogig.storage.bdbje.JEObjectDatabase_v0_2;
import org.locationtech.geogig.storage.bdbje.JEStagingDatabase_v0_1;
import org.locationtech.geogig.storage.bdbje.JEStagingDatabase_v0_2;
import org.locationtech.geogig.storage.fs.FileRefDatabase;
import org.locationtech.geogig.storage.mongo.MongoGraphDatabase;
import org.locationtech.geogig.storage.mongo.MongoObjectDatabase;
import org.locationtech.geogig.storage.mongo.MongoStagingDatabase;
import org.locationtech.geogig.storage.sqlite.SQLiteStorage;
import org.locationtech.geogig.storage.sqlite.XerialGraphDatabase;
import org.locationtech.geogig.storage.sqlite.XerialObjectDatabase;
import org.locationtech.geogig.storage.sqlite.XerialStagingDatabase;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.util.Modules;

public class CLIContextBuilder extends ContextBuilder {

    private static final VersionedFormat DEFAULT_REFS = new VersionedFormat("file", "1.0");

    private static final VersionedFormat DEFAULT_OBJECTS = new VersionedFormat("bdbje", "0.2");

    private static final VersionedFormat DEFAULT_STAGING = new VersionedFormat("bdbje", "0.2");

    private static final VersionedFormat DEFAULT_GRAPH = new VersionedFormat("bdbje", "0.2");

    private static final PluginDefaults defaults = new PluginDefaults(DEFAULT_OBJECTS,//
            DEFAULT_STAGING,//
            DEFAULT_REFS,//
            DEFAULT_GRAPH);

    @Override
    public Context build(Hints hints) {
        return Guice.createInjector(
                Modules.override(new GeogigModule(), new CachingModule()).with(new MetricsModule(),
                        new PluginsModule(), new DefaultPlugins(), new HintsModule(hints)))
                .getInstance(org.locationtech.geogig.api.Context.class);
    }

    public static class DefaultPlugins extends AbstractModule {
        @Override
        protected void configure() {
            bind(PluginDefaults.class).toInstance(defaults);
            MapBinder<VersionedFormat, RefDatabase> refPlugins = MapBinder.newMapBinder(binder(),
                    VersionedFormat.class, RefDatabase.class);
            refPlugins //
                    .addBinding(DEFAULT_REFS)//
                    .to(FileRefDatabase.class)//
                    .in(Scopes.SINGLETON);
            MapBinder<VersionedFormat, ObjectDatabase> objectPlugins = MapBinder.newMapBinder(
                    binder(), VersionedFormat.class, ObjectDatabase.class);
            objectPlugins //
                    .addBinding(new VersionedFormat("bdbje", "0.2"))//
                    .to(JEObjectDatabase_v0_2.class)//
                    .in(Scopes.SINGLETON);//
            objectPlugins //
                    .addBinding(new VersionedFormat("bdbje", "0.1"))//
                    .to(JEObjectDatabase_v0_1.class)//
                    .in(Scopes.SINGLETON);
            objectPlugins //
                    .addBinding(new VersionedFormat("mongodb", "0.1"))//
                    .to(MongoObjectDatabase.class)//
                    .in(Scopes.SINGLETON);
            objectPlugins //
                    .addBinding(
                            new VersionedFormat(SQLiteStorage.FORMAT_NAME, SQLiteStorage.VERSION))//
                    .to(XerialObjectDatabase.class)//
                    .in(Scopes.SINGLETON);
            MapBinder<VersionedFormat, StagingDatabase> stagingPlugins = MapBinder.newMapBinder(
                    binder(), VersionedFormat.class, StagingDatabase.class);
            stagingPlugins //
                    .addBinding(new VersionedFormat("mongodb", "0.1"))//
                    .to(MongoStagingDatabase.class)//
                    .in(Scopes.SINGLETON);
            stagingPlugins //
                    .addBinding(new VersionedFormat("bdbje", "0.2"))//
                    .to(JEStagingDatabase_v0_2.class)//
                    .in(Scopes.SINGLETON);
            stagingPlugins //
                    .addBinding(new VersionedFormat("bdbje", "0.1"))//
                    .to(JEStagingDatabase_v0_1.class)//
                    .in(Scopes.SINGLETON);
            stagingPlugins //
                    .addBinding(
                            new VersionedFormat(SQLiteStorage.FORMAT_NAME, SQLiteStorage.VERSION))//
                    .to(XerialStagingDatabase.class)//
                    .in(Scopes.SINGLETON);
            MapBinder<VersionedFormat, GraphDatabase> graphPlugins = MapBinder.newMapBinder(
                    binder(), VersionedFormat.class, GraphDatabase.class);
            graphPlugins //
                    .addBinding(new VersionedFormat("bdbje", "0.2")) //
                    .to(JEGraphDatabase_v0_2.class) //
                    .in(Scopes.SINGLETON);
            graphPlugins //
                    .addBinding(new VersionedFormat("bdbje", "0.1")) //
                    .to(JEGraphDatabase_v0_1.class) //
                    .in(Scopes.SINGLETON);
            graphPlugins //
                    .addBinding(new VersionedFormat("mongodb", "0.1")) //
                    .to(MongoGraphDatabase.class) //
                    .in(Scopes.SINGLETON);
            graphPlugins //
                    .addBinding(
                            new VersionedFormat(SQLiteStorage.FORMAT_NAME, SQLiteStorage.VERSION)) //
                    .to(XerialGraphDatabase.class) //
                    .in(Scopes.SINGLETON);
        }
    }
}
