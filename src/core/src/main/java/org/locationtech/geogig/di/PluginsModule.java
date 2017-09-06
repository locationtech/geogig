/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.di;

import java.net.URI;
import java.util.Map;

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.PluginDefaults;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.StorageProvider;
import org.locationtech.geogig.storage.VersionedFormat;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;

import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Scopes;

public class PluginsModule extends AbstractModule {

    @Override
    protected void configure() {

        bind(ConfigDatabase.class).toProvider(PluginConfigDatabaseProvider.class)
                .in(Scopes.SINGLETON);
        bind(ObjectDatabase.class).toProvider(PluginObjectDatabaseProvider.class)
                .in(Scopes.SINGLETON);
        bind(IndexDatabase.class).toProvider(PluginIndexDatabaseProvider.class)
                .in(Scopes.SINGLETON);
        bind(RefDatabase.class).toProvider(PluginRefDatabaseProvider.class).in(Scopes.SINGLETON);
        bind(ConflictsDatabase.class).toProvider(PluginConflictsDatabaseProvider.class)
                .in(Scopes.SINGLETON);
    }

    private static class PluginConfigDatabaseProvider implements Provider<ConfigDatabase> {

        private Context context;

        private Hints hints;

        @Inject
        PluginConfigDatabaseProvider(Context context, Hints hints) {
            this.context = context;
            this.hints = hints;
        }

        @Override
        public ConfigDatabase get() {
            Platform platform = context.platform();

            Optional<URI> uri = new ResolveGeogigURI(platform, hints).call();
            ConfigDatabase config = null;
            if (uri.isPresent()) {
                config = RepositoryResolver.resolveConfigDatabase(uri.get(), context, false);
            } else {
                // if there's no repository URI, then we can only do global operations
                config = IniFileConfigDatabase.globalOnly(platform);
            }
            return config;
        }
    }

    private static class PluginObjectDatabaseProvider extends FormatSelector<ObjectDatabase> {
        private final PluginDefaults defaults;

        @Inject
        public PluginObjectDatabaseProvider(PluginDefaults defaults, ConfigDatabase config,
                Map<VersionedFormat, Provider<ObjectDatabase>> plugins) {
            super(config, plugins);
            this.defaults = defaults;
        }

        @Override
        protected final VersionedFormat readConfig(ConfigDatabase config) {
            final String formatKey = "storage.objects";
            String versionKey = null;
            String format = null, version = null;
            try {
                format = getConfig(formatKey, config).orNull();
                if (format != null) {
                    versionKey = format + ".version";
                    version = getConfig(versionKey, config).orNull();
                }
            } catch (RuntimeException e) {
                // ignore, the config may not be available when we need this.
            }
            if (format == null || version == null) {
                // .get, not .orNull. we should only be using the plugin providers when there are
                // plugins set up
                return defaults.getObjects().get();
            }

            for (StorageProvider p : StorageProvider.findProviders()) {
                VersionedFormat objectFormat = p.getObjectDatabaseFormat();
                if (objectFormat != null && format.equals(objectFormat.getFormat())
                        && version.equals(objectFormat.getVersion())) {
                    return objectFormat;
                }
            }
            throw new IllegalStateException(
                    String.format("No storage provider found for %s='%s' and %s='%s'", formatKey,
                            format, versionKey, version));
        }

    }

    private static class PluginIndexDatabaseProvider extends FormatSelector<IndexDatabase> {
        private final PluginDefaults defaults;

        @Inject
        public PluginIndexDatabaseProvider(PluginDefaults defaults, ConfigDatabase config,
                Map<VersionedFormat, Provider<IndexDatabase>> plugins) {
            super(config, plugins);
            this.defaults = defaults;
        }

