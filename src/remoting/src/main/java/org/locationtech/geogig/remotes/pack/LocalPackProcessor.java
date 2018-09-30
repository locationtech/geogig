/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.pack;

import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.remotes.internal.Deduplicator;
import org.locationtech.geogig.remotes.pack.Pack.IndexDef;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.BulkOpListener.CountingListener;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;

import lombok.NonNull;

public class LocalPackProcessor implements PackProcessor {

    private ObjectStore target;

    private IndexDatabase targetIndexdb;

    public LocalPackProcessor(@NonNull ObjectStore targetStore,
            @NonNull IndexDatabase targetIndexdb) {
        this.target = targetStore;
        this.targetIndexdb = targetIndexdb;
    }

    public @Override void putAll(Iterator<? extends RevObject> iterator, BulkOpListener listener) {
        target.putAll(iterator, listener);
    }

    public @Override void putIndex(//@formatter:off
            @NonNull IndexDef indexDef, 
            @NonNull IndexDatabase sourceStore,
            @NonNull ObjectReporter objectReport, 
            @NonNull Deduplicator deduplicator
            ) {//@formatter:on

        final IndexDatabase indexdb = targetIndexdb;
        final String treeName = indexDef.getIndex().getTreeName();
        final String attributeName = indexDef.getIndex().getAttributeName();
        final Optional<IndexInfo> existingIndex = indexdb.getIndexInfo(treeName, attributeName);

        if (!existingIndex.isPresent()) {
            sourceStore.copyIndexTo(indexDef.getIndex(), indexdb);
            return;
        }
        final ExecutorService producerThread = Executors.newSingleThreadExecutor();
        Iterator<RevTree> missingContents;
        try {
            final ObjectId oldIndexTreeId = indexDef.getParentIndexTreeId();
            final ObjectId newIndexTreeId = indexDef.getIndexTreeId();

            List<ObjectId[]> treeIds = Collections
                    .singletonList(new ObjectId[] { oldIndexTreeId, newIndexTreeId });
            final ContentIdsProducer producer = ContentIdsProducer.forIndex(indexDef.getIndex(),
                    sourceStore, treeIds, deduplicator, objectReport);

            producerThread.submit(producer);
            Iterator<ObjectId> missingContentIds = producer.iterator();

            missingContents = sourceStore.getAll(() -> missingContentIds, NOOP_LISTENER,
                    RevTree.class);
            IndexInfo indexInfo = indexDef.getIndex();
            CountingListener c = new BulkOpListener.CountingListener();
            indexdb.putAll(missingContents, c);

            ObjectId originalTree = indexDef.getCanonical();
            ObjectId indexedTree = indexDef.getIndexTreeId();
            indexdb.addIndexedTree(indexInfo, originalTree, indexedTree);
        } finally {
            producerThread.shutdownNow();
        }
    }

}
