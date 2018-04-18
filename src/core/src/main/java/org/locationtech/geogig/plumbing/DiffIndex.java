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

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

/**
 * Compares content and metadata links of blobs between the index and repository
 */
public class DiffIndex extends AbstractGeoGigOp<AutoCloseableIterator<DiffEntry>>
        implements Supplier<AutoCloseableIterator<DiffEntry>> {

    private String refSpec;

    private final List<String> pathFilters = Lists.newLinkedList();

    private boolean reportTrees;

    private Long limit;

    private boolean preserveIterationOrder;

    /**
     * @param pathFilter the path filter to use during the diff operation
     * @return {@code this}
     */
    public DiffIndex setFilter(@Nullable List<String> pathFilter) {
        this.pathFilters.clear();
        if (pathFilter != null) {
            this.pathFilters.addAll(pathFilter);
        }
        return this;
    }

    public DiffIndex addFilter(@Nullable String pathFilter) {
        if (pathFilter != null) {
            this.pathFilters.add(pathFilter);
        }
        return this;
    }

    /**
     * @param refSpec the name of the root tree object in the repository's object database to
     *        compare the index against. If {@code null} or not specified, defaults to the tree
     *        object of the current HEAD commit.
     * @return {@code this}
     */
    public DiffIndex setOldVersion(@Nullable String refSpec) {
        this.refSpec = refSpec;
        return this;
    }

    /**
     * @param preserveIterationOrder if {@code true} the diff order will be consistent
     * @return {@code this}
     */
    public DiffIndex setPreserveIterationOrder(boolean preserveIterationOrder) {
        this.preserveIterationOrder = preserveIterationOrder;
        return this;
    }

    /**
     * Finds differences between the tree pointed to by the given ref and the index.
     * 
     * @return an iterator to a set of differences between the two trees
     * @see DiffEntry
     */
    @Override
    protected AutoCloseableIterator<DiffEntry> _call() {
        final String oldVersion = Optional.fromNullable(refSpec).or(Ref.HEAD);
        final Optional<ObjectId> rootTreeId;
        rootTreeId = command(ResolveTreeish.class).setTreeish(oldVersion).call();
        Preconditions.checkArgument(rootTreeId.isPresent(), "refSpec %s did not resolve to a tree",
                oldVersion);

        final RevTree rootTree;

        rootTree = command(RevObjectParse.class).setObjectId(rootTreeId.get()).call(RevTree.class)
                .get();

        final RevTree newTree = stagingArea().getTree();

        DiffTree diff = command(DiffTree.class).setPathFilter(this.pathFilters)
                .setReportTrees(this.reportTrees).setOldTree(rootTree.getId())
                .setNewTree(newTree.getId()).setPreserveIterationOrder(preserveIterationOrder)
                .setMaxDiffs(limit);

        return diff.call();
    }

    /**
     * @param reportTrees
     * @return
     */
    public DiffIndex setReportTrees(boolean reportTrees) {
        this.reportTrees = reportTrees;
        return this;
    }

    /**
     * Implements {@link Supplier#get()} by deferring to {@link #call()}
     * 
     * @see #call()
     */
    @Override
    public AutoCloseableIterator<DiffEntry> get() {
        return call();
    }

    public DiffIndex setMaxDiffs(@Nullable Long limit) {
        Preconditions.checkArgument(limit == null || limit.longValue() >= 0, "limit must be >= 0: ",
                limit);
        this.limit = limit;
        return this;
    }
}
