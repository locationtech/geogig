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
