/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Ben Carriel (Cornell University) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import java.util.Iterator;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.plumbing.DiffIndex;
import org.locationtech.geogig.api.plumbing.DiffWorkTree;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.api.plumbing.merge.ConflictsReadOp;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

@CanRunDuringConflict
public class StatusOp extends AbstractGeoGigOp<StatusOp.StatusSummary> {

    public static class StatusSummary {

        private static final Supplier<Iterator<DiffEntry>> empty;

        private static final Supplier<Iterable<Conflict>> no_conflicts;
        static {
            Iterator<DiffEntry> e = Iterators.<DiffEntry> emptyIterator();
            empty = Suppliers.ofInstance(e);
            Iterable<Conflict> c = ImmutableList.of();
            no_conflicts = Suppliers.ofInstance(c);
        }

        private Supplier<Iterable<Conflict>> conflicts = no_conflicts;

        private Supplier<Iterator<DiffEntry>> staged = empty;

        private Supplier<Iterator<DiffEntry>> unstaged = empty;

        private long countStaged, countUnstaged;

        private int countConflicted;

        public Supplier<Iterable<Conflict>> getConflicts() {
            return conflicts;
        }

        public Supplier<Iterator<DiffEntry>> getStaged() {
            return staged;
        }

        public Supplier<Iterator<DiffEntry>> getUnstaged() {
            return unstaged;
        }

        public long getCountStaged() {
            return countStaged;
        }

        public long getCountUnstaged() {
            return countUnstaged;
        }

        public int getCountConflicts() {
            return countConflicted;
        }
    }

    @Override
    protected StatusSummary _call() {
        WorkingTree workTree = workingTree();
        StagingArea index = index();

        StatusSummary summary = new StatusSummary();

        summary.countStaged = index.countStaged(null).count();
        summary.countUnstaged = workTree.countUnstaged(null).count();
        summary.countConflicted = index.countConflicted(null);

        if (summary.countStaged > 0) {
            summary.staged = command(DiffIndex.class).setReportTrees(true);
        }
        if (summary.countUnstaged > 0) {
            summary.unstaged = command(DiffWorkTree.class).setReportTrees(true);
        }
        if (summary.countConflicted > 0) {
            summary.conflicts = command(ConflictsReadOp.class);
        }
        return summary;
    }
}
