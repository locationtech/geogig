/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;

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

    @Override
    protected Optional<Ref> _call() {
        Preconditions.checkState(id != null, "id has not been set.");
        Predicate<Ref> filter = new Predicate<Ref>() {
            @Override
            public boolean apply(@Nullable Ref ref) {
                return ref.getObjectId().equals(id);
            }
        };
        ImmutableSet<Ref> refs = command(ForEachRef.class).setFilter(filter).call();
        if (refs.isEmpty()) {
            return Optional.absent();
        } else {
            return Optional.of(refs.iterator().next());
        }
    }
}