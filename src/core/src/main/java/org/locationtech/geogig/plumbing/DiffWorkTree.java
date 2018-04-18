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

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

/**
 * Compares the features in the {@link WorkingTree working tree} and the {@link StagingArea index}
 * or a given root tree-ish.
 */
public class DiffWorkTree extends AbstractGeoGigOp<AutoCloseableIterator<DiffEntry>>
        implements Supplier<AutoCloseableIterator<DiffEntry>> {

    private String pathFilter;

    private String refSpec;

    private boolean reportTrees;

    private Long limit;

    private boolean preserveIterationOrder;

    /**
     * @param refSpec the name of the root tree object in the to compare the working tree against.
     *        If {@code null} or not specified, defaults to the current state of the index.
     * @return {@code this}
     */
    public DiffWorkTree setOldVersion(@Nullable String refSpec) {
        this.refSpec = refSpec;
        return this;
    }

    /**
     * @param path the path filter to use during the diff operation
     * @return {@code this}
     */
    public DiffWorkTree setFilter(@Nullable String path) {
        pathFilter = path;
        return this;
    }

    /**
     * @param preserveIterationOrder if {@code true} the diff order will be consistent
     * @return {@code this}
     */
    public DiffWorkTree setPreserveIterationOrder(boolean preserveIterationOrder) {
        this.preserveIterationOrder = preserveIterationOrder;
        return this;
    }

    /**
     * If no {@link #setOldVersion(String) old version} was set, returns the differences between the
     * working tree and the index, otherwise the differences between the working tree and the
     * specified revision.
     * 
     * @return an iterator to a set of differences between the two trees
     * @see DiffEntry
     */
    @Override
    protected AutoCloseableIterator<DiffEntry> _call() {

        final Optional<String> ref = Optional.fromNullable(refSpec);

        final RevTree oldTree = ref.isPresent() ? getOldTree() : stagingArea().getTree();
        final RevTree newTree = workingTree().getTree();

        DiffTree diff = command(DiffTree.class).setReportTrees(this.reportTrees)
                .setOldTree(oldTree.getId()).setNewTree(newTree.getId()).setMaxDiffs(limit)
                .setPreserveIterationOrder(preserveIterationOrder);
        if (this.pathFilter != null) {
            diff.setPathFilter(ImmutableList.of(pathFilter));
        }
        return diff.call();
    }

    /**
     * @return the tree referenced by the old ref, or the head of the index.
     */
    private RevTree getOldTree() {

        final String oldVersion = Optional.fromNullable(refSpec).or(Ref.STAGE_HEAD);

        Optional<ObjectId> headTreeId = command(ResolveTreeish.class).setTreeish(oldVersion).call();
        Preconditions.checkArgument(headTreeId.isPresent(),
                "Refspec " + oldVersion + " does not resolve to a tree");
        final RevTree headTree;
        headTree = command(RevObjectParse.class).setObjectId(headTreeId.get()).call(RevTree.class)
                .get();

        return headTree;
    }

    /**
     * @param reportTrees
     * @return
     */
    public DiffWorkTree setReportTrees(boolean reportTrees) {
        this.reportTrees = reportTrees;
        return this;
    }

    public DiffWorkTree setMaxDiffs(@Nullable Long limit) {
        Preconditions.checkArgument(limit == null || limit.longValue() >= 0L,
                "limit must be >= 0: ", limit);
        this.limit = limit;
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

}
