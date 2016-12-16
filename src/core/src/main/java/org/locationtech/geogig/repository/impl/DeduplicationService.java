/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import java.util.ServiceLoader;

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
}
