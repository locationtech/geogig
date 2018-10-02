/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.cache;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.ServiceFinder;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.impl.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import lombok.NonNull;

/**
 * Manages a pool of {@link ObjectCache} operating against a single internal shared cache.
 * <p>
 * <h3>Usage</h3> Repositories (more concretely, {@link ObjectStore} implementations), acquire an
 * instance of {@link ObjectCache} through the {@link #acquire(String)} method, using a string
 * argument that uniquely identify the resources they operate upon, and must return the
 * {@code ObjectCache} to this pool through the {@link #release(ObjectCache)} method when they don't
 * need it anymore (i.e. at {@link ObjectStore#close()}).
 * <p>
 * <h3>Configuration</h3> The initial maximum capacity for the shared cache defaults to 25% of the
 * JVM's maximum heap size.
 * <p>
 * This value can be overridden either by an environment variable or Java System property called
 * {@code GEOGIG_CACHE_MAX_SIZE}, with the System property taking precedence over the environment
 * variable.
 * <p>
 * The value of the {@code GEOGIG_CACHE_MAX_SIZE} can be expressed as a percentage of the JVM's
 * maximum heap size or an absolute size. In either case, it cannot exceed 90% of the JVM's maximum
 * heap size.
 * <p>
 * The {@code GEOGIG_CACHE_MAX_SIZE} argument value can be expressed in the following format:
 * <ul>
 * <li>{float} between 0 and 0.9: Size as percentage of available heap (e.g. {@code 0} for zero
 * cache size, {@code 0.5} for 50% of available heap, {@code .75}) for 75% of available heap.
 * <li>{integer}[B]: size in bytes (e.g. {@code 1024}, {@code 1024b}, {@code 1024B})
 * <li>{float}K[B]: size in kibibytes (e.g. {@code 1024K}, {@code 1024k})
 * <li>{float}M[B]: size in mibibytes (e.g. {@code 1.5m}, {@code 1024M}, {@code 1.5m})
 * <li>{float}G[B]: size in gibibytes (e.g. {@code 1.5G}, {@code 2g}, {@code 2.5G})
 * </ul>
 * <p>
 * At runtime, the maximum cache capacity can be changed as described by the mutator methods of
 * {@link CacheManagerBean}
 */
public class CacheManager implements CacheManagerBean {

    private static final Logger LOG = LoggerFactory.getLogger(CacheManager.class);

    public static final CacheManager INSTANCE = new CacheManager();

    /**
     * The name of the variable used as a system property or environment variable to provide the
     * fully qualified name of the {@link SharedCacheBuilder} at runtime.
     */
    public static final String ENV_VAR = "SHARED_CACHE_BUILDER";

    static {
        registerMBeanServer();
    }

    /**
     * A {@link ConnectionManager} that ensures a single instance of an {@link ObjectCache} exists
     * for any given unique cache identifier until all handles to it are
     * {@link #release(ObjectCache) released}
     */
    private final CacheConnections CACHES;

    /**
     * The instance of the shared cache being currently used. Not to be accessed directly but
     * through {@link #sharedCache()}
     */
    @VisibleForTesting
    volatile SharedCache _SHARED_CACHE;

    private final ConcurrentMap<String, CacheIdentifier> CACHE_IDS = new ConcurrentHashMap<>();

    private final AtomicInteger CACHE_ID_SEQ = new AtomicInteger();

    /**
     * Cached value of {@link #resolveDefaultMaxSize()}
     */
    private long resolvedMaxCacheSize = -1L;

    /**
     * Set by {@link #setMaximumSize(long)}
     */
    private long currentMaxCacheSize = -1;

    @VisibleForTesting
    CacheManager() {
        CACHES = new CacheConnections(this);
    }

    final SharedCache sharedCache() {
        if (_SHARED_CACHE == null) {
            synchronized (this) {
                if (_SHARED_CACHE == null) {
                    resolvedMaxCacheSize = resolveDefaultMaxSize();
                    setMaximumSize(resolvedMaxCacheSize);
                }
            }
        }
        return _SHARED_CACHE;
    }

    public void setEncoder(@NonNull RevObjectSerializer encoder) {
        sharedCache().setEncoder(encoder);
    }
    
