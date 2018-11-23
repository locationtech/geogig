/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.geogig.storage.cache.ObjectCache;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;

class PGObjectStoreObjectIterator<T extends RevObject>
        implements AutoCloseableIterator<ObjectInfo<T>> {

    private PeekingIterator<NodeRef> nodes;

    private final Class<T> type;

    private final BulkOpListener listener;

    private final PGObjectStore store;

    private Iterator<ObjectInfo<T>> nextBatch;

    final ObjectCache cache;

    private boolean closed;

    private ObjectInfo<T> next;

    public PGObjectStoreObjectIterator(Iterator<NodeRef> refs, Class<T> type,
            BulkOpListener listener, PGObjectStore store) {
        this.nodes = Iterators.peekingIterator(refs);
        this.type = type;
        this.listener = listener;
        this.store = store;
        cache = store.sharedCache;
    }

    @Override
    public void close() {
        closed = true;
        nodes = null;
        nextBatch = null;
    }

    @Override
    public boolean hasNext() {
        if (closed) {
            return false;
        }
        if (next == null) {
            next = computeNext();
        }
        return next != null;
    }

    @Override
    public ObjectInfo<T> next() {
        if (closed) {
            throw new NoSuchElementException("Iterator is closed");
        }
        final ObjectInfo<T> curr;
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

    private @Nullable ObjectInfo<T> computeNext() {
        if (nextBatch != null && nextBatch.hasNext()) {
            return nextBatch.next();
        }
        if (!nodes.hasNext()) {
            return null;
        }
        {
            ObjectInfo<T> obj = tryNextCached();
            if (obj != null) {
                return obj;
            }
        }

        final int queryBatchSize = store.getAllBatchSize;
        final int superPartitionBatchSize = 10 * queryBatchSize;

        List<ObjectInfo<T>> hits = new LinkedList<>();
        List<NodeRef> cacheMisses = new ArrayList<>(superPartitionBatchSize);
        for (int i = 0; i < superPartitionBatchSize && nodes.hasNext(); i++) {
            NodeRef node = nodes.next();
            ObjectId id = node.getObjectId();
            RevObject cached = cache.getIfPresent(id);
            if (cached == null) {
                cacheMisses.add(node);
            } else {
                T obj = cacheHit(id, cached);
                if (obj != null) {
                    hits.add(new ObjectInfo<T>(node, obj));
                }
            }
        }
        List<List<NodeRef>> partitions = Lists.partition(cacheMisses, queryBatchSize);
        List<Future<List<ObjectInfo<T>>>> futures = new ArrayList<>(partitions.size());
        for (List<NodeRef> partition : partitions) {
            Future<List<ObjectInfo<T>>> dbBatch;
            dbBatch = store.getObjects(partition, listener, type);
            futures.add(dbBatch);
        }

        final Function<Future<List<ObjectInfo<T>>>, List<ObjectInfo<T>>> futureGetter = (objs) -> {
            try {
                return objs.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        };

        Iterable<List<ObjectInfo<T>>> lists = Iterables.transform(futures, futureGetter);
        Iterable<ObjectInfo<T>> concat = Iterables.concat(lists);
        Iterator<ObjectInfo<T>> iterator = concat.iterator();

        nextBatch = Iterators.concat(hits.iterator(), iterator);
        return computeNext();
    }

    private ObjectInfo<T> tryNextCached() {
        while (nodes.hasNext()) {
            NodeRef node = nodes.peek();
            ObjectId id = node.getObjectId();
            RevObject cached = cache.getIfPresent(id);
            if (cached == null) {
                return null;
            } else {
                nodes.next();
                T obj = cacheHit(id, cached);
                if (obj != null) {
                    return new ObjectInfo<T>(node, obj);
                }
            }
        }
        return null;
    }

    @Nullable
    private T cacheHit(ObjectId id, RevObject object) {
        if (type.isInstance(object)) {
            listener.found(id, null);
            return type.cast(object);
        } else {
            listener.notFound(id);
        }
        return null;
    }
}