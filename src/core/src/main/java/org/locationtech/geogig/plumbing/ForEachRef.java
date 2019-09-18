/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;

import lombok.NonNull;

/**
 * Update the object name stored in a {@link Ref} safely.
 * <p>
 * 
 */
public class ForEachRef extends AbstractGeoGigOp<Set<Ref>> {

    private Predicate<Ref> filter;

    public ForEachRef setFilter(@NonNull Predicate<Ref> filter) {
        this.filter = filter;
        return this;
    }

    public ForEachRef setPrefixFilter(final @NonNull String prefix) {
        this.filter = ref -> Ref.isChild(prefix, ref.getName());
        return this;
    }

    /**
     * @return the new value of the ref
     */
    protected @Override Set<Ref> _call() {
        final Predicate<Ref> filter = this.filter == null ? r -> true : this.filter;

        Set<Ref> refs = refDatabase().getAll().stream().filter(filter).collect(Collectors.toSet());
        return refs;
    }
}
