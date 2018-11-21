/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.FORMAT_NAME;
import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.VERSION;

import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Stream;

import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.impl.SynchronizedGraphDatabase;
import org.locationtech.geogig.storage.postgresql.config.ConnectionConfig;
import org.locationtech.geogig.storage.postgresql.config.Environment;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

/**
 * PostgreSQL implementation for {@link ObjectDatabase}.
 * <p>
 * TODO: document/force use of {@code SET constraint_exclusion=ON}
 */
public class PGObjectDatabase extends PGObjectStore implements ObjectDatabase {

    private PGBlobStore blobStore;

    private PGGraphDatabase graph;

    @Inject
    public PGObjectDatabase(final ConfigDatabase configdb, final Hints hints)
            throws URISyntaxException {
        this(configdb, Environment.get(hints), readOnly(hints));
    }

    protected @Override String getCacheIdentifier(ConnectionConfig connectionConfig) {
        final String cacheIdentifier = connectionConfig.toURI().toString() + "#objects";
        return cacheIdentifier;
    }

    private static boolean readOnly(Hints hints) {
        return hints == null ? false : hints.getBoolean(Hints.OBJECTS_READ_ONLY);
    }

    public PGObjectDatabase(final ConfigDatabase configdb, final Environment config,
            final boolean readOnly) {
        super(configdb, config, readOnly);
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        StorageType.OBJECT.configure(configdb, FORMAT_NAME, VERSION);
    }

    @Override
    public boolean checkConfig() throws RepositoryConnectionException {
        return StorageType.OBJECT.verify(configdb, FORMAT_NAME, VERSION);
    }

    @Override
    public void open() {
        super.open();
        Preconditions.checkState(super.dataSource != null);
        final int repositoryId = config.getRepositoryId();
        final String blobsTable = config.getTables().blobs();

        blobStore = new PGBlobStore(dataSource, blobsTable, repositoryId);
        graph = new PGGraphDatabase(config);
        graph.open();
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public void close() {
        if (isOpen()) {
            try {
                graph.close();
                graph = null;
            } finally {
                super.close();
            }
        }
    }

    @Override
    public PGBlobStore getBlobStore() {
        Preconditions.checkState(isOpen(), "Database is closed");
        config.checkRepositoryExists();
        return blobStore;
    }

    @Override
    public GraphDatabase getGraphDatabase() {
        Preconditions.checkState(isOpen(), "Database is closed");
        config.checkRepositoryExists();
        return new SynchronizedGraphDatabase(graph);
    }

    /**
     * Overrides to add graphdb mapping on commits
     */
    public @Override boolean put(final RevObject object) {
        final boolean added = super.put(object);
        if (added && TYPE.COMMIT.equals(object.getType())) {
            RevCommit c = (RevCommit) object;
            graph.put(c.getId(), c.getParentIds());
        }
        return added;
    }

    /**
     * Overrides to update the {@link GraphDatabase} before {@link #putAll} commits a batch of
     * inserts, using the same connection and transaction
     */
    protected @Override void postInsert(final Connection cx,
            final Map<EncodedObject, Boolean> insertResults) throws SQLException {

        Stream<RevCommit> insertedCommits = insertResults.entrySet().stream()//
                .filter((e) -> e.getValue().booleanValue() && TYPE.COMMIT == e.getKey().type())//
                .map((e) -> (RevCommit) e.getKey().object());

        graph.put(cx, insertedCommits);

    }

}