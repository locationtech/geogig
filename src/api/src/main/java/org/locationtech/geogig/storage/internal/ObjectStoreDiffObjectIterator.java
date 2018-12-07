/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage.internal;

import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.DiffObjectInfo;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

public class ObjectStoreDiffObjectIterator<T extends RevObject>
        implements AutoCloseableIterator<DiffObjectInfo<T>> {

    private PeekingIterator<DiffEntry> nodes;

    private final Class<T> type;

    private final ObjectStore leftStore, rightStore;

    private Iterator<DiffObjectInfo<T>> nextBatch;

    private boolean closed;

    private DiffObjectInfo<T> next;

    private int getAllBatchSize = 1_000;

    public ObjectStoreDiffObjectIterator(//@formatter:off
            Iterator<DiffEntry> refs, 
            Class<T> type,
            ObjectStore store) {//@formatter:on
        this(refs, type, store, store);
    }

    public ObjectStoreDiffObjectIterator(//@formatter:off
            Iterator<DiffEntry> refs, 
            Class<T> type,
            ObjectStore leftStore,
            ObjectStore rightStore) {//@formatter:on
        this.nodes = Iterators.peekingIterator(refs);
        this.type = type;
        this.leftStore = leftStore;
        this.rightStore = rightStore;
    }

    public @Override void close() {
        closed = true;
        nodes = null;
        nextBatch = null;
    }

    public @Override boolean hasNext() {
        if (closed) {
            return false;
        }
        if (next == null) {
            next = computeNext();
        }
        return next != null;
    }

    public @Override DiffObjectInfo<T> next() {
        if (closed) {
            throw new NoSuchElementException("Iterator is closed");
        }
        final DiffObjectInfo<T> curr;
        if (next == null) {
            curr = computeNext();
        } else {
            curr = next;
            next = null;
        }
        if (curr == null) {
            throw new NoSuchElementException();
        }
        return curr;
    }

    private @Nullable DiffObjectInfo<T> computeNext() {
        if (nextBatch != null && nextBatch.hasNext()) {
            return nextBatch.next();
        }
        if (!nodes.hasNext()) {
            return null;
        }

        final int queryBatchSize = this.getAllBatchSize;

        List<DiffEntry> nextEntries = Iterators.partition(this.nodes, queryBatchSize).next();
        Set<ObjectId> leftEntriesIds = new HashSet<>();
        Set<ObjectId> rightEntriesIds = this.leftStore == this.rightStore ? leftEntriesIds
                : new HashSet<>();

        nextEntries.forEach(e -> {
            ObjectId oldId = e.oldObjectId();
            ObjectId newId = e.newObjectId();
            if (!oldId.isNull()) {
                leftEntriesIds.add(oldId);
            }
            if (!newId.isNull()) {
                rightEntriesIds.add(newId);
            }
        });

        Iterator<T> objects = leftStore.getAll(leftEntriesIds, NOOP_LISTENER, this.type);
        if (rightEntriesIds != leftEntriesIds && !rightEntriesIds.isEmpty()) {
            objects = Iterators.concat(objects,
                    rightStore.getAll(rightEntriesIds, NOOP_LISTENER, this.type));
        }
        Map<ObjectId, T> objectsById = new HashMap<>();
        objects.forEachRemaining(o -> objectsById.putIfAbsent(o.getId(), o));
        nextBatch = createBatch(nextEntries, objectsById);
        return computeNext();
    }

    private Iterator<DiffObjectInfo<T>> createBatch(List<DiffEntry> entries,
            Map<ObjectId, T> values) {

        return entries.stream().map(e -> toDiffObject(e, values)).iterator();
    }

    private DiffObjectInfo<T> toDiffObject(DiffEntry e, Map<ObjectId, T> values) {
        T oldValue = values.get(e.oldObjectId());
        T newValue = values.get(e.newObjectId());
        return new DiffObjectInfo<>(e, oldValue, newValue);
    }
}