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

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;

/**
 * Update the object name stored in a {@link Ref} safely.
 * <p>
 * 
 */
public class ForEachRef extends AbstractGeoGigOp<ImmutableSet<Ref>> {

    private Predicate<Ref> filter;

    public ForEachRef setFilter(Predicate<Ref> filter) {
        this.filter = filter;
        return this;
    }

    public ForEachRef setPrefixFilter(final String prefix) {
        this.filter = new Predicate<Ref>() {
            @Override
            public boolean apply(Ref ref) {
                return ref != null && ref.getName().startsWith(prefix);
            }
        };
        return this;
    }

    /**
     * @return the new value of the ref
     */
    @Override
    protected ImmutableSet<Ref> _call() {

        @SuppressWarnings("unchecked")
        final Predicate<Ref> filter = (Predicate<Ref>) (this.filter == null
                ? Predicates.alwaysTrue() : this.filter);

        ImmutableSet.Builder<Ref> refs = new ImmutableSet.Builder<Ref>();
        for (String refName : refDatabase().getAll().keySet()) {
            Optional<Ref> ref = command(RefParse.class).setName(refName).call();
            if (ref.isPresent() && filter.apply(ref.get())) {
                Ref accepted = ref.get();
                refs.add(accepted);
            }
        }
        return refs.build();
    }
}
