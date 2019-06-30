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
import java.util.function.Supplier;

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

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public @RequiredArgsConstructor class PluginsModule {

    private @NonNull Context context;

    private @Getter Supplier<ConfigDatabase> configDatabase = new PluginConfigDatabaseProvider();

    private @Getter Supplier<ConflictsDatabase> conflictsDatabase = new PluginConflictsDatabaseProvider();

    private @Getter Supplier<ObjectDatabase> objectsDatabase = new PluginObjectDatabaseProvider();

    private @Getter Supplier<IndexDatabase> indexDatabase = new PluginIndexDatabaseProvider();

    private @Getter Supplier<RefDatabase> refsDatabase = new PluginRefDatabaseProvider();

    private class PluginConfigDatabaseProvider implements Supplier<ConfigDatabase> {
        public @Override ConfigDatabase get() {
            Platform platform = context.platform();
            Hints hints = context.hints();
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

    private class PluginObjectDatabaseProvider implements Supplier<ObjectDatabase> {

        public @Override ObjectDatabase get() {
            Platform platform = context.platform();
            Hints hints = context.hints();
            URI repoURI = new ResolveGeogigURI(platform, hints).call()
                    .orElseThrow(() -> new NoSuchElementException("Repository URI unresolved"));

            RepositoryResolver resolver = RepositoryFinder.INSTANCE.lookup(repoURI);
            return resolver.resolveObjectDatabase(repoURI, hints);
        }

    }

    private class PluginIndexDatabaseProvider implements Supplier<IndexDatabase> {

        public @Override IndexDatabase get() {
            Platform platform = context.platform();
            Hints hints = context.hints();
            URI repoURI = new ResolveGeogigURI(platform, hints).call()
                    .orElseThrow(() -> new NoSuchElementException("Repository URI unresolved"));

            RepositoryResolver resolver = RepositoryFinder.INSTANCE.lookup(repoURI);
            return resolver.resolveIndexDatabase(repoURI, hints);
        }

    }

    private class PluginRefDatabaseProvider implements Supplier<RefDatabase> {
        public @Override RefDatabase get() {
            Platform platform = context.platform();
            Hints hints = context.hints();
            URI repoURI = new ResolveGeogigURI(platform, hints).call()
                    .orElseThrow(() -> new NoSuchElementException("Repository URI unresolved"));

            RepositoryResolver resolver = RepositoryFinder.INSTANCE.lookup(repoURI);
            return resolver.resolveRefDatabase(repoURI, hints);
        }
    }

    private class PluginConflictsDatabaseProvider implements Supplier<ConflictsDatabase> {
        public @Override ConflictsDatabase get() {
            Platform platform = context.platform();
            Hints hints = context.hints();
            URI repoURI = new ResolveGeogigURI(platform, hints).call()
                    .orElseThrow(() -> new NoSuchElementException("Repository URI unresolved"));

            RepositoryResolver resolver = RepositoryFinder.INSTANCE.lookup(repoURI);
            return resolver.resolveConflictsDatabase(repoURI, hints);
        }
    }
}
