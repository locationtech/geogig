/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.porcelain;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Iterator;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.plumbing.DiffIndex;
import org.locationtech.geogig.api.plumbing.DiffTree;
import org.locationtech.geogig.api.plumbing.DiffWorkTree;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.di.CanRunDuringConflict;

/**
 * Perform a diff between trees pointed out by two commits
 * <p>
 * Usage:
 * <ul>
 * <li>
 * <code>{@link #setOldVersion(String) oldVersion} == null && {@link #setNewVersion(String) newVersion} == null</code>
 * : compare working tree and index
 * <li>
 * <code>{@link #setOldVersion(String) oldVersion} != null && {@link #setNewVersion(String) newVersion} == null</code>
 * : compare the working tree with the given commit
 * <li>
 * <code>{@link #setCompareIndex(boolean) compareIndex} == true && {@link #setOldVersion(String) oldVersion} == null && {@link #setNewVersion(String) newVersion} == null</code>
 * : compare the index with the HEAD commit
 * <li>
 * <code>{@link #setCompareIndex(boolean) compareIndex} == true && {@link #setOldVersion(String) oldVersion} != null && {@link #setNewVersion(String) newVersion} == null</code>
 * : compare the index with the given commit
 * <li>
 * <code>{@link #setOldVersion(String) oldVersion} != null && {@link #setNewVersion(String) newVersion} != null</code>
 * : compare {@code commit1} with {@code commit2}, where {@code commit1} is the eldest or left side
 * of the diff.
 * </ul>
 * 
 * @see DiffWorkTree
 * @see DiffIndex
 * @see DiffTree
 */
@CanRunDuringConflict
public class DiffOp extends AbstractGeoGigOp<Iterator<DiffEntry>> implements Iterable<DiffEntry> {

    private String oldRefSpec;

    private String newRefSpec;

    private String pathFilter;

    private boolean cached;

    private boolean reportTrees;

    /**
     * @param compareIndex if true, the index will be used in the comparison
     */
    public DiffOp setCompareIndex(boolean compareIndex) {
        this.cached = compareIndex;
        return this;
    }

    /**
     * @param revObjectSpec the old version to compare against
     * @return {@code this}
     */
    public DiffOp setOldVersion(@Nullable String revObjectSpec) {
        this.oldRefSpec = revObjectSpec;
        return this;
    }

    /**
     * @param treeishOid the old {@link ObjectId} to compare against
     * @return {@code this}
     */
    public DiffOp setOldVersion(ObjectId treeishOid) {
        return setOldVersion(treeishOid.toString());
    }

    /**
     * @param revObjectSpec the new version to compare against
     * @return {@code this}
     */
    public DiffOp setNewVersion(String revObjectSpec) {
        this.newRefSpec = revObjectSpec;
        return this;
    }

    /**
     * @param treeishOid the new {@link ObjectId} to compare against
     * @return {@code this}
     */
    public DiffOp setNewVersion(ObjectId treeishOid) {
        return setNewVersion(treeishOid.toString());
    }

    /**
     * @param pathFilter
     * @return {@code this}
     */
    public DiffOp setFilter(String pathFilter) {
        this.pathFilter = pathFilter;
        return this;
    }

    /**
     * Executes the diff operation.
     * 
     * @return an iterator to a set of differences between the two trees
     * @see DiffEntry
     */
    @Override
    protected  Iterator<DiffEntry> _call() {
        checkArgument(cached && oldRefSpec == null || !cached, String.format(
                "compare index allows only one revision to check against, got %s / %s", oldRefSpec,
                newRefSpec));
        checkArgument(newRefSpec == null || oldRefSpec != null,
                "If new rev spec is specified then old rev spec is mandatory");

        Iterator<DiffEntry> iterator;
        if (cached) {
            // compare the tree-ish (default to HEAD) and the index
            DiffIndex diffIndex = command(DiffIndex.class).addFilter(this.pathFilter)
                    .setReportTrees(reportTrees);
            if (oldRefSpec != null) {
                diffIndex.setOldVersion(oldRefSpec);
            }
            iterator = diffIndex.call();
        } else if (newRefSpec == null) {

            DiffWorkTree workTreeIndexDiff = command(DiffWorkTree.class).setFilter(pathFilter)
                    .setReportTrees(reportTrees);
            if (oldRefSpec != null) {
                workTreeIndexDiff.setOldVersion(oldRefSpec);
            }
            iterator = workTreeIndexDiff.call();
        } else {

            iterator = command(DiffTree.class).setOldVersion(oldRefSpec).setNewVersion(newRefSpec)
                    .setPathFilter(pathFilter).setReportTrees(reportTrees).call();
        }

        return iterator;
    }

    /**
     * @param b
     * @return
     */
    public DiffOp setReportTrees(boolean reportTrees) {
        this.reportTrees = reportTrees;
        return this;
    }

    @Override
    public Iterator<DiffEntry> iterator() {
        return call();
    }

}
