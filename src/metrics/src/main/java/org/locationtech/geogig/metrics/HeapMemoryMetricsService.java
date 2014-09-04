/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.TimeUnit;

import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.porcelain.ConfigException;
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.inject.Provider;

class HeapMemoryMetricsService extends AbstractScheduledService {

    private final long MB = 1024 * 1024;

    private static final MemoryMXBean MEMORY_MX_BEAN = ManagementFactory.getMemoryMXBean();

    private Provider<Platform> platform;

    private Provider<ConfigDatabase> configDb;

    // track memory usage reported in last run to avoid flooding the log file when there were no
    // changes
    private long lastHeapUsedMB;

    private long lastNonHeapUsedMB;

    public HeapMemoryMetricsService(final Provider<Platform> platform,
            final Provider<ConfigDatabase> configDb) {
        this.platform = platform;
        this.configDb = configDb;
    }

    @Override
    protected void runOneIteration() {
        try {
            Boolean enabled = configDb.get().get(MetricsModule.METRICS_ENABLED, Boolean.class)
                    .or(Boolean.FALSE);
            if (!enabled.booleanValue()) {
                return;
            }
        } catch (ConfigException e) {
            return;// not in a geogig repository
        }

        MemoryUsage heap = MEMORY_MX_BEAN.getHeapMemoryUsage();
        MemoryUsage nonHeap = MEMORY_MX_BEAN.getNonHeapMemoryUsage();

        long heapUsedMB = heap.getUsed() / MB;
        long nonHeapUsedMB = nonHeap.getUsed() / MB;

        // do not flood the log file if memory usage didn't change from last run
        if (heapUsedMB == lastHeapUsedMB && nonHeapUsedMB == lastNonHeapUsedMB) {
            return;
        }

        long timestamp = platform.get().currentTimeMillis();
        int objectPendingFinalizationCount = MEMORY_MX_BEAN.getObjectPendingFinalizationCount();

        MetricsModule.MEMORY_LOGGER.info("{},{},{},{}", timestamp, heapUsedMB, nonHeapUsedMB,
                objectPendingFinalizationCount);

        lastHeapUsedMB = heapUsedMB;
        lastNonHeapUsedMB = nonHeapUsedMB;

    }

    @Override
    protected Scheduler scheduler() {
        final long initialDelay = 5;
        final long period = 2;
        final TimeUnit unit = TimeUnit.SECONDS;
        return Scheduler.newFixedRateSchedule(initialDelay, period, unit);
    }

}
