/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.FORMAT_NAME;
import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.VERSION;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.Index;
import org.locationtech.geogig.repository.Index.IndexType;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.StorageType;

import com.google.common.base.Optional;
import com.google.inject.Inject;

/**
 * PostgreSQL implementation for {@link IndexDatabase}.
 * <p>
 * TODO: Refactor PGObjectDatabase implementation into a PGObjectStore class that this database can
 * be derived from. See the FileIndexDatabase/FileObjectDatabase implementations.
 */
public class PGIndexDatabase implements IndexDatabase {

    private ConfigDatabase configdb;

    private boolean isOpen = false;

    @Inject
    public PGIndexDatabase(final ConfigDatabase configdb) {
        this.configdb = configdb;
    }

    @Override
    public void open() {
        isOpen = true;
    }

    @Override
    public void close() {
        isOpen = false;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public boolean exists(ObjectId id) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public List<ObjectId> lookUp(String partialId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RevObject get(ObjectId id) throws IllegalArgumentException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends RevObject> T get(ObjectId id, Class<T> type) throws IllegalArgumentException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public @Nullable RevObject getIfPresent(ObjectId id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public @Nullable <T extends RevObject> T getIfPresent(ObjectId id, Class<T> type)
            throws IllegalArgumentException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RevTree getTree(ObjectId id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RevFeature getFeature(ObjectId id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RevFeatureType getFeatureType(ObjectId id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RevCommit getCommit(ObjectId id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RevTag getTag(ObjectId id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean put(RevObject object) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void delete(ObjectId objectId) {
        // TODO Auto-generated method stub

    }

    @Override
    public Iterator<RevObject> getAll(Iterable<ObjectId> ids) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Iterator<RevObject> getAll(Iterable<ObjectId> ids, BulkOpListener listener) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends RevObject> Iterator<T> getAll(Iterable<ObjectId> ids, BulkOpListener listener,
            Class<T> type) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void putAll(Iterator<? extends RevObject> objects) {
        // TODO Auto-generated method stub

    }

    @Override
    public void putAll(Iterator<? extends RevObject> objects, BulkOpListener listener) {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteAll(Iterator<ObjectId> ids) {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteAll(Iterator<ObjectId> ids, BulkOpListener listener) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        StorageType.INDEX.configure(configdb, FORMAT_NAME, VERSION);

    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        StorageType.INDEX.verify(configdb, FORMAT_NAME, VERSION);
    }

    @Override
    public Index createIndex(String featureType, String attribute, IndexType strategy,
            @Nullable Map<String, Object> metadata) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    @Override
    public Optional<Index> getIndex(String treeName, String attributeName) {
        return Optional.absent();
    }

    @Override
    public void addIndexedTree(Index index, ObjectId originalTree, ObjectId indexedTree) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    @Override
    public Optional<ObjectId> resolveIndexedTree(Index index, ObjectId treeId) {
        return Optional.absent();
    }

}