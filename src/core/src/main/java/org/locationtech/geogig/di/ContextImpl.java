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

import java.util.function.Supplier;

import org.locationtech.geogig.repository.Command;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.repository.impl.RepositoryImpl;
import org.locationtech.geogig.repository.impl.StagingAreaImpl;
import org.locationtech.geogig.repository.impl.WorkingTreeImpl;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;

import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;

import lombok.NonNull;

/**
 * Provides a method for finding and creating instances of GeoGig operations.
 * 
 * @see Context
 * @see AbstractGeoGigOp
 */
public class ContextImpl implements Context {

    private final DecoratorProvider decoratorProvider = new DecoratorProvider();

    private @NonNull Platform platform;

    private @NonNull Hints hints;

    private final PluginsModule plugins;

    private final Supplier<StagingAreaImpl> stagingArea;

    private final Supplier<WorkingTreeImpl> workingTree;

    private final Supplier<ConfigDatabase> configDatabase;

    private final Supplier<ConflictsDatabase> conflictsDatabase;

    private final Supplier<IndexDatabase> indexDatabase;

    private final Supplier<ObjectDatabase> objectsDatabase;

    private final Supplier<RefDatabase> refsDatabase;

    private final Supplier<Repository> repository;

    public ContextImpl(@NonNull Platform platform, @NonNull Hints hints) {
        this.platform = platform;
        this.hints = hints;
        plugins = new PluginsModule(this);
        workingTree = Suppliers.memoize(() -> new WorkingTreeImpl(this));
        stagingArea = Suppliers.memoize(() -> new StagingAreaImpl(this));

        configDatabase = Suppliers.memoize(() -> plugins.getConfigDatabase().get());
        conflictsDatabase = Suppliers.memoize(() -> plugins.getConflictsDatabase().get());
        indexDatabase = Suppliers.memoize(() -> plugins.getIndexDatabase().get());
        objectsDatabase = Suppliers.memoize(() -> plugins.getObjectsDatabase().get());
        refsDatabase = Suppliers.memoize(() -> plugins.getRefsDatabase().get());

        repository = Suppliers.memoize(() -> new RepositoryImpl(this));
    }

    /**
     * Finds and returns an instance of a command of the specified class.
     * 
     * @param commandClass the kind of command to locate and instantiate
     * @return a new instance of the requested command class, with its dependencies resolved
     */
    public @Override <T extends Command<?>> T command(Class<T> commandClass) {
        T command = getInstance(commandClass);
        command.setContext(this);
        command = getDecoratedInstance(command);
        return command;
    }

    private <T> T getInstance(final @NonNull Class<T> type) {
        try {
            return type.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private <T> T getDecoratedInstance(final Class<T> type) {
        T undecorated = getInstance(type);
        Preconditions.checkNotNull(undecorated, "no instance of type %s found in context", type);
        return getDecoratedInstance(undecorated);
    }

    private <T> T getDecoratedInstance(@NonNull T undecorated) {
        T decoratedInstance = decoratorProvider.get(undecorated);
        return decoratedInstance;
    }

    public @Override Hints hints() {
        return getDecoratedInstance(hints);
    }

    public @Override Platform platform() {
        return getDecoratedInstance(platform);
    }

    public @Override WorkingTree workingTree() {
        return getDecoratedInstance(workingTree.get());
    }

    public @Override StagingArea stagingArea() {
        return getDecoratedInstance(stagingArea.get());
    }

    public @Override RefDatabase refDatabase() {
        return getDecoratedInstance(refsDatabase.get());
    }

    public @Override ObjectDatabase objectDatabase() {
        return getDecoratedInstance(objectsDatabase.get());
    }

    public @Override IndexDatabase indexDatabase() {
        return getDecoratedInstance(indexDatabase.get());
    }

    public @Override ConflictsDatabase conflictsDatabase() {
        return getDecoratedInstance(conflictsDatabase.get());
    }

    public @Override ConfigDatabase configDatabase() {
        return getDecoratedInstance(configDatabase.get());
    }

    public @Override GraphDatabase graphDatabase() {
        return getDecoratedInstance(objectDatabase().getGraphDatabase());
    }

    public @Override Repository repository() {
        return getDecoratedInstance(repository.get());
    }

    public @Override BlobStore blobStore() {
        return getDecoratedInstance(objectDatabase().getBlobStore());
    }

    public @Override Context snapshot() {
        return new SnapshotContext(this);
    }
}
