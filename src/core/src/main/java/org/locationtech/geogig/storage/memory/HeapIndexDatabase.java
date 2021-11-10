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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.decorator.ForwardingObjectStore;

import com.google.common.base.Preconditions;

/**
 * Provides an implementation of a GeoGig index database that utilizes the heap for the storage of
 * objects.
 * 
 * @see ForwardingObjectStore
 */
public class HeapIndexDatabase extends ForwardingObjectStore implements IndexDatabase {

    private final ConcurrentMap<String, List<IndexInfo>> indexes = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<ObjectId, Map<ObjectId, ObjectId>> indexTreeMappings = new ConcurrentHashMap<>();

    public HeapIndexDatabase() {
        super(new HeapObjectStore());
    }

    public HeapIndexDatabase(Hints hints) {
        super(new HeapObjectDatabase(hints));
    }

    public @Override String toString() {
        return getClass().getSimpleName();
    }

    private void addIndex(IndexInfo index) {
        String treeName = index.getTreeName();
        indexes.computeIfAbsent(treeName, k -> new ArrayList<>()).add(index);
    }

    public @Override IndexInfo createIndexInfo(String treeName, String attributeName,
            IndexType strategy, @Nullable Map<String, Object> metadata) {
        IndexInfo index = new IndexInfo(treeName, attributeName, strategy, metadata);
        addIndex(index);
        return index;
    }

    public @Override IndexInfo updateIndexInfo(String treeName, String attributeName,
            IndexType strategy, Map<String, Object> metadata) {
        IndexInfo newIndexInfo = new IndexInfo(treeName, attributeName, strategy, metadata);
        Optional<IndexInfo> oldIndexInfo = getIndexInfo(treeName, attributeName);
        Preconditions.checkState(oldIndexInfo.isPresent());
        List<IndexInfo> indexInfos = indexes.get(treeName);
        indexInfos.remove(oldIndexInfo.get());
        indexInfos.add(newIndexInfo);
        return newIndexInfo;
    }

    public @Override Optional<IndexInfo> getIndexInfo(String treeName, String attributeName) {
        if (indexes.containsKey(treeName)) {
            for (IndexInfo index : indexes.get(treeName)) {
                if (index.getAttributeName().equals(attributeName)) {
                    return Optional.of(index);
                }
            }
        }
        return Optional.empty();
    }

    public @Override List<IndexInfo> getIndexInfos(String treeName) {
        return indexes.getOrDefault(treeName, List.of());
    }

    public @Override List<IndexInfo> getIndexInfos() {
        return indexes.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    public @Override boolean dropIndex(IndexInfo index) {
        List<IndexInfo> treeIndexes = indexes.get(index.getTreeName());
        if (treeIndexes != null) {
            if (treeIndexes.contains(index)) {
                clearIndex(index);
                treeIndexes.remove(index);
                return true;
            }
        }
        return false;
    }

    public @Override void clearIndex(IndexInfo index) {
        indexTreeMappings.remove(index.getId());
    }

    public @Override void addIndexedTree(IndexInfo index, ObjectId originalTree,
            ObjectId indexedTree) {
        if (!indexTreeMappings.containsKey(index.getId())) {
            indexTreeMappings.put(index.getId(), new ConcurrentHashMap<ObjectId, ObjectId>());
        }
        indexTreeMappings.get(index.getId()).put(originalTree, indexedTree);
    }

    public @Override Optional<ObjectId> resolveIndexedTree(IndexInfo index, ObjectId treeId) {
        Map<ObjectId, ObjectId> indexMappings = indexTreeMappings.get(index.getId());
        if (indexMappings != null) {
            return Optional.ofNullable(indexMappings.get(treeId));
        }
        return Optional.empty();
    }

    public @Override AutoCloseableIterator<IndexTreeMapping> resolveIndexedTrees(IndexInfo index) {
        Map<ObjectId, ObjectId> indexMappings = indexTreeMappings.get(index.getId());
        if (indexMappings == null || indexMappings.isEmpty()) {
            return AutoCloseableIterator.emptyIterator();
        }
        indexMappings = new HashMap<>(indexMappings);
        Iterator<IndexTreeMapping> iterator = indexMappings.entrySet().stream()
                .map(e -> new IndexTreeMapping(e.getKey(), e.getValue())).iterator();
        return AutoCloseableIterator.fromIterator(iterator);
    }
}
