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

import javax.management.MXBean;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.RevObject;

/**
 * Interface for querying and configuring the shared {@link RevObject} cache.
 * <p>
 * The shared cache is registered as an {@link MXBean} so it can be watched and configured through a
 * MBeans client such as JConsole.
 * <p>
 * For the sake of MBeans conformance, this interface contains only the methods suitable for RMI
 * invocation. For more information about how to configure and use the cache see the documentation
 * for {@link CacheManager}.
 * 
 * @see CacheManager
 * @since 1.1.1
 */
@MXBean
public interface CacheManagerBean {

    final String GEOGIG_CACHE_MAX_SIZE = "GEOGIG_CACHE_MAX_SIZE";

    /**
     * The default maximum cache capacity, used if neither the {@code GEOGIG_CACHE_MAX_SIZE}
     * environment variable or system property where provided, or couldn't be parsed; expressed as
     * 25% of the maximum JVM heap memory size.
     * 
     * @see #getDefaultSizeMB()
     */
    final double DEFAULT_CACHE_SIZE_PERCENT = 0.25;

    /**
     * Evicts all objects in the cache.
     * <p>
     * This is a non destructive operation, meaning the cache is cleared but it's statistics (hit
     * count, miss count, etc) are preserved.
     * <p>
     * Calling this method does not explicitly invoke the Java garbage collector, but merely clears
     * the cache leaving all its previously held entries eligible for immediate GC.
     */
    void clear();

    /**
     * @return the currently configured maximum size as a ratio between the maximum JVM heap size
     *         and the maximum cache size
     */
    double getMaximumSizePercent();

    /**
     * @param percent a value between 0.0 and 0.9 representing the maximum cache size as a ratio
     *        between the maximum JVM heap size and the maximum cache size
     * @throws IllegalArgumentException if the argument exceeds the absolute maximum cache size
     *         allowed of 90% of the JVM heap max size
     * @see #setMaximumSize(long)
     */
    void setMaximumSizePercent(double percent) throws IllegalArgumentException;

    /**
     * Sets the maximum cache capacity expressed in bytes.
     * <p>
     * Note this is a destructive method. When this method returns the internal cache is discarded
     * and replaced by a new on with the given capacity.
     * 
     * @param maxSizeBytes the new maximum cache capacity
     * @throws IllegalArgumentException if the argument exceeds the absolute maximum cache size
     *         allowed of 90% of the JVM heap max size
     */
    void setMaximumSize(long maxSizeBytes) throws IllegalArgumentException;

    /**
     * Convenience method to set the maximum cache capacity in MibiBytes
     * 
     * @param maxSizeMB the maximum cache size, expressed in MibiBytes
     * @throws IllegalArgumentException if the argument exceeds the absolute maximum cache size
     *         allowed of 90% of the JVM heap max size
     * @see #setMaximumSize(long)
     */
    void setMaximumSizeMB(double maxSizeMB) throws IllegalArgumentException;

    /**
     * @return the currently configured maximum size in MB
     */
    double getMaximumSizeMB();

    /**
     * The absolute maximum allowed cache size, which is 90% of the maximum heap size.
     * <p>
     * Trying to configure the cache maximum size to a value higher than this will result in an
     * {@link IllegalArgumentException}
     * 
     * @return the 90% of current maximum heap size expressed in MB
     */
    double getAbsoluteMaximumSizeMB();

    /**
     * Returns the resolved default cache size in MB as given by a Java system property, an
     * environment variable, or the {@link #DEFAULT_CACHE_SIZE_PERCENT internally defined maximum
     * size}, in that order of precedence.
     * <p>
     * The default maximum size can be given at startup by either the {@code GEOGIG_CACHE_MAX_SIZE}
     * argument either as a {@link #getMaximumSizeSystemProperty() System Property} or an
     * {@link #getMaximumSizeEnvVariable() environment variable}.
     * <p>
     * If both are given, the system property takes precedence over the environment variable.
     * <p>
     * If none is given, the default maximum cache size defaults to a
     * {@link #DEFAULT_CACHE_SIZE_PERCENT 25% of the JVM maximum heap size}.
     * 
     * @return the resolved default cache size in MB
     */
    double getDefaultSizeMB();

    /**
     * The default maximum cache size can be given at runtime through the
     * {@code GEOGIG_CACHE_MAX_SIZE} System property.
     * <p>
     * The allowed values for the system property follow the rules outlined on this class'
     * documentation.
     * <p>
     * For example, running Java with the {@code -DGEOGIG_CACHE_MAX_SIZE=2G} argument, sets the
     * cache size to 2 gigabytes, and {@code -DGEOGIG_CACHE_MAX_SIZE=0.75} to 75% of the JVM maximum
     * heap size as given by {@link Runtime#maxMemory()}.
     * 
     * @return the value of the {@code GEOGIG_CACHE_MAX_SIZE} Java System property
     */
    @Nullable
    String getMaximumSizeSystemProperty();

    /**
     * The default maximum cache size can be given at runtime through the
     * {@code GEOGIG_CACHE_MAX_SIZE} environment variable.
     * <p>
     * The value of the {@code GEOGIG_CACHE_MAX_SIZE} environment variable is obtained through
     * {@link System#getenv(String) System.getenv("GEOGIG_CACHE_MAX_SIZE")}, and is configured
     * differently depending on the running operating system.
     * <p>
     * The allowed values for the environment variable follow the rules outlined on this class'
     * documentation.
     * 
     * @return the value of the {@code GEOGIG_CACHE_MAX_SIZE} environment variable
     */
    @Nullable
    String getMaximumSizeEnvVariable();

    @Nullable
    String getCacheImplementationName();

    /**
     * @return the approximate size of the cache entries in bytes
     */
    long getSizeBytes();

    /**
     * Conenience method to return the approximate size of the cache entries in MibiBytes, for
     * reporting purposes
     */
    double getSizeMB();

    /**
     * @return number of {@link RevObject}s in the cache
     */
    long getSize();

    /**
     * @return number of cache queries that were hits
     */
    long getHitCount();

    /**
     * @return ratio between number of cache queries and those that were hits
     */
    double getHitRate();

    /**
     * @return number of cache queries that were misses
     */
    long getMissCount();

    /**
     * @return ratio between number of cache queries and those that were misses
     */
    double getMissRate();

    /**
     * @return number of times a cache entre was dropped due to size restrictions
     */
    long getEvictionCount();

}
