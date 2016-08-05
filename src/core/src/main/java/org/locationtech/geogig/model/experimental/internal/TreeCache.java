/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.experimental.internal;

import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterables;

class TreeCache {

    private final AtomicInteger idSequence = new AtomicInteger();

    private final BiMap<Integer, ObjectId> oidMapping = HashBiMap.create(10_000);

    private final LoadingCache<Integer, RevTree> cache;

    private final ObjectStore store;

    public TreeCache(final ObjectStore store) {
        this.store = store;

        final CacheLoader<Integer, RevTree> loader = new CacheLoader<Integer, RevTree>() {
            @Override
            public RevTree load(Integer key) throws Exception {
                ObjectId treeId = oidMapping.get(key);
                RevTree tree = TreeCache.this.store.getTree(treeId);
                return tree;
            }
        };
        this.cache = CacheBuilder.newBuilder().concurrencyLevel(1).maximumSize(100_000)
                .recordStats().build(loader);
    }

    public RevTree getTree(final ObjectId treeId) {
        Integer internalId = oidMapping.inverse().get(treeId);
        final RevTree tree;
        if (internalId == null) {
            tree = store.getTree(treeId);
            getTreeId(tree);
            if (tree.buckets().isPresent()) {
                preload(Iterables.transform(tree.buckets().get().values(), (b) -> b.getObjectId()));
            }
        } else {
            tree = resolve(internalId.intValue());
        }
        return tree;
    }

    public RevTree resolve(final int leafRevTreeId) {
        RevTree tree;
        try {
            tree = cache.get(Integer.valueOf(leafRevTreeId));
        } catch (ExecutionException e) {
            throw Throwables.propagate(e);
        }
        Preconditions.checkNotNull(tree);
        return tree;
    }

    public Integer getTreeId(RevTree tree) {
        Integer cacheId = oidMapping.inverse().get(tree.getId());
        if (cacheId == null) {
            cacheId = Integer.valueOf(idSequence.incrementAndGet());
            cache.put(cacheId, tree);
        }
        return cacheId;
    }

    public void preload(Iterable<ObjectId> trees) {
        // Stopwatch sw = Stopwatch.createStarted();
        Iterator<RevObject> preloaded = store.getAll(trees);
        // int c = 0;
        while (preloaded.hasNext()) {
            getTreeId((RevTree) preloaded.next());
            // c++;
        }
        // System.err.printf("preloaded %d trees in %s\n", c, sw.stop());
    }
}