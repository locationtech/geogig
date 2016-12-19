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

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

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

            private ObjectId id = ResolveBranchId.this.id;

            @Override
            public boolean apply(@Nullable Ref ref) {
                String refName = ref.getName();
                ObjectId refId = ref.getObjectId();
                return refName.startsWith(Ref.HEADS_PREFIX) && refId.equals(this.id);
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