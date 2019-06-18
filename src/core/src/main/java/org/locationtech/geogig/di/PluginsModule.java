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
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.inject.Inject;

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.RepositoryFinder;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Scopes;

public class PluginsModule extends AbstractModule {

    protected @Override void configure() {

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

        public @Override ConfigDatabase get() {
            Platform platform = context.platform();

            Optional<URI> uri = new ResolveGeogigURI(platform, hints).call();
            ConfigDatabase config = null;
            if (uri.isPresent()) {
                config = RepositoryFinder.INSTANCE.resolveConfigDatabase(uri.get(), context, false);
            } else {
                // if there's no repository URI, then we can only do global operations
                config = IniFileConfigDatabase.globalOnly(platform);
            }
            return config;
        }
    }

    private static class PluginObjectDatabaseProvider implements Provider<ObjectDatabase> {

        private @Inject Platform platform;

        private @Inject Hints hints;

        public @Override ObjectDatabase get() {
            URI repoURI = new ResolveGeogigURI(platform, hints).call()
                    .orElseThrow(() -> new NoSuchElementException("Repository URI unresolved"));

            RepositoryResolver resolver = RepositoryFinder.INSTANCE.lookup(repoURI);
            return resolver.resolveObjectDatabase(repoURI, hints);
        }

    }

    private static class PluginIndexDatabaseProvider implements Provider<IndexDatabase> {

        private @Inject Platform platform;

        private @Inject Hints hints;

        public @Override IndexDatabase get() {
            URI repoURI = new ResolveGeogigURI(platform, hints).call()
                    .orElseThrow(() -> new NoSuchElementException("Repository URI unresolved"));

            RepositoryResolver resolver = RepositoryFinder.INSTANCE.lookup(repoURI);
            return resolver.resolveIndexDatabase(repoURI, hints);
        }

    }

    private static class PluginRefDatabaseProvider implements Provider<RefDatabase> {

        private @Inject Platform platform;

        private @Inject Hints hints;

        public @Override RefDatabase get() {
            URI repoURI = new ResolveGeogigURI(platform, hints).call()
                    .orElseThrow(() -> new NoSuchElementException("Repository URI unresolved"));

            RepositoryResolver resolver = RepositoryFinder.INSTANCE.lookup(repoURI);
            return resolver.resolveRefDatabase(repoURI, hints);
        }
    }

    private static class PluginConflictsDatabaseProvider implements Provider<ConflictsDatabase> {
        private @Inject Platform platform;

        private @Inject Hints hints;

        public @Override ConflictsDatabase get() {
            URI repoURI = new ResolveGeogigURI(platform, hints).call()
                    .orElseThrow(() -> new NoSuchElementException("Repository URI unresolved"));

            RepositoryResolver resolver = RepositoryFinder.INSTANCE.lookup(repoURI);
            return resolver.resolveConflictsDatabase(repoURI, hints);
        }
    }
}
