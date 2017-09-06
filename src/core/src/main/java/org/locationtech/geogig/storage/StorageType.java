/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage;

import org.locationtech.geogig.repository.RepositoryConnectionException;

import com.google.common.base.Optional;

/**
 * Enumeration for the various types of storage used by GeoGig. Provides utility functions for
 * configuring a {@link ConfigDatabase} with a format name and version for each storage type.
 * 
 * @since 1.0
 */
public enum StorageType {
    OBJECT("objects"), INDEX("index"), REF("refs"), STAGING("staging");

    private StorageType(String key) {
        this.key = key;
    }

    public final String key;

    /**
     * Adds configuration properties to the {@link ConfigDatabase} with the format name and version
     * of this storage type.
     * 
     * @param configDB the config database
     * @param formatName the format name of the storage type
     * @param version the version of the storage format
     * @throws RepositoryConnectionException
     */
    public void configure(ConfigDatabase configDB, String formatName, String version)
            throws RepositoryConnectionException {
        Optional<String> storageName = configDB.get("storage." + key);
        Optional<String> storageVersion = configDB.get(formatName + ".version");
        if (storageName.isPresent() && !storageName.get().equals(formatName)) {
            throw new RepositoryConnectionException("Initializing already " + "initialized " + key
                    + " database. Would set " + formatName + ":" + version + " but found "
                    + storageName.orNull() + ":" + storageVersion.orNull());
        }
        if (storageVersion.isPresent() && !version.equals(storageVersion.get())) {
            throw new RepositoryConnectionException("Initializing already " + "initialized " + key
                    + " database. Would set " + formatName + ":" + version + " but found "
                    + storageName.orNull() + ":" + storageVersion.orNull());
        }
        configDB.put("storage." + key, formatName);
        configDB.put(formatName + ".version", version);
    }

    /**
     * Verifies that the repository is compatible with the provided format name and version for this
     * storage type.
     * 
     * @param configDB the config database
     * @param formatName the format name of the storage type
     * @param version the version of the storage format
     * @return {@code true} if the storage type was configured and verified, {@code false} if it was
     *         unset
     * @throws RepositoryConnectionException
     */
    public boolean verify(ConfigDatabase configDB, String formatName, String version)
            throws RepositoryConnectionException {
        Optional<String> storageName = configDB.get("storage." + key);
        Optional<String> storageVersion = configDB.get(formatName + ".version");
        boolean unset = !storageName.isPresent();
        boolean valid = storageName.isPresent() && formatName.equals(storageName.get())
                && storageVersion.isPresent() && version.equals(storageVersion.get());
        if (!(unset || valid)) {
            throw new RepositoryConnectionException("Cannot open " + key + " database with format: "
                    + formatName + " and version: " + version + ", found format: "
                    + storageName.orNull() + ", version: " + storageVersion.orNull());
        }
        return !unset;
    }
}