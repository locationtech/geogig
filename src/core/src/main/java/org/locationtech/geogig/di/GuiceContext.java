/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.di;

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.StagingAreaImpl;
import org.locationtech.geogig.repository.impl.WorkingTreeImpl;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.PluginDefaults;
import org.locationtech.geogig.storage.RefDatabase;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Provides a method for finding and creating instances of GeoGig operations.
 * 
 * @see Context
 * @see AbstractGeoGigOp
 */
public class GuiceContext implements Context {

    private com.google.inject.Injector guiceInjector;

    /**
     * Constructs a new {@code GuiceCommandLocator} with the given {@link Context}.
     * 
     * @param injector the injector which has commands bound to it
     */
    @Inject
    public GuiceContext(com.google.inject.Injector injector) {
        this.guiceInjector = injector;
    }

    /**
     * Finds and returns an instance of a command of the specified class.
     * 
     * @param commandClass the kind of command to locate and instantiate
     * @return a new instance of the requested command class, with its dependencies resolved
     */
    @Override
    public <T extends AbstractGeoGigOp<?>> T command(Class<T> commandClass) {
        T command = getInstance(commandClass);
        command.setContext(this);
        command = getDecoratedInstance(command);
        return command;
    }

    private <T> T getInstance(final Class<T> type) {
        Provider<T> provider = guiceInjector.getProvider(type);
        T instance = provider.get();
        return instance;
    }

    private <T> T getDecoratedInstance(final Class<T> type) {
        T undecorated = getInstance(type);
        return getDecoratedInstance(undecorated);
    }

    private <T> T getDecoratedInstance(T undecorated) {
        DecoratorProvider decoratorProvider = guiceInjector.getInstance(DecoratorProvider.class);
        T decoratedInstance = decoratorProvider.get(undecorated);
        return decoratedInstance;
    }

    @Override
    public WorkingTree workingTree() {
        return getDecoratedInstance(WorkingTree.class);
    }

    @Override
    @Deprecated
    public StagingArea index() {
        return stagingArea();
    }

    @Override
    public StagingArea stagingArea() {
        return getDecoratedInstance(StagingArea.class);
    }

    @Override
    public RefDatabase refDatabase() {
        return getDecoratedInstance(RefDatabase.class);
    }

    @Override
    public Platform platform() {
        return getDecoratedInstance(Platform.class);
    }

    @Override
    public ObjectDatabase objectDatabase() {
        return getDecoratedInstance(ObjectDatabase.class);
    }

    @Override
    public IndexDatabase indexDatabase() {
        return getDecoratedInstance(IndexDatabase.class);
    }

    @Override
    public ConflictsDatabase conflictsDatabase() {
        return getDecoratedInstance(objectDatabase().getConflictsDatabase());
    }

    @Override
    public ConfigDatabase configDatabase() {
        return getDecoratedInstance(ConfigDatabase.class);
    }

    @Override
    public GraphDatabase graphDatabase() {
        return getDecoratedInstance(GraphDatabase.class);
    }

    @Deprecated
    @Override
    public Repository repository() {
        return getDecoratedInstance(Repository.class);
    }

    @Override
    public PluginDefaults pluginDefaults() {
        return getDecoratedInstance(PluginDefaults.class);
    }

    @Override
    public BlobStore blobStore() {
        return getDecoratedInstance(objectDatabase().getBlobStore());
    }

    @Override
    public Context snapshot() {
        return new SnapshotContext(this);
    }

    private static class SnapshotContext implements Context {

        private Context context;

        private RefDatabaseSnapshot refsSnapshot;

        public SnapshotContext(Context context) {
            this.context = context;
            this.refsSnapshot = new RefDatabaseSnapshot(context.refDatabase());
            this.refsSnapshot.create();
        }

        @Override
        public <T extends AbstractGeoGigOp<?>> T command(Class<T> commandClass) {
            T command = context.command(commandClass);
            command.setContext(this);
            return command;
        }

        @Override
        public WorkingTree workingTree() {
            return new WorkingTreeImpl(this);
        }

        @Override
        public StagingArea index() {
            return index();
        }

        @Override
        public StagingArea stagingArea() {
            return new StagingAreaImpl(this);
        }

        @Override
        public RefDatabase refDatabase() {
            return refsSnapshot;
        }

        @Override
        public Context snapshot() {
            return this;
        }
        //////////////// END OF DECORATED METHODS /////////////////

        @Override
        public Platform platform() {
            return context.platform();
        }

        @Override
        public ObjectDatabase objectDatabase() {
            return context.objectDatabase();
        }

        @Override
        public IndexDatabase indexDatabase() {
            return context.indexDatabase();
        }

        @Override
        public ConflictsDatabase conflictsDatabase() {
            return context.conflictsDatabase();
        }

        @Override
        public ConfigDatabase configDatabase() {
            return context.configDatabase();
        }

        @Override
        public GraphDatabase graphDatabase() {
            return context.graphDatabase();
        }

        @Override
        public Repository repository() {
            return context.repository();
        }

        @Override
        public BlobStore blobStore() {
            return context.blobStore();
        }

        @Override
        public PluginDefaults pluginDefaults() {
            return context.pluginDefaults();
        }
    }
}
