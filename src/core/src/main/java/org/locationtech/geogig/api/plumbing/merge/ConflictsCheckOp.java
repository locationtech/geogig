/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.plumbing.merge;

import java.net.URL;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;

import com.google.common.base.Optional;

public class ConflictsCheckOp extends AbstractGeoGigOp<Boolean> {
    @Override
    protected  Boolean _call() {
        final Optional<URL> repoUrl = command(ResolveGeogigDir.class).call();
        Boolean hasConflicts = Boolean.FALSE;

        if (repoUrl.isPresent()) {
            boolean conflicts = stagingDatabase().hasConflicts(null);
            hasConflicts = Boolean.valueOf(conflicts);
        }
        return hasConflicts;
    }
}
