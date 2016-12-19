/* Copyright (c) 2014-2016 Boundless and others.
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.di.Decorator;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.impl.ForwardingObjectDatabase;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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

    public static Decorator objects(final Provider<? extends CacheFactory> cacheProvider) {

        return new Decorator() {
            @Override
            public boolean canDecorate(Object subject) {
                return subject instanceof ObjectDatabase;
            }

            @SuppressWarnings("unchecked")
            @Override
            public ObjectStore decorate(Object subject) {
                Provider<ObjectDatabase> odb = Providers.of((ObjectDatabase) subject);
                CachingObjectDatabase cachingObjectDatabase = new CachingObjectDatabase(odb,
                        cacheProvider);
                return cachingObjectDatabase;
            }
        };
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
            return getAll(ids, listener, RevObject.class);
        }

        @Override
        public <T extends RevObject> Iterator<T> getAll(final Iterable<ObjectId> ids,
                BulkOpListener listener, Class<T> type) {
            return cache.getAll(ids, listener, type, super.subject.get());
        }

        @Override
        public void delete(ObjectId objectId) {
            cache.delete(objectId, super.subject.get());
        }

        @Override
        public void deleteAll(Iterator<ObjectId> ids) {
            deleteAll(ids, BulkOpListener.NOOP_LISTENER);
        }

        @Override
        public void deleteAll(Iterator<ObjectId> ids, BulkOpListener listener) {
            cache.deleteAll(ids, listener, super.subject.get());
        }
    }

    private static class ValueLoader implements Callable<RevObject> {

        private ObjectId id;

        private ObjectStore db;

        public ValueLoader(ObjectId id, ObjectStore db) {
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

        final boolean cacheFeatures = false;// TODO make configurable?

        public CacheHelper(final Provider<? extends CacheFactory> cacheProvider) {
            this.cacheProvider = cacheProvider;
        }

        @Nullable
        public RevObject getIfPresent(ObjectId id, ObjectStore db) throws IllegalArgumentException {
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

        public RevObject get(ObjectId id, ObjectStore db) throws IllegalArgumentException {
            return get(id, RevObject.class, db);
        }

        public <T extends RevObject> T get(ObjectId id, Class<T> type, ObjectStore db)
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

        public <T extends RevObject> Iterator<T> getAll(final Iterable<ObjectId> ids,
                final BulkOpListener listener, final Class<T> type, final ObjectStore db) {

            final int partitionSize = 10_000;
            Iterable<List<ObjectId>> partition = Iterables.partition(ids, partitionSize);

            final Cache<ObjectId, RevObject> cache = cacheProvider.get().get();

            List<Iterator<T>> iterators = new LinkedList<>();

            final Set<ObjectId> missing = new HashSet<>();

            for (List<ObjectId> p : partition) {
                final ImmutableSet<ObjectId> partitionIds = ImmutableSet.copyOf(p);
                Map<ObjectId, RevObject> present = Maps.filterValues(
                        cache.getAllPresent(partitionIds),
                        (o) -> type.isAssignableFrom(o.getClass()));

                if (present.isEmpty()) {
                    missing.addAll(partitionIds);
                } else {
                    missing.addAll(Sets.difference(partitionIds, present.keySet()));
                    Function<RevObject, T> function = (o) -> {
                        listener.found(o.getId(), null);
                        return type.cast(o);
                    };
                    Iterator<T> notifyingIterator = Iterators.transform(present.values().iterator(),
                            function);
                    iterators.add(notifyingIterator);
                }
            }
            if (!missing.isEmpty()) {
                Iterator<T> iterator = new AbstractIterator<T>() {

                    private Iterator<T> delegate = db.getAll(missing, listener, type);

                    @Override
                    protected T computeNext() {
                        if (delegate.hasNext()) {
                            T next = delegate.next();
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

        public void delete(ObjectId objectId, ObjectStore db) {
            db.delete(objectId);
            final Cache<ObjectId, RevObject> cache = cacheProvider.get().get();
            cache.invalidate(objectId);
        }

        public void deleteAll(Iterator<ObjectId> ids, BulkOpListener listener, ObjectStore db) {

            final BulkOpListener invalidatingListener = new BulkOpListener() {

                final Cache<ObjectId, RevObject> cache = cacheProvider.get().get();

                @Override
                public void deleted(ObjectId id) {
                    cache.invalidate(id);
                }
            };

            db.deleteAll(ids, BulkOpListener.composite(listener, invalidatingListener));
        }

        private final boolean isCacheable(Object object, boolean cacheFeatures) {
            if (!cacheFeatures && object instanceof RevFeature) {
                return false;
            }
            return object != null;
        }
    }

}
