/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Spliterator.DISTINCT;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterators.spliteratorUnknownSize;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.impl.ConnectionManager;
import org.locationtech.geogig.storage.impl.ForwardingObjectStore;
import org.locationtech.geogig.storage.impl.SynchronizedGraphDatabase;

import com.google.inject.Inject;

/**
 * Provides an implementation of a GeoGig object database that utilizes the heap for the storage of
 * objects.
 * 
 * @see ForwardingObjectStore
 */
public class HeapObjectDatabase extends ForwardingObjectStore implements ObjectDatabase {

    static HeapObjectDatabaseConnectionManager CONN_MANAGER = new HeapObjectDatabaseConnectionManager();

    private HeapBlobStore blobs;

    private HeapGraphDatabase graph;

    private Platform platform;

    public HeapObjectDatabase() {
        super(new HeapObjectStore(), false);
    }

    @Inject
    public HeapObjectDatabase(Platform platform, Hints hints) {
        super(connect(platform), readOnly(hints));
        this.platform = platform;
    }

    private static HeapObjectStore connect(Platform platform) {
        Path path = platform.pwd().toPath();
        HeapObjectStore store = CONN_MANAGER.acquire(path);
        return store;
    }

    private static boolean readOnly(Hints hints) {
        return hints == null ? false : hints.getBoolean(Hints.OBJECTS_READ_ONLY);
    }

    /**
     * Closes the database.
     * 
     * @see org.locationtech.geogig.storage.ObjectDatabase#close()
     */
    @Override
    public void close() {
        super.close();
        if (graph != null) {
            graph.close();
            graph = null;
        }
    }

    /**
     * Opens the database for use by GeoGig.
     */
    @Override
    public void open() {
        if (isOpen()) {
            return;
        }
        super.open();
        blobs = new HeapBlobStore();
        graph = new HeapGraphDatabase(platform);
        graph.open();
    }

    @Override
    public boolean isReadOnly() {
        return !super.canWrite;
    }

    @Override
    public BlobStore getBlobStore() {
        return blobs;
    }

    @Override
    public GraphDatabase getGraphDatabase() {
        return new SynchronizedGraphDatabase(graph);
    }

    @Override
    public void configure() {
        // No-op
    }

    @Override
    public boolean checkConfig() {
        return true;
    }

    public @Override boolean put(RevObject object) {
        final boolean added = super.put(object);
        if (added && TYPE.COMMIT.equals(object.getType())) {
            try {
                RevCommit c = (RevCommit) object;
                graph.put(c.getId(), c.getParentIds());
            } catch (RuntimeException e) {
                super.delete(object.getId());
                throw e;
            }
        }
        return added;
    }

    public @Override void putAll(Iterator<? extends RevObject> objects) {
        putAll(objects, BulkOpListener.NOOP_LISTENER);
    }

    public @Override void putAll(Iterator<? extends RevObject> objects, BulkOpListener listener) {
        checkNotNull(objects, "objects is null");
        checkNotNull(listener, "listener is null");
        checkState(isOpen(), "db is closed");
        checkWritable();

        final int characteristics = IMMUTABLE | NONNULL | DISTINCT;
        Stream<? extends RevObject> stream;
        stream = StreamSupport.stream(spliteratorUnknownSize(objects, characteristics), true);

        stream.forEach((o) -> {
            if (put(o)) {
                listener.inserted(o.getId(), null);
            } else {
                listener.found(o.getId(), null);
            }
        });
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private static class HeapObjectDatabaseConnectionManager
            extends ConnectionManager<Path, HeapObjectStore> {

        @Override
        protected HeapObjectStore connect(Path address) {
            return new HeapObjectStore();
        }

        @Override
        protected void disconnect(HeapObjectStore c) {
            c.close();
        }

    }
}
