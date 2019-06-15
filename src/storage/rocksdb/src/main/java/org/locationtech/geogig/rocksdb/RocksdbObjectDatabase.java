/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.impl.SynchronizedGraphDatabase;

import com.google.common.collect.Sets;
import com.google.common.io.Closeables;

import lombok.NonNull;

public class RocksdbObjectDatabase extends RocksdbObjectStore implements ObjectDatabase {

    private RocksdbBlobStore blobs;

    private RocksdbGraphDatabase graph;

    public RocksdbObjectDatabase(@NonNull File dbdir, boolean readOnly) {
        super(dbdir, readOnly);
    }

    @Override
    public RocksdbBlobStore getBlobStore() {
        return blobs;
    }

    @Override
    public GraphDatabase getGraphDatabase() {
        return new SynchronizedGraphDatabase(graph);
    }

    @Override
    public synchronized void open() {
        if (isOpen()) {
            return;
        }
        super.open();
        try {
            File blobsDir = new File(super.dbDirectory, "blobs");
            File graphDir = new File(super.dbDirectory.getParentFile(), "graph.rocksdb");
            blobsDir.mkdir();
            graphDir.mkdir();
            this.blobs = new RocksdbBlobStore(blobsDir, isReadOnly());
            this.graph = new RocksdbGraphDatabase(graphDir, isReadOnly());
            this.graph.open();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    public synchronized void close() {
        if (!isOpen()) {
            return;
        }
        try {
            super.close();
        } finally {
            RocksdbBlobStore blobs = this.blobs;
            RocksdbGraphDatabase graph = this.graph;
            this.blobs = null;
            this.graph = null;
            try {
                Closeables.close(blobs, true);
                Closeables.close(graph, true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Overrides to add graphdb commit to parents mappings on commits
     */
    public @Override boolean put(final RevObject object) {
        final boolean added = super.put(object);
        if (added && TYPE.COMMIT.equals(object.getType())) {
            RevCommit c = (RevCommit) object;
            graph.put(c.getId(), c.getParentIds());
        }
        return added;
    }

    protected @Override void putAll(Stream<RevObject> stream, BulkOpListener listener) {
        // collect all ids of commits being inserted
        Set<ObjectId> visitedCommits = Sets.newConcurrentHashSet();
        Consumer<RevObject> trackCommits = (o) -> {
            if (TYPE.COMMIT == o.getType()) {
                visitedCommits.add(o.getId());
            }
        };
        // the stream will call trackCommits for each object as they're consumed
        stream = stream.peek(trackCommits);
        try {
            super.putAll(stream, listener);
        } finally {
            // insert the mappings for all the commits that tried to be inserted and can be found in
            // the objects db. It is ok to call graphdb.put with a commit that already exists, and
            // this way we don't have to keep a potentially huge collection of RevCommits in memory
            if (!visitedCommits.isEmpty()) {
                Iterator<RevCommit> inserted = super.getAll(visitedCommits,
                        BulkOpListener.NOOP_LISTENER, RevCommit.class);
                graph.putAll(() -> inserted);
            }
        }
    }
}
