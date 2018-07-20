/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import static com.google.common.base.Preconditions.checkState;

import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.di.HintsModule;
import org.locationtech.geogig.di.PluginsModule;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.PluginDefaults;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.StorageProvider;
import org.locationtech.geogig.storage.VersionedFormat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.util.Modules;

public class PluginsContextBuilder extends ContextBuilder {

    @Override
    public Context build(Hints hints) {
        return Guice
                .createInjector(Modules.override(new GeogigModule(), new HintsModule(hints))
                        .with(new PluginsModule(), new DefaultPlugins()))
                .getInstance(org.locationtech.geogig.repository.Context.class);
    }

    public static class DefaultPlugins extends AbstractModule {

        private static final StorageProvider DEFAULT_PROVIDER;

        static {
            // hack to set a PluginDefaults to rocksDB without having an explicit dependency
            // on RocksDB modules. This should be removed once the StroageProvider/Plugin
            // mechanisms are reworked.
            StorageProvider storageProvider = null;
            for (StorageProvider provider : StorageProvider.findProviders()) {
                if ("rocksdb".equals(provider.getName())) {
                    // we have a RocksDB provider available, use it as the default
                    storageProvider = provider;
                    break;
                }
            }
            // set the default to the provider found, or null
            DEFAULT_PROVIDER = storageProvider;
        }

        @Override
        protected void configure() {

            if (null != DEFAULT_PROVIDER) {
                // set a PluginDefaults using the default provider
                PluginDefaults pluginDefaults = new PluginDefaults(DEFAULT_PROVIDER);
                bind(PluginDefaults.class).toInstance(pluginDefaults);
            }

            MapBinder<VersionedFormat, RefDatabase> refPlugins = MapBinder
                    .newMapBinder(binder(), VersionedFormat.class, RefDatabase.class)
                    .permitDuplicates();

            MapBinder<VersionedFormat, ObjectDatabase> objectPlugins = MapBinder
                    .newMapBinder(binder(), VersionedFormat.class, ObjectDatabase.class)
                    .permitDuplicates();

            MapBinder<VersionedFormat, IndexDatabase> indexPlugins = MapBinder
                    .newMapBinder(binder(), VersionedFormat.class, IndexDatabase.class)
                    .permitDuplicates();

            MapBinder<VersionedFormat, ConflictsDatabase> conflictsPlugins = MapBinder
                    .newMapBinder(binder(), VersionedFormat.class, ConflictsDatabase.class)
                    .permitDuplicates();

            Iterable<StorageProvider> providers = StorageProvider.findProviders();

            for (StorageProvider sp : providers) {
                VersionedFormat objectDatabaseFormat = sp.getObjectDatabaseFormat();
                VersionedFormat indexDatabaseFormat = sp.getIndexDatabaseFormat();
                VersionedFormat refsDatabaseFormat = sp.getRefsDatabaseFormat();
                VersionedFormat conflictsDatabaseFormat = sp.getConflictsDatabaseFormat();

                if (objectDatabaseFormat != null) {
                    PluginsContextBuilder.bind(objectPlugins, objectDatabaseFormat);
                }
                if (indexDatabaseFormat != null) {
                    PluginsContextBuilder.bind(indexPlugins, indexDatabaseFormat);
                }
                if (refsDatabaseFormat != null) {
                    PluginsContextBuilder.bind(refPlugins, refsDatabaseFormat);
                }
                if (conflictsDatabaseFormat != null) {
                    PluginsContextBuilder.bind(conflictsPlugins, conflictsDatabaseFormat);
                }
            }
        }
    }

    static <T> void bind(MapBinder<VersionedFormat, T> plugins, VersionedFormat format) {
        Class<?> implementingClass = format.getImplementingClass();
        checkState(implementingClass != null,
                "If singleton class not provided, this method must be overritten");
        @SuppressWarnings("unchecked")
        Class<? extends T> binding = (Class<? extends T>) implementingClass;
        plugins.addBinding(format).to(binding).in(Scopes.SINGLETON);
    }

}
