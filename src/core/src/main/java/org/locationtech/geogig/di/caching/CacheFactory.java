/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.di.caching;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.porcelain.ConfigException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.Provider;

abstract class CacheFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheFactory.class);

    private volatile Cache<ObjectId, RevObject> cache;

    private final Provider<ConfigDatabase> configDb;

    private final String configKeywordPrefix;

    public CacheFactory(final String configKeywordPrefix, final Provider<ConfigDatabase> configDb) {
        this.configKeywordPrefix = configKeywordPrefix;
        this.configDb = configDb;
    }

    public Cache<ObjectId, RevObject> get() {
        if (cache == null) {
            createCache();
        }
        return cache;
    }

    protected synchronized void createCache() {
        if (cache != null) {
            return;
        }
        if (!cacheIsEnabled()) {
            this.cache = NO_CACHE;
            return;
        }
        final int maxSize = getConfig("maxSize", 50_000);
        final int concurrencyLevel = getConfig("concurrencyLevel", 4);

        final int expireSeconds = getConfig("expireSeconds", 300);
        final int initialCapacity = getConfig("initialCapacity", 10 * 1000);
        CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder();
        cacheBuilder = cacheBuilder.maximumSize(maxSize);
        cacheBuilder.expireAfterAccess(expireSeconds, TimeUnit.SECONDS);
        cacheBuilder.initialCapacity(initialCapacity);
        cacheBuilder.concurrencyLevel(concurrencyLevel);
        cacheBuilder.softValues();

        try {
            this.cache = cacheBuilder.build();
        } catch (RuntimeException e) {
            LOGGER.error(
                    "Error configuring cache '{}' with maxSize: {}, expireSeconds: {}, initialCapacity: {}, concurrencyLevel: {}",
                    configKeywordPrefix, maxSize, expireSeconds, initialCapacity, concurrencyLevel,
                    e);

            throw e;
        }

        LOGGER.debug(
                "Cache '{}' configured with maxSize: {}, expireSeconds: {}, initialCapacity: {}, concurrencyLevel: {}",
                configKeywordPrefix, maxSize, expireSeconds, initialCapacity, concurrencyLevel);

    }

    private boolean cacheIsEnabled() {
        LOGGER.debug("checking if cache {} is enabled...", configKeywordPrefix);
        final boolean enabled = getConfig("enabled", Boolean.TRUE);
        if (!enabled) {
            LOGGER.debug("Cache {} is disabled", configKeywordPrefix);
        }
        return enabled;
    }

    @SuppressWarnings("unchecked")
    private <T> T getConfig(final String keyword, final T defaultValue) {
        final String kw = configKeywordPrefix + "." + keyword;
        ConfigDatabase configDatabase = configDb.get();
        try {
            Optional<? extends Object> value = configDatabase.get(kw, defaultValue.getClass());
            if (value.isPresent()) {
                LOGGER.trace("Got cache config property {} = {}", kw, value.get());
                return (T) value.get();
            }
        } catch (ConfigException e) {
            return defaultValue;
        }
        return defaultValue;
    }

    private static final Cache<ObjectId, RevObject> NO_CACHE = new Cache<ObjectId, RevObject>() {

        @Override
        public RevObject getIfPresent(Object key) {
            return null;
        }

        @Override
        public RevObject get(ObjectId key, Callable<? extends RevObject> valueLoader)
                throws ExecutionException {
            try {
                return valueLoader.call();
            } catch (Exception e) {
                throw new ExecutionException(e);
            }
        }

        @Override
        public ImmutableMap<ObjectId, RevObject> getAllPresent(Iterable<?> keys) {
            return ImmutableMap.of();
        }

        @Override
        public void put(ObjectId key, RevObject value) {
            // do nothing
        }

        @Override
        public void putAll(Map<? extends ObjectId, ? extends RevObject> m) {
            // do nothing
        }

        @Override
        public void invalidate(Object key) {
            // do nothing
        }

        @Override
        public void invalidateAll(Iterable<?> keys) {
            // do nothing
        }

        @Override
        public void invalidateAll() {
            // do nothing
        }

        @Override
        public long size() {
            return 0;
        }

        @Override
        public CacheStats stats() {
            return new CacheStats(0, 0, 0, 0, 0, 0);
        }

        @Override
        public ConcurrentMap<ObjectId, RevObject> asMap() {
            return Maps.newConcurrentMap();
        }

        @Override
        public void cleanUp() {
            // do nothing
        }
    };
}
