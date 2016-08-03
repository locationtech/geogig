/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.merge;

import java.net.URI;
import java.util.List;

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Conflict;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

/**
 * 
 * @deprecated at 1.0-RC3
 * @see ConflictsQueryOp
 */
@Deprecated
public class ConflictsReadOp extends AbstractGeoGigOp<List<Conflict>>
        implements Supplier<Iterable<Conflict>> {

    @Override
    protected List<Conflict> _call() {
        final Optional<URI> repoUrl = command(ResolveGeogigURI.class).call();
        if (repoUrl.isPresent()) {
            return conflictsDatabase().getConflicts(null, null);
        } else {
            return ImmutableList.of();
        }
    }

    @Override
    public Iterable<Conflict> get() {
        return call();
    }
}
