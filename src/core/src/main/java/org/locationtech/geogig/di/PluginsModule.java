/* Copyright (c) 2014 Boundless and others.
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

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
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
        bind(RefDatabase.class).toProvider(PluginRefDatabaseProvider.class).in(Scopes.SINGLETON);
        bind(GraphDatabase.class).toProvider(PluginGraphDatabaseProvider.class)
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
                config = RepositoryResolver.resolveConfigDatabase(uri.get(), context);
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

    private static class PluginGraphDatabaseProvider extends FormatSelector<GraphDatabase> {
        private final PluginDefaults defaults;

        @Inject
        public PluginGraphDatabaseProvider(PluginDefaults defaults, ConfigDatabase config,
                Map<VersionedFormat, Provider<GraphDatabase>> plugins) {
            super(config, plugins);
            this.defaults = defaults;
        }

        @Override
        protected final VersionedFormat readConfig(ConfigDatabase config) {
            final String formatKey = "storage.graph";
            String versionKey = null;
            String format = null, version = null;
            try {
                format = getConfig(formatKey, config).orNull();
                if (format != null) {
                    versionKey = format + ".version";
                    version = getConfig(versionKey, config).orNull();
                }
            } catch (RuntimeException e) {
                // ignore, the config may not be available when we need this
            }

            if (format == null || version == null) {
                // .get, not .orNull. we should only be using the plugin providers when there are
                // plugins set up
                return defaults.getGraph().get();
            }
            for (StorageProvider p : StorageProvider.findProviders()) {
                VersionedFormat graphFormat = p.getGraphDatabaseFormat();
                if (graphFormat != null && format.equals(graphFormat.getFormat())
                        && version.equals(graphFormat.getVersion())) {
                    return graphFormat;
                }
            }

            throw new IllegalStateException(
                    String.format("No storage provider found for %s='%s' and %s='%s'", formatKey,
                            format, versionKey, version));
        }
    }
}
