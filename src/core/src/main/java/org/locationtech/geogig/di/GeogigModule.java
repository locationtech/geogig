/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.di;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.DefaultPlatform;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.hooks.CommandHooksDecorator;
import org.locationtech.geogig.repository.Index;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.DeduplicationService;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.StagingDatabase;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV2;
import org.locationtech.geogig.storage.fs.FileObjectDatabase;
import org.locationtech.geogig.storage.fs.FileRefDatabase;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.storage.memory.HeapDeduplicationService;
import org.locationtech.geogig.storage.memory.HeapGraphDatabase;
import org.locationtech.geogig.storage.memory.HeapStagingDatabase;

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
 * @see WorkingTree
 * @see ObjectDatabase
 * @see StagingDatabase
 * @see RefDatabase
 * @see GraphDatabase
 * @see ObjectSerializingFactory
 * @see DeduplicationService
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

        bind(Platform.class).to(DefaultPlatform.class).asEagerSingleton();

        bind(Repository.class).in(Scopes.SINGLETON);
        bind(ConfigDatabase.class).to(IniFileConfigDatabase.class).in(Scopes.SINGLETON);
        bind(StagingArea.class).to(Index.class).in(Scopes.SINGLETON);
        bind(StagingDatabase.class).to(HeapStagingDatabase.class).in(Scopes.SINGLETON);
        bind(WorkingTree.class).in(Scopes.SINGLETON);
        bind(GraphDatabase.class).to(HeapGraphDatabase.class).in(Scopes.SINGLETON);

        bind(ObjectDatabase.class).to(FileObjectDatabase.class).in(Scopes.SINGLETON);
        bind(RefDatabase.class).to(FileRefDatabase.class).in(Scopes.SINGLETON);

        bind(ObjectSerializingFactory.class).to(DataStreamSerializationFactoryV2.class).in(
                Scopes.SINGLETON);

        bind(DeduplicationService.class).to(HeapDeduplicationService.class).in(Scopes.SINGLETON);

        bindCommitGraphInterceptor();

        bindConflictCheckingInterceptor();

        bindDecorator(binder(), new CommandHooksDecorator());
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
