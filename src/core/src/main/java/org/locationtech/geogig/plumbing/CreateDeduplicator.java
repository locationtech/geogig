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

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.DeduplicationService;
import org.locationtech.geogig.repository.Deduplicator;

public class CreateDeduplicator extends AbstractGeoGigOp<Deduplicator> {

    @Override
    protected  Deduplicator _call() {
        DeduplicationService deduplicationService;
        deduplicationService = context.deduplicationService();
        return deduplicationService.createDeduplicator();
    }
}
