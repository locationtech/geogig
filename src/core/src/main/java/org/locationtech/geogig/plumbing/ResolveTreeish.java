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

import static org.locationtech.geogig.base.Preconditions.checkState;

import java.util.Optional;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ObjectStore;

import lombok.NonNull;

/**
 * Resolves the given "ref spec" to a tree id in the repository's object database.
 */
public class ResolveTreeish extends AbstractGeoGigOp<Optional<ObjectId>> {

    private String treeishRefSpec;

    private ObjectId treeish;

    private ObjectStore source = null;

    /**
     * @param treeishRefSpec a ref spec that ultimately resolves to a tree id
     * @return {@code this}
     */
    public ResolveTreeish setTreeish(@NonNull String treeishRefSpec) {
        this.treeishRefSpec = treeishRefSpec;
        this.treeish = null;
        return this;
    }

    /**
     * @param treeish an object id that ultimately resolves to a tree id (i.e. a commit id, a tree
     *        id)
     * @return {@code this}
     */
    public ResolveTreeish setTreeish(@NonNull ObjectId treeish) {
        this.treeish = treeish;
        this.treeishRefSpec = null;
        return this;
    }

    /**
     * @param source the object store to use
     * @return {@code this}
     */
    public ResolveTreeish setSource(ObjectStore source) {
        this.source = source;
        return this;
    }

    /**
     * Executes the command.
     * 
     * @return an {@link Optional} of the {@link ObjectId} that was resolved, or
     *         {@link Optional#empty()} if it did not resolve.
     */
    protected @Override Optional<ObjectId> _call() {
        checkState(treeishRefSpec != null || treeish != null, "tree-ish ref spec not set");

        ObjectStore source = this.source == null ? objectDatabase() : this.source;

        Optional<ObjectId> resolved;
        if (treeishRefSpec != null) {
            resolved = command(RevParse.class).setSource(source).setRefSpec(treeishRefSpec).call();
        } else {
            resolved = Optional.of(treeish);
        }

        return call(resolved);
    }

    /**
     * @param resolved an {@link Optional} with an ObjectId to resolve
     * @return an {@link Optional} of the {@link ObjectId} that was resolved, or
     *         {@link Optional#empty()} if it did not resolve.
     */
    private Optional<ObjectId> call(Optional<ObjectId> resolved) {
        if (!resolved.isPresent()) {
            return Optional.empty();
        }

        ObjectId objectId = resolved.get();
        if (objectId.isNull()) {// might be an empty commit ref
            return Optional.of(RevTree.EMPTY_TREE_ID);
        }

        ObjectStore source = this.source == null ? objectDatabase() : this.source;
        final RevObject object = source.get(objectId);
        final TYPE objectType = object.getType();

        switch (objectType) {
        case TREE:
            // ok
            break;
        case COMMIT: {
            RevCommit commit = source.getCommit(objectId);
            objectId = commit.getTreeId();
            break;
        }
        case TAG: {
            RevTag tag = source.getTag(objectId);
            objectId = source.getCommit(tag.getCommitId()).getTreeId();
            break;
        }
        default:
            throw new IllegalArgumentException(String.format(
                    "Provided ref spec ('%s') doesn't resolve to a tree-ish object: %s",
                    treeishRefSpec, String.valueOf(objectType)));
        }

        return Optional.of(objectId);
    }
}
