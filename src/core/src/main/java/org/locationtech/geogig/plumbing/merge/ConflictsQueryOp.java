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

import java.util.Collections;
import java.util.Iterator;

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Conflict;

import com.google.common.base.Supplier;

/**
 * Internal command to return an iterator of conflicts in the operation's namespace (i.e.
 * transaction space)
 * 
 */
public class ConflictsQueryOp extends AbstractGeoGigOp<Iterator<Conflict>>
        implements Supplier<Iterator<Conflict>> {

    @Override
    protected Iterator<Conflict> _call() {
        if (repository().isOpen()) {
            return conflictsDatabase().getByPrefix(null, null);
        }
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<Conflict> get() {
        return call();
    }
}
