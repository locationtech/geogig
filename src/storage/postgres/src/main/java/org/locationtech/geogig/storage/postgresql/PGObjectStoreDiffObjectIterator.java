/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.DiffObjectInfo;
import org.locationtech.geogig.storage.cache.ObjectCache;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;

class PGObjectStoreDiffObjectIterator<T extends RevObject>
        implements AutoCloseableIterator<DiffObjectInfo<T>> {

    private PeekingIterator<DiffEntry> nodes;

    private final Class<T> type;

    private final PGObjectStore store;

    private Iterator<DiffObjectInfo<T>> nextBatch;

    final ObjectCache cache;

    private boolean closed;

    private DiffObjectInfo<T> next;

    public PGObjectStoreDiffObjectIterator(Iterator<DiffEntry> refs, Class<T> type,
            PGObjectStore store) {
        this.nodes = Iterators.peekingIterator(refs);
        this.type = type;
        this.store = store;
        this.cache = store.sharedCache;
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
        {
            DiffObjectInfo<T> obj = tryNextCached();
            if (obj != null) {
                return obj;
            }
        }

        final int queryBatchSize = store.getAllBatchSize;
        final int superPartitionBatchSize = 10 * queryBatchSize;

        List<DiffObjectInfo<T>> hits = new LinkedList<>();
        List<DiffEntry> cacheMisses = new ArrayList<>(superPartitionBatchSize);
        for (int i = 0; i < superPartitionBatchSize && nodes.hasNext(); i++) {
            DiffEntry node = nodes.next();
            ObjectId idLeft = node.oldObjectId();
            ObjectId idRight = node.newObjectId();
            RevObject cachedLeft = idLeft.isNull() ? null : cache.getIfPresent(idLeft);
            RevObject cachedRight = idRight.isNull() ? null : cache.getIfPresent(idRight);
            if ((!idLeft.isNull() && cachedLeft == null)
                    || (!idRight.isNull() && cachedRight == null)) {
                cacheMisses.add(node);
            } else {
                hits.add(DiffObjectInfo.of(node, type.cast(cachedLeft), type.cast(cachedRight)));
            }
        }
        List<List<DiffEntry>> partitions = Lists.partition(cacheMisses, queryBatchSize);
        List<Future<List<DiffObjectInfo<T>>>> futures = new ArrayList<>(partitions.size());
        for (List<DiffEntry> partition : partitions) {
            Future<List<DiffObjectInfo<T>>> dbBatch;
            dbBatch = store.getObjects(partition, type);
            futures.add(dbBatch);
        }

        final Function<Future<List<DiffObjectInfo<T>>>, List<DiffObjectInfo<T>>> futureGetter = (
                objs) -> {
            try {
                return objs.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        };

        Iterable<List<DiffObjectInfo<T>>> lists = Iterables.transform(futures, futureGetter);
        Iterable<DiffObjectInfo<T>> concat = Iterables.concat(lists);
        Iterator<DiffObjectInfo<T>> iterator = concat.iterator();

        nextBatch = Iterators.concat(hits.iterator(), iterator);
        return computeNext();
    }

    private DiffObjectInfo<T> tryNextCached() {
        if (nodes.hasNext()) {
            DiffEntry node = nodes.peek();
            ObjectId idLeft = node.oldObjectId();
            ObjectId idRight = node.newObjectId();
            RevObject cachedLeft = idLeft.isNull() ? null : cache.getIfPresent(idLeft);
            RevObject cachedRight = idRight.isNull() ? null : cache.getIfPresent(idRight);
            if (!idLeft.isNull() && cachedLeft == null) {
                return null;
            }
            if (!idRight.isNull() && cachedRight == null) {
                return null;
            }
            nodes.next();// consume the peeked entry
            return DiffObjectInfo.of(node, type.cast(cachedLeft), type.cast(cachedRight));
        }
        return null;
    }
}