/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.List;
import java.util.ServiceLoader;

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.impl.DeduplicationService;
import org.locationtech.geogig.storage.memory.HeapDeduplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * Uses the standard Java {@link ServiceLoader SPI mechanism} to look up for an implementation of
 * {@link DeduplicationService} in the classpath and returns it, or returns
 * {@link HeapDeduplicationService} if none is found.
 *
 */
public class CreateDeduplicator extends AbstractGeoGigOp<DeduplicationService> {

    private static final Logger LOG = LoggerFactory.getLogger(CreateDeduplicator.class);

    @Override
    protected DeduplicationService _call() {
        ServiceLoader<DeduplicationService> loader = ServiceLoader.load(DeduplicationService.class);
        List<DeduplicationService> services = Lists.newArrayList(loader.iterator());

        DeduplicationService service;
        if (services.isEmpty()) {
            LOG.info("No " + DeduplicationService.class.getSimpleName()
                    + " service found, using default heap based one");
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
        return service;
    }
}
