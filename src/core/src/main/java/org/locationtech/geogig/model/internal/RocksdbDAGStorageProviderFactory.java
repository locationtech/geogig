/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.model.internal;

import org.locationtech.geogig.storage.ObjectStore;

class RocksdbDAGStorageProviderFactory implements DAGStorageProviderFactory {

    private final ObjectStore source;

    public RocksdbDAGStorageProviderFactory(ObjectStore store) {
        this.source = store;
    }

    @Override
    public RocksdbDAGStorageProvider canonical() {
        return new RocksdbDAGStorageProvider(source, new TreeCache(source));
    }

    @Override
    public RocksdbDAGStorageProvider quadtree() {
        throw new UnsupportedOperationException();
    }

}
