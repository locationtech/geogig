/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing;

import static com.google.common.base.Preconditions.checkState;

import java.util.List;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.diff.DiffCountConsumer;
import org.locationtech.geogig.api.plumbing.diff.DiffObjectCount;
import org.locationtech.geogig.api.plumbing.diff.DiffTreeVisitor;
import org.locationtech.geogig.api.plumbing.diff.PathFilteringDiffConsumer;
import org.locationtech.geogig.storage.StagingDatabase;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

/**
 * Counts differences between two trees.
 * 
 * @see DiffCountConsumer
 */
public class DiffCount extends AbstractGeoGigOp<DiffObjectCount> {

    private final List<String> pathFilters = Lists.newLinkedList();

    private String oldRefSpec;

    private String newRefSpec;

    public DiffCount setOldVersion(@Nullable String refSpec) {
        this.oldRefSpec = refSpec;
        return this;
    }

    public DiffCount setNewVersion(@Nullable String refSpec) {
        this.newRefSpec = refSpec;
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
        checkState(oldRefSpec != null, "old ref spec not provided");
        checkState(newRefSpec != null, "new ref spec not provided");

        final RevTree oldTree = getTree(oldRefSpec);
        final RevTree newTree = getTree(newRefSpec);

        DiffObjectCount diffCount;
        StagingDatabase index = stagingDatabase();
        DiffTreeVisitor visitor = new DiffTreeVisitor(oldTree, newTree, index, index);

        DiffCountConsumer counter = new DiffCountConsumer(index);
        DiffTreeVisitor.Consumer filter = counter;
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
    private RevTree getTree(String refSpec) {

        final RevTree headTree;
        Optional<ObjectId> resolved = command(ResolveTreeish.class).setTreeish(refSpec).call();
        if (resolved.isPresent()) {
            ObjectId headTreeId = resolved.get();
            headTree = command(RevObjectParse.class).setObjectId(headTreeId).call(RevTree.class)
                    .get();
        } else {
            headTree = RevTree.EMPTY;
        }
        return headTree;
    }

}
