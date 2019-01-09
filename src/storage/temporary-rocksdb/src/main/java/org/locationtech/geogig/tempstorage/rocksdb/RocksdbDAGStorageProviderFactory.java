/* Copyright (c) 2019 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.tempstorage.rocksdb;

import org.locationtech.geogig.model.internal.DAGStorageProvider;
import org.locationtech.geogig.model.internal.DAGStorageProviderFactory;
import org.locationtech.geogig.storage.ObjectStore;

import lombok.NonNull;

public class RocksdbDAGStorageProviderFactory implements DAGStorageProviderFactory {

    /**
     * @return {@code 1}, next highest priority than (heap) default one
     */
    public @Override int getPriority() {
        return 1;
    }

    public @Override DAGStorageProvider newInstance(@NonNull ObjectStore treeStore) {
        return new RocksdbDAGStorageProvider(treeStore);
    }
}
