/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.Optional;
import java.util.function.Predicate;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;

import com.google.common.base.Preconditions;

/**
 * Given an id, returns the ref that points to that id, if it exists
 * 
 */
public class ResolveBranchId extends AbstractGeoGigOp<Optional<Ref>> {

    private ObjectId id;

    public ResolveBranchId setObjectId(ObjectId id) {
        this.id = id;
        return this;
    }

    protected @Override Optional<Ref> _call() {
        Preconditions.checkState(id != null, "id has not been set.");
        Predicate<Ref> filter = ref -> {
            String refName = ref.getName();
            ObjectId refId = ref.getObjectId();
            return Ref.isChild(Ref.HEADS_PREFIX, refName) && refId.equals(this.id);
        };
        return command(ForEachRef.class).setFilter(filter).call().stream().findFirst();
    }
}