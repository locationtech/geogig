/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.util.ServiceLoader;

import com.google.common.collect.ImmutableList;

/**
 * A plug-in mechanism for providers of storage backends for the different kinds of geogig
 * databases.
 * <p>
 * Realizations of this interface are looked up in the classpath using the standard Java
 * {@link ServiceLoader service provider interface (SPI)} mechanism, by loading the classes defined
 * in all {@code META-INF/services/org.locationtech.geogig.storage.StorageProvider} text files.
 *
 * @since 1.0
 */
public abstract class StorageProvider {

    /**
     * @return a short name of the storage backend
     */
    public abstract String getName();

    /**
     * @return an application level version for the storage backend, may denote a serialization
     *         format or storage mechanism
     */
    public abstract String getVersion();

    /**
     * @return a human readable description of the storage backend
     */
    public abstract String getDescription();

    /**
     * @return the format of the {@link ObjectDatabase}
     */
    public abstract VersionedFormat getObjectDatabaseFormat();

    /**
     * @return the format of the {@link IndexDatabase}
     */
    public abstract VersionedFormat getIndexDatabaseFormat();

    /**
     * @return the format of the {@link ConflictsDatabase}
     */
    public abstract VersionedFormat getConflictsDatabaseFormat();

    /**
     * @return the format of the {@link RefDatabase}
     */
    public abstract VersionedFormat getRefsDatabaseFormat();

    /**
     * @return the available providers as found by {@link ServiceLoader} under the
     *         {@link StorageProvider} key.
     */
    public static Iterable<StorageProvider> findProviders() {
        ServiceLoader<StorageProvider> loader = ServiceLoader.load(StorageProvider.class,
                StorageProvider.class.getClassLoader());
        return ImmutableList.copyOf(loader.iterator());
    }
}
