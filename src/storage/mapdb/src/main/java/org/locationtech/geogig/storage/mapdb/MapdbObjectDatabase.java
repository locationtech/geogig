/* Copyright (c) 2015 SWM Services GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Sebastian Schmidt (SWM Services GmbH) - initial implementation
 */
package org.locationtech.geogig.storage.mapdb;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.ConnectionManager;
import org.locationtech.geogig.storage.ForwardingObjectStore;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectInserter;
import org.locationtech.geogig.storage.fs.FileBlobStore;
import org.mapdb.DB;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * An Object database that uses a MapDB file database for persistence.
 * 
 * @see http://mapdb.org/
 */
public class MapdbObjectDatabase extends ForwardingObjectStore implements ObjectDatabase {

    private final static ConnectionManager<Path, MapdbObjectStore> STORE_MANAGER = new ConnectionManager<Path, MapdbObjectStore>() {

        @Override
        protected void disconnect(MapdbObjectStore store) {
            store.close();
        }

        @Override
        protected MapdbObjectStore connect(Path address) {
            return new MapdbObjectStore(address);
        }
    };

    private static class StoreProvider implements Provider<MapdbObjectStore> {

        private Path directory;

        private MapdbObjectStore liveStore;

        public StoreProvider(Path storeDirectory) {
            this.directory = storeDirectory;
        }

        @Override
        public MapdbObjectStore get() {
            if (liveStore == null) {
                liveStore = STORE_MANAGER.acquire(directory);
            }
            return liveStore;
        }

    }

    private final ConfigDatabase config;

    private MapdbConflictsDatabase conflicts;

    private FileBlobStore blobStore;

    private Platform platform;

    private final AtomicBoolean open = new AtomicBoolean();

    private final boolean readOnly;

    @Inject
    public MapdbObjectDatabase(ConfigDatabase config, Platform platform, Hints hints) {
        super(connect(platform));
        this.readOnly = hints == null ? false : hints.getBoolean(Hints.OBJECTS_READ_ONLY);
        this.config = config;
    }

    private static StoreProvider connect(Platform platform) {

        Optional<URI> repoPath = new ResolveGeogigURI(platform, null).call();
        Preconditions.checkState(repoPath.isPresent(), "Not inside a geogig directory");
        URI uri = repoPath.get();
        Preconditions.checkState("file".equals(uri.getScheme()),
                "Repository URL is not file system based: %s", uri);
        File repoLocation = new File(uri);
        Path storeDirectory = new File(repoLocation, "objects").toPath();
        return new StoreProvider(storeDirectory);
    }

    @Override
    public synchronized void open() {
        if (isOpen()) {
            return;
        }
        super.open();
        DB db = ((MapdbObjectStore) subject.get()).db;
        conflicts = new MapdbConflictsDatabase(db);
        blobStore = new FileBlobStore(platform);
        this.open.set(true);
    }

    @Override
    public final boolean isOpen() {
        return open.get();
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.OBJECT.configure(config, "mapdb", "0.1");
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.OBJECT.verify(config, "mapdb", "0.1");
    }

    @Override
    public synchronized void close() {
        final boolean wasOpen = open.getAndSet(false);
        if (!wasOpen) {
            return;
        }

        StoreProvider provider = (StoreProvider) subject;
        MapdbObjectStore store = (MapdbObjectStore) provider.liveStore;
        STORE_MANAGER.release(store);
        ((StoreProvider) provider).liveStore = null;
        conflicts.close();
        conflicts = null;
        blobStore.close();
        blobStore = null;
    }

    @Override
    public ConflictsDatabase getConflictsDatabase() {
        return conflicts;
    }

    @Override
    public BlobStore getBlobStore() {
        return blobStore;
    }

    @Override
    public boolean delete(ObjectId id) {
        checkWritable();
        return super.delete(id);
    }

    @Override
    public long deleteAll(Iterator<ObjectId> ids) {
        return deleteAll(ids, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public long deleteAll(Iterator<ObjectId> ids, BulkOpListener listener) {
        checkWritable();
        return super.deleteAll(ids, listener);
    }

    @Override
    public boolean put(final RevObject object) {
        checkWritable();
        return super.put(object);
    }

    @Override
    public void putAll(final Iterator<? extends RevObject> objects) {
        putAll(objects, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public void putAll(Iterator<? extends RevObject> objects, BulkOpListener listener) {
        checkWritable();
        super.putAll(objects, listener);
    }

    @Override
    public ObjectInserter newObjectInserter() {
        return new ObjectInserter(this);
    }

    private void checkWritable() {
        if (readOnly) {
            throw new IllegalStateException(toString() + " is read only.");
        }
    }
}
