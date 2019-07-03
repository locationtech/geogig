/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.repository;

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

public interface Command<T> {

    /**
     * Interface for a listener that will be notified before and after the op is called.
     */
    interface CommandListener {
        /**
         * Called prior to the operation's {@link Command#call()} method.
         * 
         * @param command the command that is going to be called
         */
        public void preCall(Command<?> command);

        /**
         * Called after the operation's {@link Command#call()} method.
         * 
         * @param command the command that was called
         * @param result the value returned from the {@code call} method.
         * @param exception the exception thrown by the command, or {@code null}.
         */
        public void postCall(Command<?> command, @Nullable Object result,
                @Nullable RuntimeException exception);
    }

    /**
     * Adds a {@link Command.CommandListener} to this operation.
     * 
     * @param l the listener to add
     */
    void addListener(Command.CommandListener l);

    /**
     * @return a content holder for client code data that can be used by decorators/interceptors
     */
    Map<String, String> getClientData();

    /**
     * @param locator the command locator to use when finding commands
     */
    Command<?> setContext(Context locator);

    /**
     * @return the {@link Context} for this command
     */
    Context context();

    /**
     * @param listener the progress listener to use
     * @return {@code this}
     */
    Command<T> setProgressListener(ProgressListener listener);

    /**
     * @return the progress listener that is currently set
     */
    ProgressListener getProgressListener();

    /**
     * Subclasses shall implement to do the real work.
     * 
     * @see java.util.concurrent.Callable#call()
     */
    T call();

}