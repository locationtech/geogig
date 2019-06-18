/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage.decorator;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.DiffObjectInfo;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectInfo;

import lombok.RequiredArgsConstructor;

public @RequiredArgsConstructor class ForwardingIndexDatabase implements IndexDatabase {

    protected final IndexDatabase actual;

    public @Override void open() {
        actual.open();
    }

    public @Override void close() {
        actual.close();
    }

    public @Override boolean isOpen() {
        return actual.isOpen();
    }

    public @Override boolean isReadOnly() {
        return actual.isReadOnly();
    }

    public @Override void checkWritable() {
        actual.checkWritable();
    }

    public @Override IndexInfo createIndexInfo(String treeName, String attributeName,
            IndexType strategy, @Nullable Map<String, Object> metadata) {
        return actual.createIndexInfo(treeName, attributeName, strategy, metadata);
    }

    public @Override boolean exists(ObjectId id) {
        return actual.exists(id);
    }

    public @Override void checkOpen() {
        actual.checkOpen();
    }

    public @Override List<ObjectId> lookUp(String partialId) {
        return actual.lookUp(partialId);
    }

    public @Override IndexInfo updateIndexInfo(String treeName, String attributeName,
            IndexType strategy, Map<String, Object> metadata) {
        return actual.updateIndexInfo(treeName, attributeName, strategy, metadata);
    }

    public @Override RevObject get(ObjectId id) throws IllegalArgumentException {
        return actual.get(id);
    }

    public @Override <T extends RevObject> T get(ObjectId id, Class<T> type)
            throws IllegalArgumentException {
        return actual.get(id, type);
    }

    public @Override Optional<IndexInfo> getIndexInfo(String treeName, String attributeName) {
        return actual.getIndexInfo(treeName, attributeName);
    }

    public @Override List<IndexInfo> getIndexInfos(String treeName) {
        return actual.getIndexInfos(treeName);
    }

    public @Override RevObject getIfPresent(ObjectId id) {
        return actual.getIfPresent(id);
    }

    public @Override List<IndexInfo> getIndexInfos() {
        return actual.getIndexInfos();
    }

    public @Override <T extends RevObject> T getIfPresent(ObjectId id, Class<T> type)
            throws IllegalArgumentException {
        return actual.getIfPresent(id, type);
    }

    public @Override boolean dropIndex(IndexInfo index) {
        return actual.dropIndex(index);
    }

    public @Override void clearIndex(IndexInfo index) {
        actual.clearIndex(index);
    }

    public @Override RevTree getTree(ObjectId id) {
        return actual.getTree(id);
    }

    public @Override void addIndexedTree(IndexInfo index, ObjectId originalTree,
            ObjectId indexedTree) {
        actual.addIndexedTree(index, originalTree, indexedTree);
    }

    public @Override RevFeature getFeature(ObjectId id) {
        return actual.getFeature(id);
    }

    public @Override RevFeatureType getFeatureType(ObjectId id) {
        return actual.getFeatureType(id);
    }

    public @Override Optional<ObjectId> resolveIndexedTree(IndexInfo index, ObjectId treeId) {
        return actual.resolveIndexedTree(index, treeId);
    }

    public @Override RevCommit getCommit(ObjectId id) {
        return actual.getCommit(id);
    }

    public @Override RevTag getTag(ObjectId id) {
        return actual.getTag(id);
    }

    public @Override boolean put(RevObject object) {
        return actual.put(object);
    }

    public @Override AutoCloseableIterator<IndexTreeMapping> resolveIndexedTrees(IndexInfo index) {
        return actual.resolveIndexedTrees(index);
    }

    public @Override void copyIndexesTo(IndexDatabase target) {
        actual.copyIndexesTo(target);
    }

    public @Override void delete(ObjectId objectId) {
        actual.delete(objectId);
    }

    public @Override Iterator<RevObject> getAll(Iterable<ObjectId> ids) {
        return actual.getAll(ids);
    }

    public @Override void copyIndexTo(IndexInfo index, IndexDatabase target) {
        actual.copyIndexTo(index, target);
    }

    public @Override Iterator<RevObject> getAll(Iterable<ObjectId> ids, BulkOpListener listener) {
        return actual.getAll(ids, listener);
    }

    public @Override <T extends RevObject> Iterator<T> getAll(Iterable<ObjectId> ids,
            BulkOpListener listener, Class<T> type) {
        return actual.getAll(ids, listener, type);
    }

    public @Override void putAll(Iterator<? extends RevObject> objects) {
        actual.putAll(objects);
    }

    public @Override void putAll(Iterator<? extends RevObject> objects, BulkOpListener listener) {
        actual.putAll(objects, listener);
    }

    public @Override void deleteAll(Iterator<ObjectId> ids) {
        actual.deleteAll(ids);
    }

    public @Override void deleteAll(Iterator<ObjectId> ids, BulkOpListener listener) {
        actual.deleteAll(ids, listener);
    }

    public @Override <T extends RevObject> AutoCloseableIterator<ObjectInfo<T>> getObjects(
            Iterator<NodeRef> nodes, BulkOpListener listener, Class<T> type) {
        return actual.getObjects(nodes, listener, type);
    }

    public @Override <T extends RevObject> AutoCloseableIterator<DiffObjectInfo<T>> getDiffObjects(
            Iterator<DiffEntry> diffEntries, Class<T> type) {
        return actual.getDiffObjects(diffEntries, type);
    }
}
