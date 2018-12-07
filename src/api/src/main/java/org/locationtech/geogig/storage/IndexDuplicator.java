/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.storage.IndexDatabase.IndexTreeMapping;

import com.google.common.collect.Sets;
import com.google.common.collect.Streams;

import lombok.extern.slf4j.Slf4j;

/**
 * Helper class to copy all the spatial indexes from one {@link IndexDatabase} to another.
 * 
 * @see IndexDatabase#copyIndexesTo(IndexDatabase)
 */
public @Slf4j class IndexDuplicator {

    private static final ForkJoinPool FORK_JOIN_POOL;

    static {
        ForkJoinPool.ForkJoinWorkerThreadFactory threadFactoryShared = pool -> {
            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory
                    .newThread(pool);
            worker.setName("Index-duplicator-" + worker.getPoolIndex());
            return worker;
        };

        int parallelism = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        UncaughtExceptionHandler eh = (t, e) -> log
                .error("Uncaught ForkJoinPool exception at thread " + t.getName(), e);

        FORK_JOIN_POOL = new ForkJoinPool(parallelism, threadFactoryShared, eh, false);
    }

    private final IndexDatabase srcIndex;

    private final IndexDatabase targetIndex;

    private IndexInfo index;

    public IndexDuplicator(IndexDatabase srcIndex, IndexDatabase targetIndex) {
        this(srcIndex, targetIndex, null);
    }

    public IndexDuplicator(IndexDatabase srcIndex, IndexDatabase targetIndex, IndexInfo index) {
        this.srcIndex = srcIndex;
        this.targetIndex = targetIndex;
        this.index = index;
    }

    public void run() {
        List<ForkJoinTask<?>> tasks = new ArrayList<>();
        List<IndexInfo> indexInfos = index == null ? srcIndex.getIndexInfos()
                : Collections.singletonList(index);
        for (IndexInfo index : indexInfos) {
            ForkJoinTask<Void> task = FORK_JOIN_POOL
                    .submit(new IndexCopyTask(index, srcIndex, targetIndex));
            tasks.add(task);
        }
        RuntimeException err = null;
        for (ForkJoinTask<?> t : tasks) {
            try {
                if (err == null) {
                    t.join();
                } else {
                    t.cancel(true);
                }
            } catch (RuntimeException e) {
                err = e;
            }
        }
        if (err != null) {
            throw err;
        }
    }

    private static class IndexCopyTask extends RecursiveAction {
        private static final long serialVersionUID = -392580386346607639L;

        private IndexInfo index;

        private IndexDatabase src;

        private IndexDatabase target;

        public IndexCopyTask(IndexInfo index, IndexDatabase srcIndex, IndexDatabase targetIndex) {
            this.index = index;
            this.src = srcIndex;
            this.target = targetIndex;
        }

        protected @Override void compute() {
            List<TreeCopyTask> treeTasks = new ArrayList<>();
            Set<IndexTreeMapping> mappings = new HashSet<>();
            try (AutoCloseableIterator<IndexTreeMapping> indexMappings = src
                    .resolveIndexedTrees(index)) {
                mappings = Sets.newHashSet(indexMappings);
            }
            for (IndexTreeMapping mapping : mappings) {
                ObjectId indexTreeId = mapping.indexTree;
                if (!target.exists(indexTreeId)) {
                    TreeCopyTask task = new TreeCopyTask(src.getTree(indexTreeId), src, target);
                    treeTasks.add(task);
                }
            }
            super.invokeAll(treeTasks);
            IndexInfo targetIndex = target.createIndexInfo(index.getTreeName(),
                    index.getAttributeName(), index.getIndexType(), index.getMetadata());
            mappings.forEach(m -> target.addIndexedTree(targetIndex, m.featureTree, m.indexTree));
        }

    }

    private static class TreeCopyTask extends RecursiveAction {
        private static final long serialVersionUID = 2886427162222070369L;

        private RevTree tree;

        private IndexDatabase src;

        private IndexDatabase target;

        public TreeCopyTask(RevTree tree, IndexDatabase src, IndexDatabase target) {
            this.tree = tree;
            this.src = src;
            this.target = target;
        }

        protected @Override void compute() {
            List<RevTree> save = new ArrayList<>();
            save.add(tree);
            List<ForkJoinTask<?>> subTasks = new ArrayList<>();

            if (tree.bucketsSize() > 0) {
                Iterable<ObjectId> bucketIds;
                Iterator<RevTree> buckets;
                bucketIds = Streams.stream(tree.getBuckets()).map(Bucket::getObjectId)
                        .collect(Collectors.toList());
                buckets = src.getAll(bucketIds, BulkOpListener.NOOP_LISTENER, RevTree.class);
                while (buckets.hasNext()) {
                    RevTree bucket = buckets.next();
                    if (bucket.bucketsSize() == 0) {
                        save.add(bucket);
                    } else {
                        if (!target.exists(bucket.getId())) {
                            TreeCopyTask task = new TreeCopyTask(bucket, src, target);
                            subTasks.add(task);
                        }
                    }
                }
            }
            subTasks.add(new SaveTask(save, target));
            super.invokeAll(subTasks);
        }
    }

    private static class SaveTask extends RecursiveAction {
        private static final long serialVersionUID = 2067245580819452473L;

        private List<RevTree> save;

        private IndexDatabase target;

        public SaveTask(List<RevTree> save, IndexDatabase target) {
            this.save = save;
            this.target = target;
        }

        protected @Override void compute() {
            target.putAll(save.iterator());
        }
    }
}
