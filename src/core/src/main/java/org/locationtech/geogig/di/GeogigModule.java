/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.di;

import org.locationtech.geogig.hooks.CommandHooksDecorator;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DefaultPlatform;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.RepositoryImpl;
import org.locationtech.geogig.repository.impl.StagingAreaImpl;
import org.locationtech.geogig.repository.impl.WorkingTreeImpl;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Providers;

/**
 * Provides bindings for GeoGig singletons.
 * 
 * @see Context
 * @see Platform
 * @see Repository
 * @see ConfigDatabase
 * @see StagingArea
 * @see WorkingTreeImpl
 * @see ObjectDatabase
 * @see StagingDatabase
 * @see RefDatabase
 * @see RevObjectSerializer
 */

public class GeogigModule extends AbstractModule {

    /**
     * 
     * @see com.google.inject.AbstractModule#configure()
     */
    @Override
    protected void configure() {
        bind(Context.class).to(GuiceContext.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), Decorator.class);
        bind(DecoratorProvider.class).in(Scopes.SINGLETON);

        bind(Platform.class).toProvider(new PlatformProvider(binder().getProvider(Hints.class)))
                .in(Scopes.SINGLETON);

        bind(Repository.class).to(RepositoryImpl.class).in(Scopes.SINGLETON);
        bind(StagingArea.class).to(StagingAreaImpl.class).in(Scopes.SINGLETON);
        bind(WorkingTree.class).to(WorkingTreeImpl.class).in(Scopes.SINGLETON);

        // TODO bind configdb to null and force it to be provided depending on the repo
        bind(ConfigDatabase.class).to(IniFileConfigDatabase.class).in(Scopes.SINGLETON);
        bind(RefDatabase.class).toProvider(Providers.of(null)).in(Scopes.SINGLETON);
        bind(ObjectDatabase.class).toProvider(Providers.of(null)).in(Scopes.SINGLETON);
        bind(IndexDatabase.class).toProvider(Providers.of(null)).in(Scopes.SINGLETON);
        bind(ConflictsDatabase.class).toProvider(Providers.of(null)).in(Scopes.SINGLETON);

        bindConflictCheckingInterceptor();

        bindDecorator(binder(), new CommandHooksDecorator());
    }

    private static class PlatformProvider implements Provider<Platform> {
        private final Provider<Hints> hints;

        private Platform resolved;

        public PlatformProvider(Provider<Hints> hints) {
            this.hints = hints;
        }

        @Override
        public Platform get() {
            if (resolved == null) {
                Hints hints = this.hints.get();
                resolved = (Platform) hints.get(Hints.PLATFORM).or(new DefaultPlatform());
            }
            return resolved;
        }
    }

    private void bindConflictCheckingInterceptor() {
        bindDecorator(binder(), new ConflictInterceptor());
    }

    public static void bindDecorator(Binder binder, Decorator decorator) {

        Multibinder.newSetBinder(binder, Decorator.class).addBinding().toInstance(decorator);

    }
}
