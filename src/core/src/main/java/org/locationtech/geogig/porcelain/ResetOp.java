/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.CleanRefsOp;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import com.google.common.base.Preconditions;
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

    private ObjectId commit;

    private ResetMode mode = ResetMode.NONE;

    private Set<String> patterns = new HashSet<>();

    private boolean clean = true;

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
        this.commit = commit.get();
        return this;
    }

    public ResetOp setCommit(final ObjectId commit) {
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
     * Clean up extra refs and blobs generated by conflict-generating operations.
     * 
     * @param clean if specified, extra refs and blobs will be cleaned, defaults to {@code true}
     * @return {@code this}
     */
    public ResetOp setClean(final boolean clean) {
        this.clean = clean;
        return this;
    }

    /**
     * Executes the reset operation.
     * 
     * @return always {@code true}
     */
    protected @Override Boolean _call() {
        Preconditions.checkArgument(!(patterns.size() > 0 && mode != ResetMode.NONE),
                "Ambiguous call, cannot specify paths and reset mode.");

        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        Preconditions.checkArgument(currHead.isPresent(), "Repository has no HEAD, can't reset.");
        Preconditions.checkState(currHead.get() instanceof SymRef,
                "Can't reset from detached HEAD");
        final SymRef headRef = (SymRef) currHead.get();

        final String currentBranch = headRef.getTarget();

        if (commit == null) {
            commit = currHead.get().getObjectId();
        }

        Preconditions.checkArgument(!ObjectId.NULL.equals(commit), "Commit could not be resolved.");

        Repository repository = repository();
        RevCommit oldCommit = repository.context().objectDatabase().getCommit(commit);

        if (patterns.size() > 0) {
            for (String pattern : patterns) {
                DiffTree diffOp = command(DiffTree.class)
                        .setOldTree(repository.context().stagingArea().getTree().getId())
                        .setNewTree(oldCommit.getTreeId()).setPathFilter(pattern);

                try (AutoCloseableIterator<DiffEntry> diff = diffOp.call()) {
                    final long numChanges = Iterators.size(diffOp.call());
                    if (numChanges == 0) {
                        // We are reseting to the current version, so there is nothing to do.
                        // However, if we are in a conflict state, the conflict should be removed
                        // and calling stage() will not do it, so we do it here
                        conflictsDatabase().removeConflict(null, pattern);
                    } else {
                        repository.context().stagingArea().stage(
                                subProgress((1.f / patterns.size()) * 100.f), diff, numChanges);
                    }
                }
            }
        } else {
            UpdateRefs updateRefs = command(UpdateRefs.class);
            String modeReason;
            if (mode == ResetMode.NONE) {
                mode = ResetMode.MIXED;
                modeReason = "";
            }
            switch (mode) {
            case HARD:
                // Update the index and the working tree to the target tree
                updateRefs.add(new Ref(Ref.WORK_HEAD, oldCommit.getTreeId()));
                updateRefs.add(new Ref(Ref.STAGE_HEAD, oldCommit.getTreeId()));
                modeReason = " (hard)";
                break;
            case SOFT:
                // Do not update index or working tree to the target tree
                modeReason = " (soft)";
                break;
            case MIXED:
                // Only update the index to the target tree
                updateRefs.add(new Ref(Ref.STAGE_HEAD, oldCommit.getTreeId()));
                modeReason = " (mixed)";
                break;
            default:
                throw new UnsupportedOperationException("Unsupported reset mode.");
            }

            // Update branch head to the specified commit
            updateRefs.add(currentBranch, oldCommit.getId());
            updateRefs.add(new SymRef(Ref.HEAD, new Ref(currentBranch, oldCommit.getId())));
            String reason = String.format("reset%s: moving to %s", modeReason, oldCommit.getId());
            updateRefs.setReason(reason).call();
            conflictsDatabase().removeConflicts(null);
            if (clean) {
                command(CleanRefsOp.class).reason("Clean up on reset").call();
            }
        }
        return true;
    }
}
