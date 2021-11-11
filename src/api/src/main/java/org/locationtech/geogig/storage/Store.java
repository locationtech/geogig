/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage;

import java.io.Closeable;

import org.locationtech.geogig.base.Preconditions;

/**
 * Base interface for storage and retrieval of revision objects.
 *
 * @since 2.0
 */
public interface Store extends Closeable {

    /**
     * Opens the database. It's safe to call this method multiple times, and only the first call
     * shall take effect.
     */
    public void open();

    /**
     * Closes the database. This method is idempotent.
     */
    public void close();

    /**
     * @return {@code true} if the database is open, false otherwise
     */
    public boolean isOpen();

    public boolean isReadOnly();

    /**
     * @throws IllegalStateException
     */
    public default void checkWritable() {
        checkOpen();
        Preconditions.checkState(!isReadOnly(), "Database is read only");
    }

    /**
     * @throws IllegalStateException
     */
    public default void checkOpen() {
        Preconditions.checkState(isOpen(), "Database is closed");
    }
}