/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.merge;

import java.net.URL;
import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

public class ConflictsReadOp extends AbstractGeoGigOp<List<Conflict>> implements
        Supplier<Iterable<Conflict>> {

    @Override
    protected  List<Conflict> _call() {
        final Optional<URL> repoUrl = command(ResolveGeogigDir.class).call();
        if (repoUrl.isPresent()) {
            return stagingDatabase().getConflicts(null, null);
        } else {
            return ImmutableList.of();
        }
    }

    @Override
    public Iterable<Conflict> get() {
        return call();
    }
}
