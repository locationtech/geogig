/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Justin Deoliveira (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.sqlite;

import java.io.File;
import java.net.URI;

import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.plumbing.ResolveGeogigURI;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Utility class for SQLite storage.
 * 
 * @author Justin Deoliveira, Boundless
 */
public class SQLiteStorage {
    /**
     * Format name used for configuration.
     */
    public static final String FORMAT_NAME = "sqlite";

    /**
     * Implementation version.
     */
    public static final String VERSION = "0.1";

    /**
     * Returns the .geogig directory for the platform object.
     */
    public static File geogigDir(Platform platform) {
        Optional<URI> url = new ResolveGeogigURI(platform, null).call();
        Preconditions.checkState(url.isPresent(), "Unable to resolve .geogig directory");

        return new File(url.get());
    }
}
