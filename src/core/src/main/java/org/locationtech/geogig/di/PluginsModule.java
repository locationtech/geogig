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
import org.locationtech.geogig.repository.RepositoryInitializer;
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

        bind(ConfigDatabase.class).toProvider(PluginConfigDatabaseProvider.class).in(
                Scopes.SINGLETON);
        bind(ObjectDatabase.class).toProvider(PluginObjectDatabaseProvider.class).in(
                Scopes.SINGLETON);
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
                config = RepositoryInitializer.resolveConfigDatabase(uri.get(), context);
            } else {
                config = new IniFileConfigDatabase(platform);
            }
            return config;
        }
    }

    private static class PluginObjectDatabaseProvider extends FormatSelector<ObjectDatabase> {
        private final PluginDefaults defaults;

        @Override
        protected final VersionedFormat readConfig(ConfigDatabase config) {
            String format = null, version = null;
            try {
                String formatKey = "storage.objects";
                format = config.get(formatKey).or(config.getGlobal(formatKey).orNull());
                if (format != null) {
                    String versionKey = format + ".version";
                    version = config.get(versionKey).or(config.getGlobal(versionKey).orNull());
                }
            } catch (RuntimeException e) {
                // ignore, the config may not be available when we need this.
            }
            if (format == null || version == null) {
                // .get, not .orNull. we should only be using the plugin providers when there are
                // plugins set up
                return defaults.getObjects().get();
            } else {
                return new VersionedFormat(format, version);
            }
        }

        @Inject
        public PluginObjectDatabaseProvider(PluginDefaults defaults, ConfigDatabase config,
                Map<VersionedFormat, Provider<ObjectDatabase>> plugins) {
            super(config, plugins);
            this.defaults = defaults;
        }
    }

    private static class PluginRefDatabaseProvider extends FormatSelector<RefDatabase> {
        private final PluginDefaults defaults;

        @Override
        protected final VersionedFormat readConfig(ConfigDatabase config) {
            String format = null, version = null;
            try {
                String formatKey = "storage.refs";
                format = config.get(formatKey).or(config.getGlobal(formatKey).orNull());

                if (format != null) {
                    String versionKey = format + ".version";
                    version = config.get(versionKey).or(config.getGlobal(versionKey).orNull());
                }
            } catch (RuntimeException e) {
                // ignore, the config may not be available when we need this.
            }

            if (format == null || version == null) {
                // .get, not .orNull. we should only be using the plugin providers when there are
                // plugins set up
                return defaults.getRefs().get();
            } else {
                return new VersionedFormat(format, version);
            }
        }

        @Inject
        public PluginRefDatabaseProvider(PluginDefaults defaults, ConfigDatabase config,
                Map<VersionedFormat, Provider<RefDatabase>> plugins) {
            super(config, plugins);
            this.defaults = defaults;
        }
    }

    private static class PluginGraphDatabaseProvider extends FormatSelector<GraphDatabase> {
        private final PluginDefaults defaults;

        @Override
        protected final VersionedFormat readConfig(ConfigDatabase config) {
            String format = null, version = null;
            try {
                String key = "storage.graph";
                format = config.get(key).or(config.getGlobal(key).orNull());
                if (format != null) {
                    String versionKey = format + ".version";
                    version = config.get(versionKey).or(config.getGlobal(versionKey).orNull());
                }
            } catch (RuntimeException e) {
                // ignore, the config may not be available when we need this
            }

            if (format == null || version == null) {
                // .get, not .orNull. we should only be using the plugin providers when there are
                // plugins set up
                return defaults.getGraph().get();
            } else {
                return new VersionedFormat(format, version);
            }
        }

        @Inject
        public PluginGraphDatabaseProvider(PluginDefaults defaults, ConfigDatabase config,
                Map<VersionedFormat, Provider<GraphDatabase>> plugins) {
            super(config, plugins);
            this.defaults = defaults;
        }
    }
}
