/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.pack;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.repository.impl.RepositoryFilter;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

/**
 * Packages up a set of {@link RefRequest} for {@link SendPackOp} to ask a repository to send all
 * the missing contents to a target repo that are needed for the target to fully contain the
 * revision graph objects enclose the requested tips.
 *
 */
public class PackRequest {

    /**
     * Which refs to pack, their {@link RefDiff#getOldRef()} indicate which version the local copy
     * of the remote ref points to, {@code null} must be used in case there's no local copy yet.
     * <p>
     * a Map avoids duplicates based on the ref name only as opposed to name and objectid on a set
     */
    private Map<String, RefRequest> refs = new TreeMap<>();

    private @Nullable Integer maxDepth;

    private @Nullable RepositoryFilter sparseFilter;

    private boolean syncIndexes;

    /**
     * Adds a ref to pack, its {@link Ref#getObjectId() objectId} indicates which version the local
     * copy of the remote ref points to, {@link ObjectId#NULL} must be used in case there's no local
     * copy yet.
     * 
     * @return
     * 
     * @throws IllegalArgumentException if {@code ref} is a {@link SymRef}
     */
    public PackRequest addRef(RefRequest ref) {
        refs.put(ref.name, ref);
        return this;
    }

    public PackRequest maxDepth(int maxDepth) {
        checkArgument(maxDepth >= 0);
        this.maxDepth = maxDepth == 0 ? null : maxDepth;
        return this;
    }

    public Optional<Integer> getMaxDepth() {
        return Optional.fromNullable(maxDepth);
    }

    public PackRequest sparseFilter(@Nullable RepositoryFilter filter) {
        this.sparseFilter = filter;
        return this;
    }

    public Optional<RepositoryFilter> getSparseFilter() {
        return Optional.fromNullable(sparseFilter);
    }

    public PackRequest syncIndexes(boolean syncIndexes) {
        this.syncIndexes = syncIndexes;
        return this;
    }

    public boolean isSyncIndexes() {
        return syncIndexes;
    }

    /**
     * @return safe copy
     */
    public List<RefRequest> getRefs() {
        return Lists.newArrayList(refs.values());
    }

    public @Override boolean equals(Object o) {
        if (!(o instanceof PackRequest)) {
            return false;
        }
        PackRequest p = (PackRequest) o;
        return Objects.equals(maxDepth, p.maxDepth) //
                && Objects.equals(sparseFilter, p.sparseFilter)//
                && Objects.equals(refs, p.refs);
    }
}