        @Override
        protected final VersionedFormat readConfig(ConfigDatabase config) {
            final String formatKey = "storage.index";
            String versionKey = null;

            String format = null, version = null;
            try {
                format = getConfig(formatKey, config).orNull();
                if (format != null) {
                    versionKey = format + ".version";
                    version = getConfig(versionKey, config).orNull();
                }
            } catch (RuntimeException e) {
                // ignore, the config may not be available when we need this.
            }
            if (format == null || version == null) {
                // .get, not .orNull. we should only be using the plugin providers when there are
                // plugins set up
                return defaults.getIndex().get();
            }

            for (StorageProvider p : StorageProvider.findProviders()) {
                VersionedFormat indexFormat = p.getIndexDatabaseFormat();
                if (indexFormat != null && format.equals(indexFormat.getFormat())
                        && version.equals(indexFormat.getVersion())) {
                    return indexFormat;
                }
            }
            throw new IllegalStateException(
                    String.format("No storage provider found for %s='%s' and %s='%s'", formatKey,
                            format, versionKey, version));
        }

    }

    private static class PluginRefDatabaseProvider extends FormatSelector<RefDatabase> {
        private final PluginDefaults defaults;

        @Inject
        public PluginRefDatabaseProvider(PluginDefaults defaults, ConfigDatabase config,
                Map<VersionedFormat, Provider<RefDatabase>> plugins) {
            super(config, plugins);
            this.defaults = defaults;
        }

        @Override
        protected final VersionedFormat readConfig(ConfigDatabase config) {
            final String formatKey = "storage.refs";
            String versionKey = null;
            String format = null, version = null;
            try {
                format = getConfig(formatKey, config).orNull();
                if (format != null) {
                    versionKey = format + ".version";
                    version = getConfig(versionKey, config).orNull();
                }
            } catch (RuntimeException e) {
                // ignore, the config may not be available when we need this.
            }

            if (format == null || version == null) {
                // .get, not .orNull. we should only be using the plugin providers when there are
                // plugins set up
                return defaults.getRefs().get();
            }

            for (StorageProvider p : StorageProvider.findProviders()) {
                VersionedFormat refsFormat = p.getRefsDatabaseFormat();
                if (refsFormat != null && format.equals(refsFormat.getFormat())
                        && version.equals(refsFormat.getVersion())) {
                    return refsFormat;
                }
            }

            throw new IllegalStateException(
                    String.format("No storage provider found for %s='%s' and %s='%s'", formatKey,
                            format, versionKey, version));
        }
    }

    private static class PluginConflictsDatabaseProvider extends FormatSelector<ConflictsDatabase> {

        private PluginDefaults defaults;

        @Inject
        public PluginConflictsDatabaseProvider(PluginDefaults defaults, ConfigDatabase config,
                Map<VersionedFormat, Provider<ConflictsDatabase>> plugins) {
            super(config, plugins);
            this.defaults = defaults;
        }

        @Override
        protected final VersionedFormat readConfig(ConfigDatabase config) {
            // reuse storage.objects config key in order to resolve the StorageProvider, instead of
            // introducing a new config key. The plugin mechanism will be simplified to use
            // RepositoryResolver to load the StorageProvider based on the repository URI so it
            // makes no sense to introduce a new config key and deal with the backward
            // compatibility issues now.
            final String formatKey = "storage.objects";
            String versionKey = null;
            String format = null, version = null;
            try {
                format = getConfig(formatKey, config).orNull();
                if (format != null) {
                    versionKey = format + ".version";
                    version = getConfig(versionKey, config).orNull();
                }
            } catch (RuntimeException e) {
                // ignore, the config may not be available when we need this.
            }

            if (format == null || version == null) {
                // .get, not .orNull. we should only be using the plugin providers when there are
                // plugins set up
                return defaults.getRefs().get();
            }

            for (StorageProvider p : StorageProvider.findProviders()) {
                VersionedFormat conflictsFormat = p.getConflictsDatabaseFormat();
                final String fmt = conflictsFormat.getFormat();
                final String v = conflictsFormat.getVersion();
                if (format.equals(fmt) && version.equals(v)) {
                    return conflictsFormat;
                }
            }

            throw new IllegalStateException(
                    String.format("No storage provider found for %s='%s' and %s='%s'", formatKey,
                            format, versionKey, version));
        }
    }
}
