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

import java.util.Iterator;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.remotes.pack.Pack.IndexDef;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.storage.BulkOpListener;
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

    public @Override void putIndex(IndexDef indexDef, Iterator<RevTree> indexContents,
            BulkOpListener listener) {

        IndexDatabase indexdb = targetIndexdb;

        String treeName = indexDef.getIndex().getTreeName();
        String attributeName = indexDef.getIndex().getAttributeName();
        Optional<IndexInfo> existingIndex = indexdb.getIndexInfo(treeName, attributeName);
        IndexInfo indexInfo = indexDef.getIndex();

        indexdb.putAll(indexContents, listener);

        if (!existingIndex.isPresent()) {
            indexInfo = indexdb.createIndexInfo(treeName, attributeName, indexInfo.getIndexType(),
                    indexInfo.getMetadata());
        }
        ObjectId originalTree = indexDef.getCanonical();
        ObjectId indexedTree = indexDef.getIndexTreeId();
        indexdb.addIndexedTree(indexInfo, originalTree, indexedTree);
    }

}
