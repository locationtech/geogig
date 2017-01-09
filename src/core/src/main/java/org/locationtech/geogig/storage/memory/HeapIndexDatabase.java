/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Index;
import org.locationtech.geogig.repository.Index.IndexType;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.impl.ConnectionManager;
import org.locationtech.geogig.storage.impl.ForwardingObjectStore;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.hash.Hasher;

/**
 * Provides an implementation of a GeoGig index database that utilizes the heap for the storage of
 * objects.
 * 
 * @see ForwardingObjectStore
 */
public class HeapIndexDatabase extends ForwardingObjectStore implements IndexDatabase {

    static HeapObjectDatabaseConnectionManager CONN_MANAGER = new HeapObjectDatabaseConnectionManager();

    private Map<String, List<Index>> indexes;

    private Map<ObjectId, ObjectId> indexTreeMappings;

    public HeapIndexDatabase() {
        super(new HeapObjectStore(), false);
    }

    public HeapIndexDatabase(Platform platform, Hints hints) {
        super(connect(platform), readOnly(hints));
    }

    private static HeapObjectStore connect(Platform platform) {
        return CONN_MANAGER.acquire(platform.pwd().toPath());
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
    }

    @Override
    public boolean isReadOnly() {
        return !super.canWrite;
    }

    @Override
    public void configure() {
        // No-op
    }

    @Override
    public void checkConfig() {
        // No-op
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

    private void addIndex(Index index) {
        String treeName = index.getTreeName();
        if (indexes.containsKey(treeName)) {
            indexes.get(treeName).add(index);
        } else {
            indexes.put(treeName, Lists.newArrayList(index));
        }
    }

    @Override
    public Index createIndex(String treeName, String attributeName, IndexType strategy) {
        Index index = new Index(treeName, attributeName, strategy);
        addIndex(index);
        return index;
    }

    @Override
    public Optional<Index> getIndex(String treeName, String attributeName) {
        if (indexes.containsKey(treeName)) {
            for (Index index : indexes.get(treeName)) {
                if (index.getAttributeName().equals(attributeName)) {
                    return Optional.of(index);
                }
            }
        }
        return Optional.absent();
    }

    @Override
    public void updateIndex(Index index, ObjectId originalTree, ObjectId indexedTree) {
        ObjectId indexTreeLookupId = computeIndexTreeLookupId(index.getId(), originalTree);
        indexTreeMappings.put(indexTreeLookupId, indexedTree);
    }

    @Override
    public Optional<ObjectId> resolveTreeId(Index index, ObjectId treeId) {
        ObjectId indexTreeLookupId = computeIndexTreeLookupId(index.getId(), treeId);
        return Optional.fromNullable(indexTreeMappings.get(indexTreeLookupId));
    }

    private ObjectId computeIndexTreeLookupId(ObjectId indexId, ObjectId treeId) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        hasher.putBytes(indexId.getRawValue());
        hasher.putBytes(treeId.getRawValue());
        return ObjectId.createNoClone(hasher.hash().asBytes());
    }
}
