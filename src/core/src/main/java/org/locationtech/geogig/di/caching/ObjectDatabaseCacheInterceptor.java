/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.di.caching;

import java.util.Iterator;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.di.Decorator;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ForwardingObjectDatabase;
import org.locationtech.geogig.storage.ForwardingStagingDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StagingDatabase;

import com.google.common.cache.Cache;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

/**
 * Method interceptor for {@linnk ObjectDatabase#get(...)} methods that applies caching.
 * <p>
 * <!-- increases random object lookup on revtrees by 20x, ~40K/s instad of ~2K/s as per
 * RevSHA1TreeTest.testPutGet -->
 */
class ObjectDatabaseCacheInterceptor {

    private ObjectDatabaseCacheInterceptor() {
        // force use of factory methods
    }

    public static Decorator staging(final Provider<? extends CacheFactory> cacheProvider) {

        return new Decorator() {
            @Override
            public boolean canDecorate(Object subject) {
                return subject instanceof StagingDatabase;
            }

            @SuppressWarnings("unchecked")
            @Override
            public StagingDatabase decorate(Object subject) {
                Provider<StagingDatabase> indexDb = Providers.of((StagingDatabase) subject);
                return new CachingStagingDatabase(indexDb, cacheProvider);
            }
        };
    }

    public static Decorator objects(final Provider<? extends CacheFactory> cacheProvider) {

        return new Decorator() {
            @Override
            public boolean canDecorate(Object subject) {
                return subject instanceof ObjectDatabase && (!(subject instanceof StagingDatabase));
            }

            @SuppressWarnings("unchecked")
            @Override
            public ObjectDatabase decorate(Object subject) {
                Provider<ObjectDatabase> odb = Providers.of((ObjectDatabase) subject);
                CachingObjectDatabase cachingObjectDatabase = new CachingObjectDatabase(odb,
                        cacheProvider);
                return cachingObjectDatabase;
            }
        };
    }

    private static class CachingStagingDatabase extends ForwardingStagingDatabase {

        private CacheHelper cache;

        public CachingStagingDatabase(Provider<StagingDatabase> subject,
                Provider<? extends CacheFactory> cacheProvider) {
            super(subject);
            this.cache = new CacheHelper(cacheProvider);
        }

        @Override
        public RevObject get(ObjectId id) throws IllegalArgumentException {
            return cache.get(id, super.subject.get());
        }

        @Override
        public <T extends RevObject> T get(ObjectId id, Class<T> type)
                throws IllegalArgumentException {
            return cache.get(id, type, super.subject.get());
        }

        @Override
        public boolean delete(ObjectId objectId) {
            return cache.delete(objectId, super.subject.get());
        }

        @Override
        public long deleteAll(Iterator<ObjectId> ids) {
            return deleteAll(ids, BulkOpListener.NOOP_LISTENER);
        }

        @Override
        public long deleteAll(Iterator<ObjectId> ids, BulkOpListener listener) {
            return cache.deleteAll(ids, listener, super.subject.get());
        }
    }

    private static class CachingObjectDatabase extends ForwardingObjectDatabase {

        private CacheHelper cache;

        public CachingObjectDatabase(final Provider<ObjectDatabase> odb,
                final Provider<? extends CacheFactory> cacheProvider) {

            super(odb);
            this.cache = new CacheHelper(cacheProvider);
        }

        @Override
        public RevObject get(ObjectId id) throws IllegalArgumentException {
            return cache.get(id, super.subject.get());
        }

        @Override
        public <T extends RevObject> T get(ObjectId id, Class<T> type)
                throws IllegalArgumentException {
            return cache.get(id, type, super.subject.get());
        }

        @Override
        public boolean delete(ObjectId objectId) {
            return cache.delete(objectId, super.subject.get());
        }

        @Override
        public long deleteAll(Iterator<ObjectId> ids) {
            return deleteAll(ids, BulkOpListener.NOOP_LISTENER);
        }

        @Override
        public long deleteAll(Iterator<ObjectId> ids, BulkOpListener listener) {
            return cache.deleteAll(ids, listener, super.subject.get());
        }
    }

    private static class CacheHelper {
        private Provider<? extends CacheFactory> cacheProvider;

        public CacheHelper(final Provider<? extends CacheFactory> cacheProvider) {
            this.cacheProvider = cacheProvider;
        }

        public RevObject get(ObjectId id, ObjectDatabase db) throws IllegalArgumentException {

            final Cache<ObjectId, RevObject> cache = cacheProvider.get().get();

            RevObject object = cache.getIfPresent(id);

            if (object == null) {
                object = db.get(id);
                if (isCacheable(object)) {
                    cache.put(id, object);
                }
            }
            return object;
        }

        public <T extends RevObject> T get(ObjectId id, Class<T> type, ObjectDatabase db)
                throws IllegalArgumentException {

            final Cache<ObjectId, RevObject> cache = cacheProvider.get().get();

            RevObject object = cache.getIfPresent(id);

            if (object == null) {
                object = db.get(id, type);
                if (isCacheable(object)) {
                    cache.put(id, object);
                }
            }
            return type.cast(object);
        }

        // public Iterator<RevObject> getAll(final Iterable<ObjectId> ids) {
        //
        // }
        //
        // public Iterator<RevObject> getAll(final Iterable<ObjectId> ids, BulkOpListener listener)
        // {
        //
        // }

        public boolean delete(ObjectId objectId, ObjectDatabase db) {
            boolean deleted = db.delete(objectId);
            if (deleted) {
                final Cache<ObjectId, RevObject> cache = cacheProvider.get().get();
                cache.invalidate(objectId);
            }
            return deleted;
        }

        public long deleteAll(Iterator<ObjectId> ids, BulkOpListener listener, ObjectDatabase db) {

            final BulkOpListener invalidatingListener = new BulkOpListener() {

                final Cache<ObjectId, RevObject> cache = cacheProvider.get().get();

                @Override
                public void deleted(ObjectId id) {
                    cache.invalidate(id);
                }
            };

            return db.deleteAll(ids, BulkOpListener.composite(listener, invalidatingListener));
        }

        private final boolean isCacheable(Object object) {
            if (object == null) {
                return false;
            }
            if (object instanceof RevFeatureType) {
                return true;
            }
            if ((object instanceof RevTree) && ((RevTree) object).buckets().isPresent()) {
                return true;
            }
            return false;
        }
    }

}
