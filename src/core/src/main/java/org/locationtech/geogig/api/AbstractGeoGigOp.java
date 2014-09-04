/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.StagingDatabase;

/**
 * Provides a base implementation for internal GeoGig operations.
 * 
 * @param <T> the type of the result of the execution of the command
 */
public abstract class AbstractGeoGigOp<T> {

    private static final ProgressListener NULL_PROGRESS_LISTENER = new DefaultProgressListener();

    private ProgressListener progressListener = NULL_PROGRESS_LISTENER;

    private List<CommandListener> listeners;

    protected Context context;

    private Map<Serializable, Serializable> metadata;

    public static interface CommandListener {
        public void preCall(AbstractGeoGigOp<?> command);

        public void postCall(AbstractGeoGigOp<?> command, Object result, boolean success);
    }

    /**
     * Constructs a new abstract operation.
     */
    public AbstractGeoGigOp() {
        //
    }

    public void addListener(CommandListener l) {
        if (listeners == null) {
            listeners = new ArrayList<AbstractGeoGigOp.CommandListener>(2);
        }
        listeners.add(l);
    }

    /**
     * @return a content holder for client code data that can be used by decorators/interceptors
     */
    public Map<Serializable, Serializable> getClientData() {
        if (metadata == null) {
            metadata = new HashMap<Serializable, Serializable>();
        }
        return metadata;
    }

    /**
     * Finds and returns an instance of a command of the specified class.
     * 
     * @param commandClass the kind of command to locate and instantiate
     * @return a new instance of the requested command class, with its dependencies resolved
     */
    public <C extends AbstractGeoGigOp<?>> C command(Class<C> commandClass) {
        return context.command(commandClass);
    }

    /**
     * @param locator the command locator to use when finding commands
     */
    public AbstractGeoGigOp<?> setContext(Context locator) {
        this.context = locator;
        return this;
    }

    public Context context() {
        return this.context;
    }

    /**
     * @param listener the progress listener to use
     * @return {@code this}
     */
    public AbstractGeoGigOp<T> setProgressListener(final ProgressListener listener) {
        this.progressListener = listener == null ? NULL_PROGRESS_LISTENER : listener;
        return this;
    }

    /**
     * @return the progress listener that is currently set
     */
    protected ProgressListener getProgressListener() {
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
    public final T call() {
        notifyPre();
        try {
            T result = _call();
            notifyPost(result, true);
            return result;
        } catch (RuntimeException e) {
            notifyPost(null, false);
            throw e;
        }
    }

    protected abstract T _call();

    private void notifyPre() {
        if (listeners == null) {
            return;
        }
        for (CommandListener l : listeners) {
            l.preCall(this);
        }
    }

    private void notifyPost(T result, boolean success) {
        if (listeners == null) {
            return;
        }
        for (CommandListener l : listeners) {
            l.postCall(this, result, success);
        }
    }

    /**
     * Shortcut for {@link Context#workingTree() getCommandLocator().getWorkingTree()}
     */
    protected WorkingTree workingTree() {
        return context.workingTree();
    }

    /**
     * Shortcut for {@link Context#index() getCommandLocator().getIndex()}
     */
    protected StagingArea index() {
        return context.index();
    }

    /**
     * Shortcut for {@link Context#refDatabase() getCommandLocator().getRefDatabase()}
     */
    protected RefDatabase refDatabase() {
        return context.refDatabase();
    }

    protected Platform platform() {
        return context.platform();
    }

    protected ObjectDatabase objectDatabase() {
        return context.objectDatabase();
    }

    protected StagingDatabase stagingDatabase() {
        return context.stagingDatabase();
    }

    protected ConfigDatabase configDatabase() {
        return context.configDatabase();
    }

    protected GraphDatabase graphDatabase() {
        return context.graphDatabase();
    }

    protected Repository repository() {
        return context.repository();
    }
}
