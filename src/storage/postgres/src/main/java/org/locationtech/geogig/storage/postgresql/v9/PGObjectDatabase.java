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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Stream;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.postgresql.config.ConnectionConfig;
import org.locationtech.geogig.storage.postgresql.config.Environment;

/**
 * PostgreSQL implementation for {@link ObjectDatabase}.
 * <p>
 * TODO: document/force use of {@code SET constraint_exclusion=ON}
 */
public class PGObjectDatabase extends PGObjectStore implements ObjectDatabase {

    private PGBlobStore blobStore;

    private PGGraphDatabase graph;

    public PGObjectDatabase(final ConfigDatabase configdb, final Environment env) {
        super(configdb, env);
    }

    protected @Override String getCacheIdentifier(ConnectionConfig connectionConfig) {
        final String cacheIdentifier = connectionConfig.toURI().toString() + "#objects";
        return cacheIdentifier;
    }

    public @Override void open() {
        if (!isOpen()) {
            super.open();
            blobStore = new PGBlobStore(env);
            graph = new PGGraphDatabase(env);
            graph.open();
        }
    }

    public @Override void close() {
        if (isOpen()) {
            super.close();
            try {
                graph.close();
            } finally {
                graph = null;
            }
        }
    }

    public @Override PGBlobStore getBlobStore() {
        Preconditions.checkState(isOpen(), "Database is closed");
        env.checkRepositoryExists();
        return blobStore;
    }

    public @Override GraphDatabase getGraphDatabase() {
        Preconditions.checkState(isOpen(), "Database is closed");
        env.checkRepositoryExists();
        return graph;
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