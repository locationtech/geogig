/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static com.google.common.base.Preconditions.checkState;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.diff.DiffCountConsumer;
import org.locationtech.geogig.plumbing.diff.PathFilteringDiffConsumer;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

/**
 * Counts differences between two trees.
 * 
 * @see DiffCountConsumer
 */
public class DiffCount extends AbstractGeoGigOp<DiffObjectCount> {

    private final List<String> pathFilters = Lists.newLinkedList();

    /**
     * either one of oldRefSpec or oldTreeId must be set. Setting one nulls out the other.
     */
    private String oldRefSpec;

    /**
     * either one of oldRefSpec or oldTreeId must be set. Setting one nulls out the other.
     */
    private ObjectId oldTreeId;

    /**
     * either one of newRefSpec or newTreeId must be set. Setting one nulls out the other.
     */
    private String newRefSpec;

    /**
     * either one of newRefSpec or newTreeId must be set. Setting one nulls out the other.
     */
    private ObjectId newTreeId;

    public DiffCount setOldVersion(String refSpec) {
        this.oldRefSpec = refSpec;
        this.oldTreeId = null;
        return this;
    }

    public DiffCount setNewVersion(String refSpec) {
        this.newRefSpec = refSpec;
        this.newTreeId = null;
        return this;
    }

    public DiffCount setOldTree(ObjectId oldTreeId) {
        this.oldRefSpec = null;
        this.oldTreeId = oldTreeId;
        return this;
    }

    public DiffCount setNewTree(ObjectId newTreeId) {
        this.newRefSpec = null;
        this.newTreeId = newTreeId;
        return this;
    }

    /**
     * @param path the path filter to use during the diff operation
     * @return {@code this}
     */
    public DiffCount addFilter(@Nullable String path) {
        if (path != null) {
            pathFilters.add(path);
        }
        return this;
    }

    /**
     * @param paths list of paths to filter by, if {@code null} or empty, then no filtering is done,
     *        otherwise the list must not contain null elements.
     */
    public DiffCount setFilter(@Nullable List<String> paths) {
        pathFilters.clear();
        if (paths != null) {
            pathFilters.addAll(paths);
        }
        return this;
    }

    @Override
    protected DiffObjectCount _call() {
        checkState(oldRefSpec != null || oldTreeId != null, "old ref spec not provided");
        checkState(newRefSpec != null || newTreeId != null, "new ref spec not provided");

        final RevTree oldTree = getTree(oldRefSpec, oldTreeId);
        final RevTree newTree = getTree(newRefSpec, newTreeId);

        DiffObjectCount diffCount;
        ObjectDatabase index = objectDatabase();
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(oldTree, newTree, index, index);

        DiffCountConsumer counter = new DiffCountConsumer(index);
        PreOrderDiffWalk.Consumer filter = counter;
        if (!pathFilters.isEmpty()) {
            filter = new PathFilteringDiffConsumer(pathFilters, counter);
        }
        visitor.walk(filter);
        diffCount = counter.get();

        return diffCount;
    }

    /**
     * @return the tree referenced by the old ref, or the head of the index.
     */
    private RevTree getTree(@Nullable String refSpec, @Nullable ObjectId treeId) {
        checkState(refSpec == null || treeId == null);

        final RevTree headTree;
        Optional<ObjectId> resolved = refSpec == null ? Optional.of(treeId)
                : command(ResolveTreeish.class).setTreeish(refSpec).call();
        if (resolved.isPresent()) {
            ObjectId headTreeId = resolved.get();
            headTree = objectDatabase().getTree(headTreeId);
        } else {
            headTree = RevTree.EMPTY;
        }
        return headTree;
    }

}
