/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.FeatureTypes;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.plumbing.merge.CheckMergeScenarioOp;
import org.locationtech.geogig.plumbing.merge.MergeScenarioConsumer;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.plumbing.merge.ReportMergeScenarioOp;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.RemoveOp;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.FeatureInfo;

import com.google.common.collect.Lists;

public class ReportMergeConflictsOpTest extends RepositoryTestCase {

    protected @Override void setUpInternal() throws Exception {
    }

    private class TestMergeScenarioConsumer extends MergeScenarioConsumer {
        public List<Conflict> conflicted = new ArrayList<Conflict>();

        public List<DiffEntry> unconflicted = new ArrayList<DiffEntry>();

        public List<FeatureInfo> merged = new ArrayList<FeatureInfo>();

        public @Override void conflicted(Conflict conflict) {
            conflicted.add(conflict);
        }

        public @Override void unconflicted(DiffEntry diff) {
            unconflicted.add(diff);
        }

        public @Override void merged(FeatureInfo featureInfo) {
            merged.add(featureInfo);
        }

        public @Override void finished() {
        }
    };

    @Test
    public void testAddedSameFeature() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points2);
        RevCommit masterCommit = repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points2);
        RevCommit branchCommit = repo.command(CommitOp.class).call();
        MergeScenarioReport conflicts = repo.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        Boolean hasConflicts = repo.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertFalse(hasConflicts.booleanValue());
    }

    @Test
    public void testRemovedSameFeature() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        deleteAndAdd(points1);
        RevCommit masterCommit = repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        deleteAndAdd(points1);
        RevCommit branchCommit = repo.command(CommitOp.class).call();
        MergeScenarioReport conflicts = repo.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        Boolean hasConflicts = repo.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertFalse(hasConflicts.booleanValue());
    }

    @Test
    public void testModifiedSameFeatureCompatible() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", Integer.valueOf(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        RevCommit masterCommit = repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_1", Integer.valueOf(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        RevCommit branchCommit = repo.command(CommitOp.class).call();
        TestMergeScenarioConsumer consumer = new TestMergeScenarioConsumer();
        MergeScenarioReport conflicts = repo.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(consumer).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        assertEquals(1, conflicts.getMerged());
        Feature pointsMerged = feature(pointsType, idP1, "StringProp1_2", Integer.valueOf(2000),
                "POINT(1 1)");
        assertEquals(RevFeature.builder().build(pointsMerged), consumer.merged.get(0).getFeature());
        Boolean hasConflictsOrAutomerge = repo.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflictsOrAutomerge.booleanValue());

    }

    @Test
    public void testModifiedSameAttributeCompatible() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", Integer.valueOf(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        RevCommit masterCommit = repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_2", Integer.valueOf(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        RevCommit branchCommit = repo.command(CommitOp.class).call();
        MergeScenarioReport conflicts = repo.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(1, conflicts.getUnconflicted());
        Boolean hasConflictsOrAutomerge = repo.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflictsOrAutomerge.booleanValue());
    }

    @Test
    public void testModifiedSameFeatureIncompatible() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", Integer.valueOf(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        RevCommit masterCommit = repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", Integer.valueOf(1000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        RevCommit branchCommit = repo.command(CommitOp.class).call();
        MergeScenarioReport conflicts = repo.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(1, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        Boolean hasConflicts = repo.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflicts.booleanValue());
    }

    @Test
    public void testModifiedAndRemoved() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", Integer.valueOf(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        RevCommit masterCommit = repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        deleteAndAdd(points1);
        RevCommit branchCommit = repo.command(CommitOp.class).call();
        MergeScenarioReport conflicts = repo.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(1, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        Boolean hasConflicts = repo.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflicts.booleanValue());
    }

    @Test
    public void testRemovedTreeOnlyInOneBranch() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points2);
        RevCommit masterCommit = repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();

        repo.command(RemoveOp.class).addPathToRemove(pointsName).setRecursive(true).call();
        repo.command(AddOp.class).call();

        RevCommit branchCommit = repo.command(CommitOp.class).call();
        MergeScenarioReport conflicts = repo.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(1, conflicts.getConflicts());
        assertEquals(1, conflicts.getUnconflicted());
        Boolean hasConflicts = repo.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflicts.booleanValue());
    }

    @Test
    public void testAddedDifferentFeatures() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points2);
        RevCommit masterCommit = repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points3);
        RevCommit branchCommit = repo.command(CommitOp.class).call();
        MergeScenarioReport conflicts = repo.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(1, conflicts.getUnconflicted());
        Boolean hasConflicts = repo.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertFalse(hasConflicts.booleanValue());
    }

    @Test
    public void testAddedSameFeatureType() throws Exception {
        insertAndAdd(lines1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        insert(points2);
        delete(points2);
        repo.command(AddOp.class).call();
        RevCommit masterCommit = repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        insert(points2);
        delete(points2);
        repo.command(AddOp.class).call();
        RevCommit branchCommit = repo.command(CommitOp.class).call();
        MergeScenarioReport conflicts = repo.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        Boolean hasConflicts = repo.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertFalse(hasConflicts.booleanValue());
    }

    @Test
    public void testAddedDifferentFeatureType() throws Exception {
        insertAndAdd(lines1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        insert(points2);
        delete(points2);
        repo.command(AddOp.class).call();
        RevCommit masterCommit = repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        insert(points1B);
        delete(points1B);
        repo.command(AddOp.class).call();
        RevCommit branchCommit = repo.command(CommitOp.class).call();
        MergeScenarioReport conflicts = repo.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(1, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        Boolean hasConflicts = repo.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflicts.booleanValue());
    }

    @Test
    public void testModifiedDefaultFeatureTypeInBothBranches() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        repo.context().workingTree().updateTypeTree(pointsName, modifiedPointsType);
        insert(points1B);
        repo.command(AddOp.class).call();
        RevCommit masterCommit = repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        FeatureType modifiedPointsTypeB = FeatureTypes.createType(pointsNs + "#" + pointsName,
                "sp:String", "ip:Integer", "pp:Point:srid=4326", "extraB:String");
        repo.context().workingTree().updateTypeTree(pointsName, modifiedPointsTypeB);
        insert(points1B);
        repo.command(AddOp.class).call();
        RevCommit branchCommit = repo.command(CommitOp.class).call();
        MergeScenarioReport conflicts = repo.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(1, conflicts.getConflicts()); // the conflict in the feature type
        assertEquals(0, conflicts.getUnconflicted()); // the change in the feature is the
                                                      // same, so no conflict
        Boolean hasConflicts = repo.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflicts.booleanValue());
    }

    @Test
    public void testModifiedFeatureTypeInOneBranchEditedAttributeValueInTheOther()
            throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points1_modified);
        RevCommit masterCommit = repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        insert(points1B);
        insert(points2);
        repo.command(AddOp.class).call();
        RevCommit branchCommit = repo.command(CommitOp.class).call();

        MergeScenarioReport conflicts = repo.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(1, conflicts.getConflicts());
        assertEquals(1, conflicts.getUnconflicted());
        Boolean hasConflicts = repo.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflicts.booleanValue());
    }

    @Test
    public void testModifiedFeatureTypeInOneBranch() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points3);
        RevCommit masterCommit = repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        insert(points1B);
        insert(points2);
        repo.command(AddOp.class).call();
        RevCommit branchCommit = repo.command(CommitOp.class).call();

        MergeScenarioReport conflicts = repo.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(2, conflicts.getUnconflicted());
        Boolean hasConflicts = repo.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertFalse(hasConflicts.booleanValue());
    }

}
