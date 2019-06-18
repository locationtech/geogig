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

import javax.inject.Inject;

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;

import com.google.common.base.Preconditions;
import com.google.inject.Provider;

import lombok.NonNull;

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
    public @Override <T extends AbstractGeoGigOp<?>> T command(Class<T> commandClass) {
        T command = getInstance(commandClass);
        command.setContext(this);
        command = getDecoratedInstance(command);
        return command;
    }

    private <T> T getInstance(final @NonNull Class<T> type) {
        Provider<T> provider = guiceInjector.getProvider(type);
        T instance = provider.get();
        return instance;
    }

    private <T> T getDecoratedInstance(final Class<T> type) {
        T undecorated = getInstance(type);
        Preconditions.checkNotNull(undecorated, "no instance of type %s found in context", type);
        return getDecoratedInstance(undecorated);
    }

    private <T> T getDecoratedInstance(@NonNull T undecorated) {
        final @NonNull DecoratorProvider decoratorProvider = guiceInjector
                .getInstance(DecoratorProvider.class);
        T decoratedInstance = decoratorProvider.get(undecorated);
        return decoratedInstance;
    }

    public @Override WorkingTree workingTree() {
        return getDecoratedInstance(WorkingTree.class);
    }

    public @Override StagingArea stagingArea() {
        return getDecoratedInstance(StagingArea.class);
    }

    public @Override RefDatabase refDatabase() {
        return getDecoratedInstance(RefDatabase.class);
    }

    public @Override Platform platform() {
        return getDecoratedInstance(Platform.class);
    }

    public @Override ObjectDatabase objectDatabase() {
        return getDecoratedInstance(ObjectDatabase.class);
    }

    public @Override IndexDatabase indexDatabase() {
        return getDecoratedInstance(IndexDatabase.class);
    }

    public @Override ConflictsDatabase conflictsDatabase() {
        return getDecoratedInstance(ConflictsDatabase.class);
    }

    public @Override ConfigDatabase configDatabase() {
        return getDecoratedInstance(ConfigDatabase.class);
    }

    public @Override GraphDatabase graphDatabase() {
        return getDecoratedInstance(objectDatabase().getGraphDatabase());
    }

    public @Override Repository repository() {
        return getDecoratedInstance(Repository.class);
    }

    public @Override BlobStore blobStore() {
        return getDecoratedInstance(objectDatabase().getBlobStore());
    }

    public @Override Context snapshot() {
        return new SnapshotContext(this);
    }
}
