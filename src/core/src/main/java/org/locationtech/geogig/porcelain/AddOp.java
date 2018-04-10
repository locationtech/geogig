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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.ConflictsDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;

/**
 * Manipulates the index (staging area) by setting the unstaged changes that match this operation
 * criteria as staged.
 * 
 * @see WorkingTree
 * @see StagingArea
 */
@CanRunDuringConflict
public class AddOp extends AbstractGeoGigOp<WorkingTree> {

    private Set<String> patterns;

    private boolean updateOnly;

    /**
     * Constructs a new {@code AddOp} with the given parameters.
     */
    public AddOp() {
        patterns = new HashSet<String>();
    }

    /**
     * Executes the add command, staging unstaged changes that match the provided patterns.
     * 
     * @return the modified {@link WorkingTree working tree}.
     */
    @Override
    protected WorkingTree _call() {
        // this is add all, TODO: implement partial adds
        String path = null;
        if (patterns.size() == 1) {
            path = patterns.iterator().next();
        }
        stage(getProgressListener(), path);
        return workingTree();
    }

    /**
     * Stages the object addressed by {@code pathFilter}, or all unstaged objects if
     * {@code pathFilter == null} to be added, if it is/they are marked as an unstaged change. Does
     * nothing otherwise.
     * <p>
     * To stage changes not yet staged, a diff tree walk is performed using the current staged
     * {@link RevTree} as the old object and the current unstaged {@link RevTree} as the new object.
     * Then all the differences are traversed and the staged tree is updated with the changes
     * reported by the diff walk (neat).
     * </p>
     * 
     * @param progress the progress listener for this process
     * @param pathFilter the filter to use
     */
    public void stage(final ProgressListener progress, final @Nullable String pathFilter) {

        // short cut for the case where the index is empty and we're staging all changes in the
        // working tree, so it's just a matter of updating the index ref to working tree RevTree id
        final StagingArea index = stagingArea();
        try (AutoCloseableIterator<DiffEntry> staged = index.getStaged(null)) {
            if (null == pathFilter && !staged.hasNext() && !updateOnly
                    && index.countConflicted(null) == 0) {
                progress.started();
                Optional<ObjectId> workHead = command(RevParse.class).setRefSpec(Ref.WORK_HEAD)
                        .call();
                if (workHead.isPresent()) {
                    command(UpdateRef.class).setName(Ref.STAGE_HEAD).setNewValue(workHead.get())
                            .call();
                }
                progress.setProgress(100f);
                progress.complete();
                return;
            }
        }

        final long numChanges = workingTree().countUnstaged(pathFilter).count();

        try (AutoCloseableIterator<DiffEntry> sourceIterator = workingTree()
                .getUnstaged(pathFilter)) {
            Iterator<DiffEntry> updatedIterator = sourceIterator;
            if (updateOnly) {
                updatedIterator = Iterators.filter(updatedIterator, new Predicate<DiffEntry>() {
                    @Override
                    public boolean apply(@Nullable DiffEntry input) {
                        // HACK: avoid reporting changed trees
                        if (input.isChange() && input.getOldObject().getType().equals(TYPE.TREE)) {
                            return false;
                        }
                        return input.getOldObject() != null;
                    }
                });
            }

            index.stage(progress, updatedIterator, numChanges);

            // if we are staging unmerged files, the conflict should get solved. However, if the
            // working index object is the same as the staging area one (for instance, after running
            // checkout --ours), it will not be reported by the getUnstaged method. We solve that
            // here.
            ConflictsDatabase conflictsDatabase = conflictsDatabase();
            conflictsDatabase.removeByPrefix(null, pathFilter);
        }
    }

    /**
     * @param pattern a regular expression to match what content to be staged
     * @return {@code this}
     */
    public AddOp addPattern(final String pattern) {
        patterns.add(pattern);
        return this;
    }

    /**
     * @param updateOnly if {@code true}, only add already tracked features (either for modification
     *        or deletion), but do not stage any newly added one.
     * @return {@code this}
     */
    public AddOp setUpdateOnly(final boolean updateOnly) {
        this.updateOnly = updateOnly;
        return this;
    }

}
