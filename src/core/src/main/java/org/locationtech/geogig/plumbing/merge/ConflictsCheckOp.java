/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.merge;

import java.net.URI;

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.base.Optional;

public class ConflictsCheckOp extends AbstractGeoGigOp<Boolean> {
    @Override
    protected Boolean _call() {
        final Optional<URI> repoUrl = command(ResolveGeogigURI.class).call();
        Boolean hasConflicts = Boolean.FALSE;

        if (repoUrl.isPresent()) {
            boolean conflicts = conflictsDatabase().hasConflicts(null);
            hasConflicts = Boolean.valueOf(conflicts);
        }
        return hasConflicts;
    }
}
