package org.locationtech.geogig.storage.cache;

public interface CacheStats {

    default long hitCount() {
        return 0L;
    }

    default double hitRate() {
        return 0D;
    }

    default long missCount() {
        return 0L;
    }

    default double missRate() {
        return 0D;
    }

    default long evictionCount() {
        return 0L;
    }

}
