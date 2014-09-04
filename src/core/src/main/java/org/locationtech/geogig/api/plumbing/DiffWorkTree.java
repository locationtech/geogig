/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import java.util.Iterator;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

/**
 * Compares the features in the {@link WorkingTree working tree} and the {@link StagingArea index}
 * or a given root tree-ish.
 */
public class DiffWorkTree extends AbstractGeoGigOp<Iterator<DiffEntry>> implements
        Supplier<Iterator<DiffEntry>> {

    private String pathFilter;

    private String refSpec;

    private boolean reportTrees;

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
     * If no {@link #setOldVersion(String) old version} was set, returns the differences between the
     * working tree and the index, otherwise the differences between the working tree and the
     * specified revision.
     * 
     * @return an iterator to a set of differences between the two trees
     * @see DiffEntry
     */
    @Override
    protected Iterator<DiffEntry> _call() {

        final Optional<String> ref = Optional.fromNullable(refSpec);

        final RevTree oldTree = ref.isPresent() ? getOldTree() : index().getTree();
        final RevTree newTree = workingTree().getTree();

        DiffTree diff = command(DiffTree.class).setReportTrees(this.reportTrees)
                .setOldTree(oldTree.getId()).setNewTree(newTree.getId());
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
        Preconditions.checkArgument(headTreeId.isPresent(), "Refspec " + oldVersion
                + " does not resolve to a tree");
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

    /**
     * Implements {@link Supplier#get()} by deferring to {@link #call()}
     * 
     * @see #call()
     */
    @Override
    public Iterator<DiffEntry> get() {
        return call();
    }

}
