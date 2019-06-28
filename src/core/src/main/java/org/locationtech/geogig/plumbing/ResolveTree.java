/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.Optional;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Preconditions;

/**
 * Resolves the specified tree-ish reference to the {@link RevTree} it points to.
 * <p>
 * Returns {@link Optional#empty() empty} if no such object is found. Throws
 * {@code IllegalArgumentException} if the ref-spec resolves to an object from which a
 * {@link RevTree} cannot be derived.
 */
public class ResolveTree extends AbstractGeoGigOp<java.util.Optional<RevTree>> {

    private String treeIsh;

    private ObjectId id;

    private ObjectStore source = null;

    protected @Override java.util.Optional<RevTree> _call() {
        Preconditions.checkArgument(treeIsh != null || id != null, "tree-ish not provided");

        ResolveTreeish resolveTreeish = command(ResolveTreeish.class).setSource(source);
        if (this.id == null) {
            resolveTreeish.setTreeish(treeIsh);
        } else {
            resolveTreeish.setTreeish(id);
        }
        Optional<ObjectId> treeId = resolveTreeish.call();
        if (treeId.isPresent()) {
            if (RevTree.EMPTY_TREE_ID.equals(treeId.get())) {
                return Optional.of(RevTree.EMPTY);
            }
            return Optional.of(objectDatabase().getTree(treeId.get()));
        }
        return Optional.empty();
    }

    /**
     * @param source the object store to use
     * @return {@code this}
     */
    public ResolveTree setSource(ObjectStore source) {
        this.source = source;
        return this;
    }

    public ResolveTree setTreeIsh(String refSpec) {
        this.treeIsh = refSpec;
        return this;
    }

    /**
     * @param id an {@link ObjectId} that would ultimately resolve to a tree (i.e. the id of a tree
     *        itself, a commit, or a tag)
     */
    public ResolveTree setTreeIsh(ObjectId id) {
        this.id = id;
        return this;
    }
}
