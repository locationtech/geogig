/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.plumbing.merge;

import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;

public class ConflictsWriteOp extends AbstractGeoGigOp<Void> {

    private List<Conflict> conflicts;

    @Override
    protected  Void _call() {
        for (Conflict conflict : conflicts) {
            stagingDatabase().addConflict(null, conflict);
        }
        return null;

    }

    public ConflictsWriteOp setConflicts(List<Conflict> conflicts) {
        this.conflicts = conflicts;
        return this;
    }

}
