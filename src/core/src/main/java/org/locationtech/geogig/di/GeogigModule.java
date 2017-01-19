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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.locationtech.geogig.hooks.CommandHooksDecorator;
import org.locationtech.geogig.model.impl.DefaultPlatform;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.Index;
import org.locationtech.geogig.repository.impl.RepositoryImpl;
import org.locationtech.geogig.repository.impl.WorkingTreeImpl;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV2;
import org.locationtech.geogig.storage.fs.FileIndexDatabase;
import org.locationtech.geogig.storage.fs.FileObjectDatabase;
import org.locationtech.geogig.storage.fs.FileRefDatabase;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.storage.impl.ObjectSerializingFactory;
import org.locationtech.geogig.storage.memory.HeapGraphDatabase;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

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
 * @see GraphDatabase
 * @see ObjectSerializingFactory
 */

public class GeogigModule extends AbstractModule {

    /**
     * 
     * @see com.google.inject.AbstractModule#configure()
     */
    @Override
    protected void configure() {

        Provider<ExecutorService> fineGrainedExecutor = new Provider<ExecutorService>() {
            @Override
            public ExecutorService get() {
                int availableProcessors = Runtime.getRuntime().availableProcessors();
                return Executors.newFixedThreadPool(availableProcessors);
            }
        };

        bind(ExecutorService.class).toProvider(fineGrainedExecutor).in(Scopes.SINGLETON);

        bind(Context.class).to(GuiceInjector.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), Decorator.class);
        bind(DecoratorProvider.class).in(Scopes.SINGLETON);

        bind(Platform.class).toProvider(new PlatformProvider(binder().getProvider(Hints.class)))
                .in(Scopes.SINGLETON);

        bind(Repository.class).to(RepositoryImpl.class).in(Scopes.SINGLETON);
        bind(ConfigDatabase.class).to(IniFileConfigDatabase.class).in(Scopes.SINGLETON);
        bind(StagingArea.class).to(Index.class).in(Scopes.SINGLETON);
        bind(WorkingTree.class).to(WorkingTreeImpl.class).in(Scopes.SINGLETON);
        bind(GraphDatabase.class).to(HeapGraphDatabase.class).in(Scopes.SINGLETON);

        bind(ObjectDatabase.class).to(FileObjectDatabase.class).in(Scopes.SINGLETON);
        bind(IndexDatabase.class).to(FileIndexDatabase.class).in(Scopes.SINGLETON);
        bind(RefDatabase.class).to(FileRefDatabase.class).in(Scopes.SINGLETON);

        bind(ObjectSerializingFactory.class).to(DataStreamSerializationFactoryV2.class)
                .in(Scopes.SINGLETON);

        bindCommitGraphInterceptor();

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

    private void bindCommitGraphInterceptor() {

        ObjectDatabasePutInterceptor commitGraphUpdater = new ObjectDatabasePutInterceptor(
                getProvider(GraphDatabase.class));

        bindDecorator(binder(), commitGraphUpdater);
    }

    public static void bindDecorator(Binder binder, Decorator decorator) {

        Multibinder.newSetBinder(binder, Decorator.class).addBinding().toInstance(decorator);

    }
}