    /**
     * Resolves the default maximum cache size in bytes.
     * <p>
     * If it's given by a valid value of the {@code GEOGIG_CACHE_MAX_SIZE} System property, then
     * that value is returned, otherwise if the {@code GEOGIG_CACHE_MAX_SIZE} environment variable
     * has a valid value, its parsed value is returned, otherwise defaults to the percent of the
     * maximum heap size defined by {@link CacheManagerBean#DEFAULT_CACHE_SIZE_PERCENT}
     * 
     * @return the default maximum cache size, taking into account the provided value as environmen
     *         variable or system property
     */
    long resolveDefaultMaxSize() {
        if (resolvedMaxCacheSize != -1) {
            return resolvedMaxCacheSize;
        }
        final String maximumSizeSystemPropertyArg = getMaximumSizeSystemProperty();
        final String maximumSizeEnvVariableArg = getMaximumSizeEnvVariable();

        long maxCacheSize = -1L;
        {
            long maximumSizeSystemProperty;
            try {
                maximumSizeSystemProperty = parseCacheSizeArgument(maximumSizeSystemPropertyArg);
                if (maximumSizeSystemProperty > -1L) {
                    LOG.info(String.format(
                            "Configuring GeoGig shared object cache maximum size to %,d bytes as given by the System property GEOGIG_CACHE_MAX_SIZE=%s",
                            maximumSizeSystemProperty, maximumSizeSystemPropertyArg));
                    maxCacheSize = maximumSizeSystemProperty;
                }
            } catch (IllegalArgumentException e) {
                if (isNullOrEmpty(maximumSizeEnvVariableArg)) {
                    LOG.warn("Unable to parse System property {}={}. Falling back to default value",
                            GEOGIG_CACHE_MAX_SIZE, maximumSizeSystemPropertyArg, e);
                } else {
                    LOG.warn(
                            "Unable to parse System property {}={}. Falling back to environment variable",
                            GEOGIG_CACHE_MAX_SIZE, maximumSizeSystemPropertyArg, e);
                }
            }
        }
        if (maxCacheSize == -1L) {
            long maximumSizeEnvVariable;
            try {
                maximumSizeEnvVariable = parseCacheSizeArgument(maximumSizeEnvVariableArg);
                if (maximumSizeEnvVariable > -1L) {
                    LOG.info(String.format(
                            "Configuring GeoGig shared object cache maximum size to %,d bytes as given by the environment variable GEOGIG_CACHE_MAX_SIZE=%s",
                            maximumSizeEnvVariable, maximumSizeEnvVariableArg));
                    maxCacheSize = maximumSizeEnvVariable;
                }
            } catch (IllegalArgumentException e) {
                LOG.warn("Unable to parse environment variable {}={}", GEOGIG_CACHE_MAX_SIZE,
                        maximumSizeEnvVariableArg, e);
            }
        }

        if (maxCacheSize != -1L) {
            long absoluteMaxCacheSize = getAbsoluteMaximumSize();
            if (absoluteMaxCacheSize < maxCacheSize) {
                LOG.warn("cache size is too big: {}, maximum allowed: {}", maxCacheSize,
                        absoluteMaxCacheSize);
                maxCacheSize = -1L;
            }
        }
        if (maxCacheSize == -1L) {
            final double percent = DEFAULT_CACHE_SIZE_PERCENT;
            maxCacheSize = getCacheSizePercent(percent);

            LOG.info(String.format(
                    "Configuring GeoGig shared object cache maximum size to the default of %,d bytes "
                            + "as given by the default %f percent of available heap. Use the GEOGIG_CACHE_MAX_SIZE System property or environment"
                            + "variable to set a different maximum size at runtime",
                    maxCacheSize, percent));
        }
        return maxCacheSize;
    }

    long getCacheSizePercent(double percent) {
        checkArgument(percent >= 0d && percent <= 0.9,
                "percent must be between zero and 90% (0.9)");

        final long maxMemory = getMaximumHeapSize();
        // Use up to 10% of the heap by default
        return (long) (maxMemory * percent);
    }

    long getMaximumHeapSize() {
        final long maxMemory = Runtime.getRuntime().maxMemory();
        return maxMemory;
    }

