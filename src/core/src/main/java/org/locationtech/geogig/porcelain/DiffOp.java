/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static com.google.common.base.Preconditions.checkArgument;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.DiffIndex;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.plumbing.DiffWorkTree;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.AutoCloseableIterator;

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
public class DiffOp extends AbstractGeoGigOp<AutoCloseableIterator<DiffEntry>>
        implements Iterable<DiffEntry> {

    private String oldRefSpec;

    private String newRefSpec;

    private String pathFilter;

    private boolean cached;

    private boolean reportTrees;

    private boolean preserveIterationOrder;

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
     * @param preserveIterationOrder if {@code true} the diff order will be consistent
     * @return {@code this}
     */
    public DiffOp setPreserveIterationOrder(boolean preserveIterationOrder) {
        this.preserveIterationOrder = preserveIterationOrder;
        return this;
    }

    /**
     * Executes the diff operation.
     * 
     * @return an iterator to a set of differences between the two trees
     * @see DiffEntry
     */
    @Override
    protected AutoCloseableIterator<DiffEntry> _call() {
        checkArgument(cached && oldRefSpec == null || !cached,
                String.format(
                        "compare index allows only one revision to check against, got %s / %s",
                        oldRefSpec, newRefSpec));
        checkArgument(newRefSpec == null || oldRefSpec != null,
                "If new rev spec is specified then old rev spec is mandatory");

        AutoCloseableIterator<DiffEntry> iterator;
        if (cached) {
            // compare the tree-ish (default to HEAD) and the index
            DiffIndex diffIndex = command(DiffIndex.class).addFilter(this.pathFilter)
                    .setReportTrees(reportTrees).setPreserveIterationOrder(preserveIterationOrder);
            if (oldRefSpec != null) {
                diffIndex.setOldVersion(oldRefSpec);
            }
            iterator = diffIndex.call();
        } else if (newRefSpec == null) {

            DiffWorkTree workTreeIndexDiff = command(DiffWorkTree.class).setFilter(pathFilter)
                    .setReportTrees(reportTrees).setPreserveIterationOrder(preserveIterationOrder);
            if (oldRefSpec != null) {
                workTreeIndexDiff.setOldVersion(oldRefSpec);
            }
            iterator = workTreeIndexDiff.call();
        } else {

            iterator = command(DiffTree.class).setOldVersion(oldRefSpec).setNewVersion(newRefSpec)
                    .setPathFilter(pathFilter).setReportTrees(reportTrees)
                    .setPreserveIterationOrder(preserveIterationOrder).call();
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
    public AutoCloseableIterator<DiffEntry> iterator() {
        return call();
    }

}
