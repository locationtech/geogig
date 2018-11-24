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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.cache.ObjectCache;

import com.google.common.base.Function;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;

class PGObjectStoreGetAllIterator<T extends RevObject> extends AbstractIterator<T> {

    private final PeekingIterator<ObjectId> ids;

    private final Class<T> type;

    private final BulkOpListener listener;

    private final PGObjectStore store;

    private Iterator<T> nextBatch;

    final ObjectCache cache;

    public PGObjectStoreGetAllIterator(Iterator<ObjectId> ids, Class<T> type,
            BulkOpListener listener, PGObjectStore store) {
        this.ids = Iterators.peekingIterator(ids);
        this.type = type;
        this.listener = listener;
        this.store = store;
        cache = store.sharedCache;
    }

    @Override
    protected T computeNext() {
        if (nextBatch != null && nextBatch.hasNext()) {
            return nextBatch.next();
        }
        if (!ids.hasNext()) {
            return endOfData();
        }
        {
            T obj = tryNextCached();
            if (obj != null) {
                return obj;
            }
        }

        final int queryBatchSize = store.getAllBatchSize;
        final int superPartitionBatchSize = 10 * queryBatchSize;

        List<T> hits = new LinkedList<>();
        List<ObjectId> cacheMisses = new ArrayList<>(superPartitionBatchSize);
        for (int i = 0; i < superPartitionBatchSize && ids.hasNext(); i++) {
            ObjectId id = ids.next();
            RevObject cached = cache.getIfPresent(id);
            if (cached == null) {
                cacheMisses.add(id);
            } else {
                T obj = cacheHit(id, cached);
                if (obj != null) {
                    hits.add(obj);
                }
            }
        }
        List<List<ObjectId>> partitions = Lists.partition(cacheMisses, queryBatchSize);
        List<Future<List<T>>> futures = new ArrayList<>(partitions.size());
        for (List<ObjectId> partition : partitions) {
            Future<List<T>> dbBatch;
            dbBatch = store.getAll(partition, listener, type);
            futures.add(dbBatch);
        }

        final Function<Future<List<T>>, List<T>> futureGetter = (objs) -> {
            try {
                return objs.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        };

        Iterable<List<T>> lists = Iterables.transform(futures, futureGetter);
        Iterable<T> concat = Iterables.concat(lists);
        Iterator<T> iterator = concat.iterator();

        nextBatch = Iterators.concat(hits.iterator(), iterator);
        return computeNext();
    }

    private T tryNextCached() {
        while (ids.hasNext()) {
            ObjectId id = ids.peek();
            RevObject cached = cache.getIfPresent(id);
            if (cached == null) {
                return null;
            } else {
                ids.next();
                T obj = cacheHit(id, cached);
                if (obj != null) {
                    return obj;
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