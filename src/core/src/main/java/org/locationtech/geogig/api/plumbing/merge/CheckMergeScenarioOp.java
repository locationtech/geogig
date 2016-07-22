/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.merge;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.plumbing.AutoCloseableIterator;
import org.locationtech.geogig.api.plumbing.DiffTree;
import org.locationtech.geogig.api.plumbing.FindCommonAncestor;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

/**
 * Checks for conflicts between changes introduced by different histories, or features that have to
 * be merged.
 * 
 * This operation analyzes a merge scenario and returns true if there are conflicts or some features
 * have to be merged. This last case happens when a feature has been edited by more than one branch,
 * and the changes introduced are not the same in all of them. This usually implies creating a
 * feature not already contained in the repo, but not necessarily.
 * 
 * This return value indicates an scenario where the merge operation has to be handled differently.
 * 
 * It returns false in case there are no such issues, and the branches to be merged are completely
 * independent in their edits.
 */
public class CheckMergeScenarioOp extends AbstractGeoGigOp<Boolean> {

    private List<RevCommit> commits;

    /**
     * @param commits the commits to check {@link RevCommit}
     */
    public CheckMergeScenarioOp setCommits(List<RevCommit> commits) {
        this.commits = commits;
        return this;
    }

    @Override
    protected Boolean _call() {
        if (commits.size() < 2) {
            return Boolean.FALSE;
        }
        Optional<ObjectId> ancestor = command(FindCommonAncestor.class).setLeft(commits.get(0))
                .setRight(commits.get(1)).call();
        Preconditions.checkState(ancestor.isPresent(), "No ancestor commit could be found.");
        for (int i = 2; i < commits.size(); i++) {
            ancestor = command(FindCommonAncestor.class).setLeft(commits.get(i))
                    .setRightId(ancestor.get()).call();
            Preconditions.checkState(ancestor.isPresent(), "No ancestor commit could be found.");
        }

        List<AutoCloseableIterator<DiffEntry>> commitDiffs = new ArrayList<AutoCloseableIterator<DiffEntry>>();

        // we organize the changes made for each path
        for (RevCommit commit : commits) {
            AutoCloseableIterator<DiffEntry> toMergeDiffs = command(DiffTree.class)
                    .setReportTrees(true)
                    .setOldTree(ancestor.get()).setNewTree(commit.getId())
                    .setPreserveIterationOrder(true).call();
            commitDiffs.add(toMergeDiffs);
        }

        PeekingIterator<DiffEntry> merged = Iterators
                .peekingIterator(Iterators.mergeSorted(commitDiffs, DiffEntry.COMPARATOR));

        while (merged.hasNext()) {
            List<DiffEntry> nextPath = nextPath(merged);
            if (hasConflicts(nextPath)) {
                for (AutoCloseableIterator<DiffEntry> iter : commitDiffs) {
                    iter.close();
                }
                return true;
            }
        }

        return false;

    }

    /**
     * Get all the next set of nodes from the iterator with the same path.
     * 
     * @param iterator
     * @return
     */
    private List<DiffEntry> nextPath(PeekingIterator<DiffEntry> iterator) {
        List<DiffEntry> entries = new ArrayList<DiffEntry>();
        DiffEntry next = iterator.next();
        entries.add(next);
        String name = next.oldName() != null ? next.oldName() : next.newName();
        while (iterator.hasNext()) {
            DiffEntry peeked = iterator.peek();
            String peekedName = peeked.oldName() != null ? peeked.oldName() : peeked.newName();
            if (name.equals(peekedName)) {
                entries.add(iterator.next());
            } else {
                break;
            }
        }
        return entries;
    }

    /**
     * Check all nodes with the same path for conflicts.
     * 
     * @param diffs
     * @return
     */
    private boolean hasConflicts(List<DiffEntry> diffs) {
        for (int i = 0; i < diffs.size(); i++) {
            for (int j = i + 1; j < diffs.size(); j++) {
                if (hasConflicts(diffs.get(i), diffs.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasConflicts(DiffEntry diff1, DiffEntry diff2) {
        if (!diff1.changeType().equals(diff2.changeType())) {
            return true;
        }

        boolean isConflict;

        switch (diff1.changeType()) {
        case ADDED: {
            TYPE type = diff1.getNewObject().getType();
            if (TYPE.TREE.equals(type)) {
                isConflict = !diff1.getNewObject().getMetadataId()
                        .equals(diff2.getNewObject().getMetadataId());
            } else {
                isConflict = !diff1.getNewObject().getObjectId()
                        .equals(diff2.getNewObject().getObjectId());
            }
        }
            break;
        case MODIFIED: {
            TYPE type = diff1.getNewObject().getType();
            if (TYPE.TREE.equals(type)) {
                isConflict = !diff1.getNewObject().getMetadataId()
                        .equals(diff2.getNewObject().getMetadataId());
            } else {
                isConflict = !diff1.newObjectId().equals(diff2.newObjectId());

            }
        }
            break;
        case REMOVED:
            isConflict = false;
            break;
        default:
            throw new IllegalStateException();
        }
        return isConflict;
    }
}
