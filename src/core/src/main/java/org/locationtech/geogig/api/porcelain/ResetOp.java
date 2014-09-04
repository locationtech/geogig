/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.DiffTree;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterators;

/**
 * 
 * Reset current HEAD to the specified state.
 * 
 */
@CanRunDuringConflict
public class ResetOp extends AbstractGeoGigOp<Boolean> {

    /**
     * Enumeration of the possible reset modes.
     */
    public enum ResetMode {
        SOFT, MIXED, HARD, MERGE, KEEP, NONE
    };

    private Supplier<ObjectId> commit;

    private ResetMode mode = ResetMode.NONE;

    private Set<String> patterns = new HashSet<String>();

    /**
     * Sets the reset mode.
     * 
     * @param mode the reset mode
     * @return {@code this}
     */
    public ResetOp setMode(ResetMode mode) {
        this.mode = mode;
        return this;
    }

    /**
     * Sets the base commit.
     * 
     * @param commit a supplier for the {@link ObjectId id} of the commit
     * @return {@code this}
     */
    public ResetOp setCommit(final Supplier<ObjectId> commit) {
        this.commit = commit;
        return this;
    }

    /**
     * Adds a pattern.
     * 
     * @param pattern a regular expression to match what content to be reset
     * @return {@code this}
     */
    public ResetOp addPattern(final String pattern) {
        patterns.add(pattern);
        return this;
    }

    /**
     * Executes the reset operation.
     * 
     * @return always {@code true}
     */
    @Override
    protected Boolean _call() {
        Preconditions.checkState(!(patterns.size() > 0 && mode != ResetMode.NONE),
                "Ambiguous call, cannot specify paths and reset mode.");

        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        Preconditions.checkState(currHead.isPresent(), "Repository has no HEAD, can't reset.");
        Preconditions
                .checkState(currHead.get() instanceof SymRef, "Can't reset from detached HEAD");
        final SymRef headRef = (SymRef) currHead.get();

        final String currentBranch = headRef.getTarget();

        if (commit == null) {
            commit = Suppliers.ofInstance(currHead.get().getObjectId());
        }

        Preconditions.checkState(!ObjectId.NULL.equals(commit.get()),
                "Commit could not be resolved.");

        Repository repository = repository();
        RevCommit oldCommit = repository.getCommit(commit.get());

        if (patterns.size() > 0) {
            for (String pattern : patterns) {
                DiffTree diffOp = command(DiffTree.class)
                        .setOldTree(repository.index().getTree().getId())
                        .setNewTree(oldCommit.getTreeId()).setPathFilter(pattern);

                Iterator<DiffEntry> diff = diffOp.call();

                final long numChanges = Iterators.size(diffOp.call());
                if (numChanges == 0) {
                    // We are reseting to the current version, so there is nothing to do. However,
                    // if we are in a conflict state, the conflict should be removed and calling
                    // stage() will not do it, so we do it here
                    repository.stagingDatabase().removeConflict(null, pattern);
                } else {
                    repository.index().stage(subProgress((1.f / patterns.size()) * 100.f), diff,
                            numChanges);
                }
            }
        } else {
            if (mode == ResetMode.NONE) {
                mode = ResetMode.MIXED;
            }
            switch (mode) {
            case HARD:
                // Update the index and the working tree to the target tree
                index().updateStageHead(oldCommit.getTreeId());
                workingTree().updateWorkHead(oldCommit.getTreeId());
                break;
            case SOFT:
                // Do not update index or working tree to the target tree
                break;
            case MIXED:
                // Only update the index to the target tree
                index().updateStageHead(oldCommit.getTreeId());
                break;
            default:
                throw new UnsupportedOperationException("Unsupported reset mode.");
            }

            // Update branch head to the specified commit
            command(UpdateRef.class).setName(currentBranch).setNewValue(oldCommit.getId()).call();
            command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(currentBranch).call();

            Optional<Ref> ref = command(RefParse.class).setName(Ref.MERGE_HEAD).call();
            if (ref.isPresent()) {
                command(UpdateRef.class).setName(Ref.MERGE_HEAD).setDelete(true).call();
            }
        }
        return true;
    }
}
