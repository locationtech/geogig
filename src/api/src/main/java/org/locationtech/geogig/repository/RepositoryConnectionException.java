/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Optional;

public class RepositoryConnectionException extends Exception {

    private static final long serialVersionUID = -4046351627917194599L;

    public RepositoryConnectionException(String message) {
        super(message);
    }

    public static enum StorageType {
        GRAPH("graph"), OBJECT("objects"), REF("refs"), STAGING("staging");

        private StorageType(String key) {
            this.key = key;
        }

        public final String key;

        public void configure(ConfigDatabase configDB, String formatName, String version)
                throws RepositoryConnectionException {
            Optional<String> storageName = configDB.get("storage." + key);
            Optional<String> storageVersion = configDB.get(formatName + ".version");
            if (storageName.isPresent() && !storageName.get().equals(formatName)) {
                throw new RepositoryConnectionException("Initializing already " + "initialized "
                        + key + " database. Would set " + formatName + ":" + version
                        + " but found " + storageName.orNull() + ":" + storageVersion.orNull());
            }
            if (storageVersion.isPresent() && !version.equals(storageVersion.get())) {
                throw new RepositoryConnectionException("Initializing already " + "initialized "
                        + key + " database. Would set " + formatName + ":" + version
                        + " but found " + storageName.orNull() + ":" + storageVersion.orNull());
            }
            configDB.put("storage." + key, formatName);
            configDB.put(formatName + ".version", version);
        }

        public void verify(ConfigDatabase configDB, String formatName, String version)
                throws RepositoryConnectionException {
            Optional<String> storageName = configDB.get("storage." + key);
            Optional<String> storageVersion = configDB.get(formatName + ".version");
            boolean unset = !(storageName.isPresent() || storageVersion.isPresent());
            boolean valid = storageName.isPresent() && formatName.equals(storageName.get())
                    && storageVersion.isPresent() && version.equals(storageVersion.get());
            if (!(unset || valid)) {
                throw new RepositoryConnectionException("Cannot open " + key
                        + " database with format: " + formatName + " and version: " + version
                        + ", found format: " + storageName.orNull() + ", version: "
                        + storageVersion.orNull());
            }
        }
    }
}
