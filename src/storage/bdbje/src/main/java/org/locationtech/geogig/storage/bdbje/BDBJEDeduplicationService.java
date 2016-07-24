/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.bdbje;

import java.io.File;
import java.io.IOException;

import org.locationtech.geogig.model.DefaultPlatform;
import org.locationtech.geogig.repository.DeduplicationService;
import org.locationtech.geogig.repository.Deduplicator;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.sleepycat.je.DatabaseConfig;

/**
 * A {@link DeduplicationService} that creates {@link DatabaseConfig#setTemporary(boolean)
 * temporary} BDB JE databases in the {@code .geogig/seen} environment upon every
 * {@link #createDatabase()} call.
 * <p>
 * The created temporary databases share the heap cache with the other JE databases, namely the
 * {@link JEObjectDatabase object} database, and will page to disk as appropriate when the BDB JE
 * cache is full.
 * </p>
 * 
 * @see BDBJEDeduplicator
 */
public class BDBJEDeduplicationService implements DeduplicationService {

    public BDBJEDeduplicationService() {
        // default public constructor
    }

    @Override
    public Deduplicator createDeduplicator() {
        DefaultPlatform platform = new DefaultPlatform();
        File tmpDb;
        try {
            tmpDb = File.createTempFile("geogig-deduplicator", ".db", platform.getTempDir());
            Preconditions.checkState(tmpDb.delete());
            Preconditions.checkState(tmpDb.mkdirs());
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return new BDBJEDeduplicator(tmpDb);
    }
}
