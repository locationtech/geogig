/* Copyright (c) 2013-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.internal;

import java.util.List;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * A service for providing deduplicators.
 * <p>
 * Implementations are to be looked up through the standard Java {@link ServiceLoader SPI mechanism}
 * under {@code META-INF/services/org.locationtech.geogig.repository.DeduplicationService}
 */
public interface DeduplicationService {
    /**
     * Create a new Deduplicator. Clients MUST ensure that the deduplicator's release() method is
     * called. For example:
     *
     * <code>
     *   Deduplicator deduplicator = deduplicationService().createDeduplicator();
     *   try {
     *       client.use(deduplicator);
     *   } finally {
     *       deduplicator.release();
     *   }
     * </code>
     */
    Deduplicator createDeduplicator();

    public static Deduplicator create() {
        final Logger LOG = LoggerFactory.getLogger(DeduplicationService.class);
        ServiceLoader<DeduplicationService> loader = ServiceLoader.load(DeduplicationService.class,
                DeduplicationService.class.getClassLoader());
        List<DeduplicationService> services = Lists.newArrayList(loader.iterator());

        DeduplicationService service;
        if (services.isEmpty()) {
            service = new HeapDeduplicationService();
        } else {
            service = services.get(0);
            if (services.size() > 1) {
                LOG.info("More than one " + DeduplicationService.class.getSimpleName()
                        + " found. Using first one encountered: " + service.getClass().getName());
            } else {
                LOG.trace("Using deduplicator service: {}", service.getClass().getName());
            }
        }
        return service.createDeduplicator();
    }

}
