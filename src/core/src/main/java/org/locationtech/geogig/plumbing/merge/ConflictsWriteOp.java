/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.merge;

import java.util.Objects;

import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;

import lombok.NonNull;

public class ConflictsWriteOp extends AbstractGeoGigOp<Void> {

    private Iterable<Conflict> conflicts;

    protected @Override Void _call() {
        Objects.requireNonNull(conflicts);
        conflictsDatabase().addConflicts(null, conflicts);
        return null;

    }

    public ConflictsWriteOp setConflicts(@NonNull Iterable<Conflict> conflicts) {
        this.conflicts = conflicts;
        return this;
    }

}
