/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import org.junit.Test;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.api.plumbing.merge.ReportCommitConflictsOp;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.opengis.feature.Feature;

public class ReportCommitConflictsOpTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Test
    public void testAddedSameFeature() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points2);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points2);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        MergeScenarioReport conflicts = geogig.command(ReportCommitConflictsOp.class)
                .setCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(0, conflicts.getUnconflicted().size());
    }

    @Test
    public void testRemovedSameFeature() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        deleteAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        deleteAndAdd(points1);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        MergeScenarioReport conflicts = geogig.command(ReportCommitConflictsOp.class)
                .setCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(0, conflicts.getUnconflicted().size());
    }

    @Test
    public void testModifiedSameFeatureCompatible() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_1", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        MergeScenarioReport conflicts = geogig.command(ReportCommitConflictsOp.class)
                .setCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(1, conflicts.getUnconflicted().size());
    }

    @Test
    public void testModifiedAndNonExistant() throws Exception {
        insertAndAdd(points2);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        MergeScenarioReport conflicts = geogig.command(ReportCommitConflictsOp.class)
                .setCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(1, conflicts.getUnconflicted().size());
    }

    @Test
    public void testModifiedSameAttributeCompatible() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_2", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        MergeScenarioReport conflicts = geogig.command(ReportCommitConflictsOp.class)
                .setCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(1, conflicts.getUnconflicted().size());
    }

    @Test
    public void testModifiedSameFeatureIncompatible() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        MergeScenarioReport conflicts = geogig.command(ReportCommitConflictsOp.class)
                .setCommit(branchCommit).call();
        assertEquals(1, conflicts.getConflicts().size());
        assertEquals(0, conflicts.getUnconflicted().size());
    }

    @Test
    public void testModifiedAndRemoved() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        deleteAndAdd(points1);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        MergeScenarioReport conflicts = geogig.command(ReportCommitConflictsOp.class)
                .setCommit(branchCommit).call();
        assertEquals(1, conflicts.getConflicts().size());
        assertEquals(0, conflicts.getUnconflicted().size());
    }

    @Test
    public void testAddedDifferentFeatures() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points2);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points3);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        MergeScenarioReport conflicts = geogig.command(ReportCommitConflictsOp.class)
                .setCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(1, conflicts.getUnconflicted().size());
    }

    @Test
    public void testAddedSameFeatureType() throws Exception {
        insertAndAdd(lines1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insert(points2);
        delete(points2);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insert(points2);
        delete(points2);
        geogig.command(AddOp.class).call();
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        MergeScenarioReport conflicts = geogig.command(ReportCommitConflictsOp.class)
                .setCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(0, conflicts.getUnconflicted().size());
    }

    @Test
    public void testAddedDifferentFeatureType() throws Exception {
        insertAndAdd(lines1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insert(points1);
        delete(points1);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insert(points1B);
        delete(points1B);
        geogig.command(AddOp.class).call();
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        MergeScenarioReport conflicts = geogig.command(ReportCommitConflictsOp.class)
                .setCommit(branchCommit).call();
        assertEquals(1, conflicts.getConflicts().size());
        assertEquals(0, conflicts.getUnconflicted().size());
    }

}
