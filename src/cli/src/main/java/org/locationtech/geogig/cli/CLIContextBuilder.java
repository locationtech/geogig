/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ContextBuilder;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.di.HintsModule;
import org.locationtech.geogig.di.PluginDefaults;
import org.locationtech.geogig.di.PluginsModule;
import org.locationtech.geogig.di.StorageProvider;
import org.locationtech.geogig.di.VersionedFormat;
import org.locationtech.geogig.di.caching.CachingModule;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.bdbje.JEStorageProviderV02;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.util.Modules;

public class CLIContextBuilder extends ContextBuilder {

    @Override
    public Context build(Hints hints) {
        return Guice
                .createInjector(Modules
                        .override(new GeogigModule(), new CachingModule(), new HintsModule(hints))
                        .with(new PluginsModule(), new DefaultPlugins()))
                .getInstance(org.locationtech.geogig.api.Context.class);
    }

    public static class DefaultPlugins extends AbstractModule {

        @Override
        protected void configure() {

            final PluginDefaults defaults = new PluginDefaults(new JEStorageProviderV02());
            bind(PluginDefaults.class).toInstance(defaults);

            MapBinder<VersionedFormat, RefDatabase> refPlugins = MapBinder
                    .newMapBinder(binder(), VersionedFormat.class, RefDatabase.class)
                    .permitDuplicates();

            MapBinder<VersionedFormat, ObjectDatabase> objectPlugins = MapBinder
                    .newMapBinder(binder(), VersionedFormat.class, ObjectDatabase.class)
                    .permitDuplicates();

            MapBinder<VersionedFormat, GraphDatabase> graphPlugins = MapBinder
                    .newMapBinder(binder(), VersionedFormat.class, GraphDatabase.class)
                    .permitDuplicates();

            Iterable<StorageProvider> providers = StorageProvider.findProviders();

            for (StorageProvider sp : providers) {
                VersionedFormat objectDatabaseFormat = sp.getObjectDatabaseFormat();
                VersionedFormat graphDatabaseFormat = sp.getGraphDatabaseFormat();
                VersionedFormat refsDatabaseFormat = sp.getRefsDatabaseFormat();

                if (objectDatabaseFormat != null) {
                    objectDatabaseFormat.bind(objectPlugins);
                }
                if (graphDatabaseFormat != null) {
                    graphDatabaseFormat.bind(graphPlugins);
                }
                if (refsDatabaseFormat != null) {
                    refsDatabaseFormat.bind(refPlugins);
                }
            }
        }
    }
}
