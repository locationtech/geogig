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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.locationtech.geogig.storage.DeduplicationService;
import org.locationtech.geogig.storage.Deduplicator;

import com.google.inject.Inject;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;

/**
 * A {@link DeduplicationService} that creates {@link DatabaseConfig#setTemporary(boolean)
 * temporary} BDB JE databases in the {@code .geogig/seen} environment upon every
 * {@link #createDatabase()} call.
 * <p>
 * The created temporary databases share the heap cache with the other JE databases, namely the
 * {@link JEObjectDatabase object} and {@link JEStagingDatabase staging} databases, and will page to
 * disk as appropriate when the BDB JE cache is full.
 * </p>
 * 
 * @see BDBJEDeduplicator
 */
public class BDBJEDeduplicationService implements DeduplicationService {
    private EnvironmentBuilder environmentBuilder;

    private Set<BDBJEDeduplicator> openDeduplicators = new HashSet<BDBJEDeduplicator>();

    private volatile Environment environment;

    private volatile AtomicInteger tick = new AtomicInteger();

    @Inject
    public BDBJEDeduplicationService(EnvironmentBuilder environmentBuilder) {
        this.environmentBuilder = environmentBuilder;
    }

    private synchronized Environment getEnvironment() {
        if (this.environment == null) {
            this.environment = environmentBuilder.setRelativePath("seen").get();
        }
        return this.environment;
    }

    private Database createDatabase() {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setDeferredWrite(false);
        dbConfig.setTransactional(false);
        dbConfig.setTemporary(true);

        return getEnvironment().openDatabase(null, "seen" + tick.incrementAndGet(), dbConfig);
    }

    @Override
    public Deduplicator createDeduplicator() {
        Database database = createDatabase();
        BDBJEDeduplicator deduplicator = new BDBJEDeduplicator(database, this);
        this.openDeduplicators.add(deduplicator);
        return deduplicator;
    }

    protected void reset(BDBJEDeduplicator deduplicator) {
        deduplicator.setDatabase(createDatabase());
    }

    public synchronized void deregister(BDBJEDeduplicator deduplicator) {
        this.openDeduplicators.remove(deduplicator);
        if (this.openDeduplicators.size() == 0) {
            this.environment.close();
            this.environment = null;
        }
    }
}
