/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.di.caching;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.di.Decorator;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ForwardingObjectDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StagingDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.UncheckedExecutionException;
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

    private static class CachingStagingDatabase extends CachingObjectDatabase implements
            StagingDatabase {
        public CachingStagingDatabase(Provider<StagingDatabase> subject,
                Provider<? extends CacheFactory> cacheProvider) {
            super(subject, cacheProvider);
        }

        @Override
        public boolean hasConflicts(String namespace) {
            return ((StagingDatabase) subject.get()).hasConflicts(namespace);
        }

        @Override
        public Optional<Conflict> getConflict(String namespace, String path) {
            return ((StagingDatabase) subject.get()).getConflict(namespace, path);
        }

        @Override
        public List<Conflict> getConflicts(String namespace, String pathFilter) {
            return ((StagingDatabase) subject.get()).getConflicts(namespace, pathFilter);
        }

        @Override
        public void addConflict(String namespace, Conflict conflict) {
            ((StagingDatabase) subject.get()).addConflict(namespace, conflict);
        }

        @Override
        public void removeConflict(String namespace, String path) {
            ((StagingDatabase) subject.get()).removeConflict(namespace, path);
        }

        @Override
        public void removeConflicts(String namespace) {
            ((StagingDatabase) subject.get()).removeConflicts(namespace);
        }
    }

    private static class CachingObjectDatabase extends ForwardingObjectDatabase {

        private CacheHelper cache;

        public CachingObjectDatabase(final Provider<? extends ObjectDatabase> odb,
                final Provider<? extends CacheFactory> cacheProvider) {

            super(odb);
            this.cache = new CacheHelper(cacheProvider);
        }

        @Override
        public @Nullable RevObject getIfPresent(ObjectId id) {
            return cache.getIfPresent(id, super.subject.get());
        }

        @Override
        public @Nullable <T extends RevObject> T getIfPresent(ObjectId id, Class<T> type) {
            RevObject object = cache.getIfPresent(id, super.subject.get());
            return object == null ? null : type.cast(object);
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
        public RevTree getTree(ObjectId id) {
            return get(id, RevTree.class);
        }

        @Override
        public RevFeature getFeature(ObjectId id) {
            return get(id, RevFeature.class);
        }

        @Override
        public RevFeatureType getFeatureType(ObjectId id) {
            return get(id, RevFeatureType.class);
        }

        @Override
        public RevCommit getCommit(ObjectId id) {
            return get(id, RevCommit.class);
        }

        @Override
        public RevTag getTag(ObjectId id) {
            return get(id, RevTag.class);
        }

        @Override
        public Iterator<RevObject> getAll(final Iterable<ObjectId> ids) {
            return getAll(ids, BulkOpListener.NOOP_LISTENER);
        }

        @Override
        public Iterator<RevObject> getAll(final Iterable<ObjectId> ids, BulkOpListener listener) {
            return cache.getAll(ids, listener, super.subject.get());
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

    private static class ValueLoader implements Callable<RevObject> {

        private ObjectId id;

        private ObjectDatabase db;

        public ValueLoader(ObjectId id, ObjectDatabase db) {
            this.id = id;
            this.db = db;
        }

        @Override
        public RevObject call() throws Exception {
            RevObject object = db.get(id);
            return object;
        }

    }

    private static class CacheHelper {
        private Provider<? extends CacheFactory> cacheProvider;

        final boolean cacheFeatures = true;// TODO make configurable?

        public CacheHelper(final Provider<? extends CacheFactory> cacheProvider) {
            this.cacheProvider = cacheProvider;
        }

        @Nullable
        public RevObject getIfPresent(ObjectId id, ObjectDatabase db)
                throws IllegalArgumentException {
            final Cache<ObjectId, RevObject> cache = cacheProvider.get().get();
            RevObject obj = cache.getIfPresent(id);
            if (obj == null) {
                obj = db.getIfPresent(id);
                if (obj != null && isCacheable(obj, cacheFeatures)) {
                    cache.put(id, obj);
                }
            }
            return obj;
        }

        public RevObject get(ObjectId id, ObjectDatabase db) throws IllegalArgumentException {
            return get(id, RevObject.class, db);
        }

        public <T extends RevObject> T get(ObjectId id, Class<T> type, ObjectDatabase db)
                throws IllegalArgumentException {

            final Cache<ObjectId, RevObject> cache = cacheProvider.get().get();

            RevObject object;
            try {
                object = cache.get(id, new ValueLoader(id, db));
            } catch (ExecutionException | UncheckedExecutionException e) {
                Throwable cause = e.getCause();
                Throwables.propagateIfInstanceOf(cause, IllegalArgumentException.class);
                Throwables.propagateIfInstanceOf(cause, IllegalStateException.class);
                throw new RuntimeException(cause);
            }
            return type.cast(object);
        }

        public Iterator<RevObject> getAll(final Iterable<ObjectId> ids,
                final BulkOpListener listener, final ObjectDatabase db) {

            final int partitionSize = 10_000;
            Iterable<List<ObjectId>> partition = Iterables.partition(ids, partitionSize);

            final Cache<ObjectId, RevObject> cache = cacheProvider.get().get();

            List<Iterator<RevObject>> iterators = new LinkedList<>();

            final Set<ObjectId> miss = new HashSet<>();
            ImmutableMap<ObjectId, RevObject> present;

            for (List<ObjectId> p : partition) {
                Set<ObjectId> set = new HashSet<>(p);
                present = cache.getAllPresent(set);
                if (!present.isEmpty()) {
                    for (ObjectId id : present.keySet()) {
                        listener.found(id, null);
                        set.remove(id);
                    }
                    iterators.add(present.values().iterator());
                }
                miss.addAll(set);
            }
            if (!miss.isEmpty()) {
                Iterator<RevObject> iterator = new AbstractIterator<RevObject>() {

                    private Iterator<RevObject> delegate = db.getAll(miss, listener);

                    @Override
                    protected RevObject computeNext() {
                        if (delegate.hasNext()) {
                            RevObject next = delegate.next();
                            if (isCacheable(next, cacheFeatures)) {
                                cache.put(next.getId(), next);
                            }
                            return next;
                        }
                        return endOfData();
                    }
                };
                iterators.add(iterator);
            }
            return Iterators.concat(iterators.iterator());
        }

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

        private final boolean isCacheable(Object object, boolean cacheFeatures) {
            if (!cacheFeatures && object instanceof RevFeature) {
                return false;
            }
            // do not cache leaf trees. They tend to be quite large. TODO: make this configurable
            if ((object instanceof RevTree) && ((RevTree) object).features().isPresent()) {
                return false;
            }
            return object != null;
        }
    }

}
