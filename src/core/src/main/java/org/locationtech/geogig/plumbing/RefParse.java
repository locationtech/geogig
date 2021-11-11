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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.RefDatabase;

/**
 * Resolve a ref name to the stored {@link Ref reference} object
 */
public class RefParse extends AbstractGeoGigOp<Optional<Ref>> {

    private static final Set<String> STANDARD_REFS = Set.of(Ref.HEAD, Ref.MASTER, Ref.ORIGIN,
            Ref.STAGE_HEAD, Ref.WORK_HEAD);

    private String refSpec;

    /**
     * @param name the name of the ref to parse
     * @return {@code this}
     */
    public RefParse setName(String name) {
        this.refSpec = name;
        return this;
    }

    /**
     * Parses a geogig reference string (possibly abbreviated) and return the resolved {@link Ref}
     * or {@code absent} if the ref spec didn't resolve to any actual reference.
     * 
     * Combinations of these operators are supported:
     * <ul>
     * <li><b>HEAD</b>, <b>MERGE_HEAD</b>, <b>FETCH_HEAD</b>, <b>STAGE_HEAD</b>, <b>WORK_HEAD</b>
     * </li>
     * <li><b>refs/...</b>: a complete reference name</li>
     * <li><b>short-name</b>: a short reference name under {@code refs/heads}, {@code refs/tags}, or
     * {@code refs/remotes} namespace, in that order of precedence</li>
     * </ul>
     * 
     * @return an {@code Optional} that contains a {@link Ref reference} or {@link Optional#empty()}
     *         if revstr can't be resolved to any {@link ObjectId}
     * @throws IllegalArgumentException if {@code refSpec} resolves to more than one ref on the same
     *         namespace
     */
    protected @Override Optional<Ref> _call() {
        Preconditions.checkState(refSpec != null, "name has not been set");

        if (STANDARD_REFS.contains(refSpec) || refSpec.startsWith("refs/")) {
            return getRef(refSpec);
        }

        // is it a top level ref?
        if (-1 == refSpec.indexOf('/')) {
            Optional<Ref> ref = getRef(refSpec);
            if (ref.isPresent()) {
                return ref;
            }
        }

        // may it be a single name like "master", resolution order is refs/heads, refs/tags, and
        // refs/remotes
        Optional<Ref> found = find(Ref.HEADS_PREFIX, refSpec);
        if (!found.isPresent()) {
            found = find(Ref.TAGS_PREFIX, refSpec);
        }
        if (!found.isPresent()) {
            found = find(Ref.REMOTES_PREFIX, refSpec);
        }
        return found;
    }

    private Optional<Ref> find(final String prefix, final String refSpec) {
        final String suffix = (refSpec.startsWith("/") ? "" : "/") + refSpec;

        List<Ref> matches = refDatabase().getAll(prefix).stream()
                .filter(r -> r.getName().endsWith(suffix)).collect(Collectors.toList());
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(refSpec + " resolves to more than one ref: "
                    + matches.stream().map(Ref::getName).collect(Collectors.joining(", ")));
        }
        return Optional.of(matches.get(0));
    }

    private Optional<Ref> getRef(final String name) {
        RefDatabase refDatabase = refDatabase();
        Optional<Ref> storedValue = refDatabase.get(name);
        if (storedValue.isPresent() && storedValue.get() instanceof SymRef) {
            Optional<Ref> target = getRef(((SymRef) storedValue.get()).getTarget());
            if (!target.isPresent()) {
                String message = String.format(
                        "Symref '%s' points to non existing target: %s. Context: %s", name,
                        storedValue.get(), context().getClass().getSimpleName());
                throw new RuntimeException(message);
            }
        }
        return storedValue;
    }

}
