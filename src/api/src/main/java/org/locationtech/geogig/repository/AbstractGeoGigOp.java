/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.util.Converters;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;

import com.google.common.base.Optional;

/**
 * Provides a base implementation for internal GeoGig operations.
 * 
 * @param <T> the type of the result of the execution of the command
 * @since 1.0
 */
public abstract class AbstractGeoGigOp<T> {

    private static final ProgressListener NULL_PROGRESS_LISTENER = new DefaultProgressListener();

    private ProgressListener progressListener = NULL_PROGRESS_LISTENER;

    private List<CommandListener> listeners;

    protected Context context;

    private Map<Serializable, Object> metadata;

    /**
     * Interface for a listener that will be notified before and after the op is called.
     */
    public static interface CommandListener {
        /**
         * Called prior to the operation's {@link AbstractGeoGigOp#call()} method.
         * 
         * @param command the command that is going to be called
         */
        public void preCall(AbstractGeoGigOp<?> command);

        /**
         * Called after the operation's {@link AbstractGeoGigOp#call()} method.
         * 
         * @param command the command that was called
         * @param result the value returned from the {@code call} method.
         * @param exception the exception thrown by the command, or {@code null}.
         */
        public void postCall(AbstractGeoGigOp<?> command, @Nullable Object result,
                @Nullable RuntimeException exception);
    }

    /**
     * Constructs a new abstract operation.
     */
    public AbstractGeoGigOp() {
        //
    }

    /**
     * Adds a {@link CommandListener} to this operation.
     * 
     * @param l the listener to add
     */
    public void addListener(CommandListener l) {
        if (listeners == null) {
            listeners = new ArrayList<AbstractGeoGigOp.CommandListener>(2);
        }
        listeners.add(l);
    }

    /**
     * @return a content holder for client code data that can be used by decorators/interceptors
     */
    public Map<Serializable, Object> getClientData() {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        return metadata;
    }

    protected <V> Optional<V> getClientData(Serializable key, Class<V> type) {
        if (metadata == null) {
            return Optional.absent();
        }
        V res = null;
        Object value = metadata.get(key);
        if (value != null) {
            res = Converters.convert(value, type);
        }
        return Optional.fromNullable(res);
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

    /**
     * @return the {@link Context} for this command
     */
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
        for (CommandListener l : listeners) {
            l.preCall(this);
        }
    }

    private void notifyPost(@Nullable T result, @Nullable RuntimeException exception) {
        if (listeners == null) {
            return;
        }
        for (CommandListener l : listeners) {
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
}
