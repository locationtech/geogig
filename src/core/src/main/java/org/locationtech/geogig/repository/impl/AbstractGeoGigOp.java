/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.repository.Command;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.SubProgressListener;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;

import lombok.NonNull;

/**
 * Provides a base implementation for internal GeoGig operations.
 * 
 * @param <T> the type of the result of the execution of the command
 * @since 1.0
 */
public abstract class AbstractGeoGigOp<T> implements Command<T> {

    private static final ProgressListener NULL_PROGRESS_LISTENER = new DefaultProgressListener();

    private ProgressListener progressListener = NULL_PROGRESS_LISTENER;

    private List<Command.CommandListener> listeners;

    protected Context context;

    private Map<String, String> metadata;

    /**
     * Constructs a new abstract operation.
     */
    public AbstractGeoGigOp() {
        //
    }

    /**
     * Adds a {@link Command.CommandListener} to this operation.
     * 
     * @param l the listener to add
     */
    @Override
    public void addListener(Command.CommandListener l) {
        if (listeners == null) {
            listeners = new ArrayList<Command.CommandListener>(2);
        }
        listeners.add(l);
    }

    /**
     * @return a content holder for client code data that can be used by decorators/interceptors
     */
    @Override
    public Map<String, String> getClientData() {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        return metadata;
    }

    protected Optional<String> getClientData(@NonNull String key) {
        if (metadata == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(metadata.get(key));
    }

    /**
     * Finds and returns an instance of a command of the specified class.
     * 
     * @param commandClass the kind of command to locate and instantiate
     * @return a new instance of the requested command class, with its dependencies resolved
     */
    public <C extends Command<?>> C command(Class<C> commandClass) {
        return context.command(commandClass);
    }

    /**
     * @param locator the command locator to use when finding commands
     */
    @Override
    public Command<?> setContext(Context locator) {
        this.context = locator;
        return this;
    }

    /**
     * @return the {@link Context} for this command
     */
    @Override
    public Context context() {
        return this.context;
    }

    /**
     * @param listener the progress listener to use
     * @return {@code this}
     */
    @Override
    public Command<T> setProgressListener(final ProgressListener listener) {
        this.progressListener = listener == null ? NULL_PROGRESS_LISTENER : listener;
        return this;
    }

    /**
     * @return the progress listener that is currently set
     */
    @Override
    public ProgressListener getProgressListener() {
        return progressListener;
    }

    /**
     * Constructs a new progress listener based on a specified sub progress amount.
     * 
     * @param amount amount of progress
     * @return the newly constructed progress listener
     */
    protected ProgressListener subProgress(float amount) {
        return new SubProgressListener(getProgressListener(), amount);
    }

    /**
     * Subclasses shall implement to do the real work.
     * 
     * @see java.util.concurrent.Callable#call()
     */
    @Override
    public T call() {
        try {
            notifyPre();
            T cmdResult = _call();
            notifyPost(cmdResult, null);
            return cmdResult;
        } catch (RuntimeException e) {
            notifyPost(null, e);
            throw e;
        }
    }

    protected abstract T _call();

    private void notifyPre() {
        if (listeners == null) {
            return;
        }
        for (Command.CommandListener l : listeners) {
            l.preCall(this);
        }
    }

    private void notifyPost(@Nullable T result, @Nullable RuntimeException exception) {
        if (listeners == null) {
            return;
        }
        for (Command.CommandListener l : listeners) {
            l.postCall(this, result, exception);
        }
    }

    /**
     * Shortcut for {@link Context#workingTree()}
     */
    protected WorkingTree workingTree() {
        return context.workingTree();
    }

    /**
     * Shortcut for {@link Context#stagingArea()}
     */
    protected StagingArea stagingArea() {
        return context.stagingArea();
    }

    /**
     * Shortcut for {@link Context#refDatabase()}
     */
    protected RefDatabase refDatabase() {
        return context.refDatabase();
    }

    /**
     * Shortcut for {@link Context#platform()}
     */
    protected Platform platform() {
        return context.platform();
    }

    /**
     * Shortcut for {@link Context#objectDatabase()}
     */
    protected ObjectDatabase objectDatabase() {
        return context.objectDatabase();
    }

    /**
     * Shortcut for {@link Context#indexDatabase()}
     */
    protected IndexDatabase indexDatabase() {
        return context.indexDatabase();
    }

    /**
     * Shortcut for {@link Context#conflictsDatabase()}
     */
    protected ConflictsDatabase conflictsDatabase() {
        return context.conflictsDatabase();
    }

    /**
     * Shortcut for {@link Context#configDatabase()}
     */
    protected ConfigDatabase configDatabase() {
        return context.configDatabase();
    }

    /**
     * Shortcut for {@link Context#graphDatabase()}
     */
    protected GraphDatabase graphDatabase() {
        return context.graphDatabase();
    }

    /**
     * Shortcut for {@link Context#repository()}
     */
    protected Repository repository() {
        return context.repository();
    }

    protected Geogig geogig() {
        return Geogig.of(context());
    }
}
