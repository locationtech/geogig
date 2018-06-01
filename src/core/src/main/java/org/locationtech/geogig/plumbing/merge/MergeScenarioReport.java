/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.merge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevObject.TYPE;

/**
 * A class that counts the changes introduced by a branch to be merged, divided in categories
 * according to how they can (or can't) be merged into the destination branch
 */
public class MergeScenarioReport {

    public static class TreeReport {
        private final String path;

        private final AtomicLong conflicts = new AtomicLong(),
                unconflictedFeatures = new AtomicLong(), unconflictedTrees = new AtomicLong(),
                merged = new AtomicLong();

        TreeReport(String treePath) {
            this.path = treePath;
        }

        //@formatter:off
        public String getPath() {return path;}
        public long getConflicts() {return conflicts.get();}
        public long getMerges() {return merged.get();}
        public long getUnconflictedFeatures() {return unconflictedFeatures.get();}
        //@formatter:on
    }

    private final ConcurrentMap<String, TreeReport> reportsByTree = new ConcurrentHashMap<>();

    private TreeReport parentReport(String path) {
        String parentPath = NodeRef.parentPath(path);
        TreeReport treeReport = reportsByTree.computeIfAbsent(parentPath,
                treePath -> new TreeReport(treePath));
        return treeReport;
    }

    public void addConflict(String path) {
        TreeReport treeReport = parentReport(path);
        treeReport.conflicts.incrementAndGet();
    }

    public void addUnconflicted(DiffEntry diff) {
        TreeReport treeReport = parentReport(diff.path());
        TYPE type = (diff.isAdd() ? diff.getNewObject() : diff.getOldObject()).getType();
        if (TYPE.FEATURE == type) {
            treeReport.unconflictedFeatures.incrementAndGet();
        } else {
            treeReport.unconflictedTrees.incrementAndGet();
        }
    }

    public void addMerged(String path) {
        TreeReport treeReport = parentReport(path);
        treeReport.merged.incrementAndGet();
    }

    public List<TreeReport> getTreeReports() {
        return new ArrayList<>(this.reportsByTree.values());
    }

    /**
     * Returns the number of conflicts.
     * 
     * @return
     */
    public long getConflicts() {
        return sum(r -> r.conflicts.get());
    }

    private long sum(ToLongFunction<TreeReport> mapper) {
        return reportsByTree.values().stream().mapToLong(mapper).sum();
    }

    /**
     * @return the number of unconflicted features and trees.
     */
    public long getUnconflicted() {
        return sum(r -> r.unconflictedFeatures.get() + r.unconflictedTrees.get());
    }

    /**
     * @return the number of unconflicted features.
     */
    public long getUnconflictedFeatures() {
        return sum(r -> r.unconflictedFeatures.get());
    }

    /**
     * @return the number of unconflicted trees.
     */
    public long getUnconflictedTrees() {
        return sum(r -> r.unconflictedTrees.get());
    }

    /**
     * Returns the number of merged features.
     * 
     * @return
     */
    public long getMerged() {
        return sum(r -> r.merged.get());
    }

    @Override
    public String toString() {
        return String.format("Conflicts: %,d, merged: %,d, unconflicted: %,d", getConflicts(),
                getMerged(), getUnconflicted());
    }

}