    /**
     * Format:
     * <ul>
     * <li>{float} between 0 and 0.9: Size as percentage of available heap (e.g. {@code 0} for zero
     * cache size, {@code 0.5} for 50% of available heap, {@code .75}) for 75% of available heap.
     * <li>{integer}[B]: size in bytes (e.g. {@code 1024}, {@code 1024b}, {@code 1024B})
     * <li>{float}K[B]: size in kibibytes (e.g. {@code 1024K}, {@code 1024k})
     * <li>{float}M[B]: size in mibibytes (e.g. {@code 1.5m}, {@code 1024M}, {@code 1.5m})
     * <li>{float}G[B]: size in gibibytes (e.g. {@code 1.5G}, {@code 2g}, {@code 2.5G})
     * </ul>
     * 
     * @return -1 if {@code sizeArg} is null, the parsed size in bytes otherwise
     * @throws IllegalArgumentException if {@code sizeArg} is non null and can't be parsed
     */
    long parseCacheSizeArgument(@Nullable String sizeArg) throws IllegalArgumentException {
        if (null == sizeArg) {
            return -1L;
        }
        sizeArg = sizeArg.toUpperCase();

        Pattern pattern = Pattern.compile("(\\A[+-]?[\\d.]+)([GMKB]?)(.*)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sizeArg);
        Map<String, Integer> powers = ImmutableMap.of(//
                "", 0, //
                "B", 0, //
                "K", 1, //
                "M", 2, //
                "G", 3);

        if (matcher.find()) {
            final String number = matcher.group(1);
            final String unit = matcher.group(2);
            final String after = matcher.group(3);// anything else
            if (!isNullOrEmpty(after)) {
                String msg = "Size format mismatch: too many arguments (" + sizeArg
                        + "), expected <float>[B|K|M|G]";
                throw new IllegalArgumentException(msg);
            }

            final double parsedNumber = Double.parseDouble(number);
            checkArgument(parsedNumber >= 0, "Value must be a positive number or zero");
            long sizeBytes;
            if (isNullOrEmpty(unit) && parsedNumber <= 1) {
                sizeBytes = getCacheSizePercent(parsedNumber);
            } else {
                final int pow = powers.get(unit);
                double bytes = parsedNumber * Math.pow(1024, pow);
                sizeBytes = (long) bytes;
            }
            return sizeBytes;
        }
        String msg = "Invalid format (" + sizeArg + "), expected <float>[B|K|M|G]";
        throw new IllegalArgumentException(msg);
    }

    /**
     * Returns a {@link RevObject} cache
     * <p>
     * Multiple invocations of this method for the same key are guaranteed to return the same
     * {@link ObjectCache} as long as there's still at least one handle to the same cache, or a new
     * one otherwise.
     * 
     * @param uniqueCacheIdentifier a client defined identifier for the objectcache
     */
    public ObjectCache acquire(final String uniqueCacheIdentifier) {
        checkNotNull(uniqueCacheIdentifier);

        CacheIdentifier prefix = CACHE_IDS.get(uniqueCacheIdentifier);
        if (prefix == null) {
            prefix = new CacheIdentifier(CACHE_ID_SEQ.incrementAndGet());
            CacheIdentifier existing = CACHE_IDS.putIfAbsent(uniqueCacheIdentifier, prefix);
            if (existing != null) {
                prefix = existing;
            }
        }

        return CACHES.acquire(prefix);
    }

    /**
     * Indicates the client code that obtained the {@link ObjectCache} through the {@link #acquire}
     * method does no longer need it and hence the cache manager is free to release the object cache
     * if there are no more clients for it.
     * 
     * @param cache the cache to return to the pool.
     */
    public void release(ObjectCache cache) {
        CACHES.release(cache);
    }

    void doRelease(ObjectCache cache) {
        cache.invalidateAll();
    }

    ObjectCache create(CacheIdentifier cacheId) {
        return new ObjectCache(() -> sharedCache(), cacheId);
    }

    private static class CacheConnections extends ConnectionManager<CacheIdentifier, ObjectCache> {

        private CacheManager cacheManager;

        public CacheConnections(CacheManager cacheManager) {
            this.cacheManager = cacheManager;
        }

        @Override
        protected ObjectCache connect(CacheIdentifier address) {
            return cacheManager.create(address);
        }

        @Override
        protected void disconnect(ObjectCache cache) {
            cacheManager.doRelease(cache);
        }
    }

    private static void registerMBeanServer() {
        MBeanServer mbeanserver = ManagementFactory.getPlatformMBeanServer();
        try {
            ObjectName beanName = new ObjectName("org.geogig:type=shared-cache");
            mbeanserver.registerMBean(INSTANCE, beanName);
            LOG.info("Registered GeoGig cache manager MBean as " + beanName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getSize() {
        return sharedCache().objectCount();
    }

    @Override
    public long getSizeBytes() {
        return sharedCache().sizeBytes();
    }

    @Override
    public double getSizeMB() {
        return getSizeBytes() / (1024D * 1024D);
    }

    @Override
    public void clear() {
        if (_SHARED_CACHE != null) {
            _SHARED_CACHE.invalidateAll();
        }
    }

    @Override
    public long getHitCount() {
        return sharedCache().getStats().hitCount();
    }

    @Override
    public double getHitRate() {
        return sharedCache().getStats().hitRate();
    }

    @Override
    public long getMissCount() {
        return sharedCache().getStats().missCount();
    }

    @Override
    public double getMissRate() {
        return sharedCache().getStats().missRate();
    }

    @Override
    public long getEvictionCount() {
        return sharedCache().getStats().evictionCount();
    }

    @Override
    public void setMaximumSizePercent(double percent) {
        long maxSize = getCacheSizePercent(percent);
        setMaximumSize(maxSize);
    }

    @Override
    public void setMaximumSizeMB(double maxSizeMB) {
        final long maxSize = (long) (maxSizeMB * (1024 * 1024));
        setMaximumSize(maxSize);
    }

    public long getMaximumSize() {
        return currentMaxCacheSize;
    }

    @Override
    public double getMaximumSizeMB() {
        long maxCacheSizeBytes = currentMaxCacheSize;
        return maxCacheSizeBytes / (1024d * 1024d);
    }

    @Override
    public void setMaximumSize(long maxSizeBytes) throws IllegalArgumentException {
        final long absoluteMaximumSize = getAbsoluteMaximumSize();
        checkArgument(maxSizeBytes >= 0 && maxSizeBytes <= absoluteMaximumSize,
                "Cache max size must be between 0 and %s, got %s", absoluteMaximumSize,
                maxSizeBytes);

        SharedCache cache;
        if (maxSizeBytes == 0) {
            cache = SharedCache.NO_CACHE;
        } else {
            SharedCacheBuilder builder;
            try {
                builder = new ServiceFinder().environmentVariable(ENV_VAR).systemProperty(ENV_VAR)
                        .lookupDefaultService(SharedCacheBuilder.class);
                LOG.info("Obtained cache builder {}", builder.getClass().getName());
                builder.setMaxSizeBytes(maxSizeBytes);
                cache = builder.build();
                LOG.info("Initialized shared cache {}", cache.getClass().getName());
            } catch (NoSuchElementException noBuilderPresent) {
                LOG.warn("No implementation of {} found in the classpath, "
                        + "nor one was provided through the {} system property or environment variable. Cache is disabled.",
                        SharedCacheBuilder.class.getName(), ENV_VAR);
                cache = SharedCache.NO_CACHE;
            }
        }

        SharedCache old = _SHARED_CACHE;
        _SHARED_CACHE = cache;
        if (old != null) {
            old.invalidateAll();
        }
        this.currentMaxCacheSize = maxSizeBytes;
    }

    @Override
    public double getMaximumSizePercent() {
        final long maxMemory = getMaximumHeapSize();
        double percent = (double) getMaximumSize() / maxMemory;
        return percent;
    }

    @Override
    public double getDefaultSizeMB() {
        return resolveDefaultMaxSize() / (1024d * 1024D);
    }

    @Override
    public double getAbsoluteMaximumSizeMB() {
        long maxMemory = getAbsoluteMaximumSize();
        return maxMemory / (double) (1024 * 1024);
    }

    long getAbsoluteMaximumSize() {
        double maxMemory = getMaximumHeapSize();
        return (long) (maxMemory * 0.9);
    }

    @Override
    @Nullable
    public String getMaximumSizeSystemProperty() {
        return System.getProperty(GEOGIG_CACHE_MAX_SIZE);
    }

    @Override
    @Nullable
    public String getMaximumSizeEnvVariable() {
        return System.getenv(GEOGIG_CACHE_MAX_SIZE);
    }

    @Override
    @Nullable
    public String getCacheImplementationName() {
        return sharedCache().getClass().getName();
    }
}
