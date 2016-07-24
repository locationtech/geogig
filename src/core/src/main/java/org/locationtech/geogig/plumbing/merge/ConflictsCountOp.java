/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.merge;

import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.base.Supplier;

/**
 * Internal command to return an the number of conflicts in the operation's namespace (i.e.
 * transaction space)
 * 
 */
public class ConflictsCountOp extends AbstractGeoGigOp<Long> implements Supplier<Long> {

    @Override
    protected Long _call() {
        long count = 0L;
        if (repository().isOpen()) {
            count = conflictsDatabase().getCountByPrefix(null, null);
        }
        return Long.valueOf(count);
    }

    @Override
    public Long get() {
        return call();
    }
}
