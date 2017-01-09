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

import java.util.Iterator;
import java.util.List;

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
import org.locationtech.geogig.storage.IndexDatabase;

import com.google.common.base.Optional;

/**
 * PostgreSQL implementation for {@link IndexDatabase}.
 * <p>
 * TODO: Refactor PGObjectDatabase implementation into a PGObjectStore class that this database can
 * be derived from. See the FileIndexDatabase/FileObjectDatabase implementations.
 */
public class PGIndexDatabase implements IndexDatabase {

    @Override
    public void open() {
        // TODO Auto-generated method stub

    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isOpen() {
        // TODO Auto-generated method stub
        return false;
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
    public void configure() throws RepositoryConnectionException {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isReadOnly() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        // TODO Auto-generated method stub

    }

    @Override
    public Index createIndex(String featureType, String attribute, IndexType strategy) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    @Override
    public Optional<Index> getIndex(String treeName, String attributeName) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void updateIndex(Index index, ObjectId originalTree, ObjectId indexedTree) {

    }

    @Override
    public Optional<ObjectId> resolveTreeId(Index index, ObjectId treeId) {

        return Optional.absent();
    }

}