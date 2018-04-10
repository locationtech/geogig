/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Ben Carriel (Cornell University) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import java.util.Collections;
import java.util.Iterator;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.plumbing.DiffIndex;
import org.locationtech.geogig.plumbing.DiffWorkTree;
import org.locationtech.geogig.plumbing.merge.ConflictsQueryOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

@CanRunDuringConflict
public class StatusOp extends AbstractGeoGigOp<StatusOp.StatusSummary> {

    public static class StatusSummary {

        private static final Supplier<AutoCloseableIterator<DiffEntry>> empty;

        private static final Supplier<Iterator<Conflict>> no_conflicts;
        static {
            empty = Suppliers.ofInstance(AutoCloseableIterator.emptyIterator());
            no_conflicts = Suppliers.ofInstance(Collections.emptyIterator());
        }

        private Supplier<Iterator<Conflict>> conflicts = no_conflicts;

        private Supplier<AutoCloseableIterator<DiffEntry>> staged = empty;

        private Supplier<AutoCloseableIterator<DiffEntry>> unstaged = empty;

        private long countStaged, countUnstaged;

        private long countConflicted;

        public Supplier<Iterator<Conflict>> getConflicts() {
            return conflicts;
        }

        public Supplier<AutoCloseableIterator<DiffEntry>> getStaged() {
            return staged;
        }

        public Supplier<AutoCloseableIterator<DiffEntry>> getUnstaged() {
            return unstaged;
        }

        public long getCountStaged() {
            return countStaged;
        }

        public long getCountUnstaged() {
            return countUnstaged;
        }

        public long getCountConflicts() {
            return countConflicted;
        }
    }

    private Long limit;

    @Override
    protected StatusSummary _call() {
        WorkingTree workTree = workingTree();
        StagingArea index = stagingArea();

        StatusSummary summary = new StatusSummary();

        summary.countStaged = index.countStaged(null).count();
        summary.countUnstaged = workTree.countUnstaged(null).count();
        summary.countConflicted = index.countConflicted(null);

        final Long limit = this.limit == null ? null : this.limit;

        if (limit == null || limit.longValue() > 0) {
            if (summary.countStaged > 0) {
                summary.staged = command(DiffIndex.class).setMaxDiffs(limit).setReportTrees(true);
            }
            if (summary.countUnstaged > 0) {
                summary.unstaged = command(DiffWorkTree.class).setMaxDiffs(limit)
                        .setReportTrees(true);
            }
            if (summary.countConflicted > 0) {
                summary.conflicts = command(ConflictsQueryOp.class);
            }
        }
        return summary;
    }

    /**
     * @param limit {@code null} for no limit, an integer >= 0 to set a limit on the number of
     *        {@link DiffEntry} returned by {@link StatusSummary#getConflicts()},
     *        {@link StatusSummary#getStaged()}, and {@link StatusSummary#getUnstaged()}
     */
    public StatusOp setReportLimit(@Nullable Long limit) {
        Preconditions.checkArgument(limit == null || limit.intValue() >= 0, "limit must be >= 0: ",
                limit);
        this.limit = limit;
        return this;
    }
}
