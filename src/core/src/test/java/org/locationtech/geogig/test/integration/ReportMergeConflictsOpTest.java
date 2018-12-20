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

import org.geotools.data.DataUtilities;
import org.junit.Test;
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
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.collect.Lists;

public class ReportMergeConflictsOpTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
    }

    private class TestMergeScenarioConsumer extends MergeScenarioConsumer {
        public List<Conflict> conflicted = new ArrayList<Conflict>();

        public List<DiffEntry> unconflicted = new ArrayList<DiffEntry>();

        public List<FeatureInfo> merged = new ArrayList<FeatureInfo>();

        @Override
        public void conflicted(Conflict conflict) {
            conflicted.add(conflict);
        }

        @Override
        public void unconflicted(DiffEntry diff) {
            unconflicted.add(diff);
        }

        @Override
        public void merged(FeatureInfo featureInfo) {
            merged.add(featureInfo);
        }

        @Override
        public void finished() {
        }
    };

    @Test
    public void testAddedSameFeature() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points2);
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points2);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        Boolean hasConflicts = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertFalse(hasConflicts.booleanValue());
    }

    @Test
    public void testRemovedSameFeature() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        deleteAndAdd(points1);
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        deleteAndAdd(points1);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        Boolean hasConflicts = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertFalse(hasConflicts.booleanValue());
    }

    @Test
    public void testModifiedSameFeatureCompatible() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_1", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        TestMergeScenarioConsumer consumer = new TestMergeScenarioConsumer();
        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(consumer).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        assertEquals(1, conflicts.getMerged());
        Feature pointsMerged = feature(pointsType, idP1, "StringProp1_2", new Integer(2000),
                "POINT(1 1)");
        assertEquals(RevFeature.builder().build(pointsMerged), consumer.merged.get(0).getFeature());
        Boolean hasConflictsOrAutomerge = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflictsOrAutomerge.booleanValue());

    }

    @Test
    public void testModifiedSameAttributeCompatible() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_2", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(1, conflicts.getUnconflicted());
        Boolean hasConflictsOrAutomerge = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflictsOrAutomerge.booleanValue());
    }

    @Test
    public void testModifiedSameFeatureIncompatible() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(1, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        Boolean hasConflicts = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflicts.booleanValue());
    }

    @Test
    public void testModifiedAndRemoved() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        deleteAndAdd(points1);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(1, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        Boolean hasConflicts = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflicts.booleanValue());
    }

    @Test
    public void testRemovedTreeOnlyInOneBranch() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points2);
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();

        geogig.command(RemoveOp.class).addPathToRemove(pointsName).setRecursive(true).call();
        geogig.command(AddOp.class).call();

        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(1, conflicts.getConflicts());
        assertEquals(1, conflicts.getUnconflicted());
        Boolean hasConflicts = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflicts.booleanValue());
    }

    @Test
    public void testAddedDifferentFeatures() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points2);
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points3);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(1, conflicts.getUnconflicted());
        Boolean hasConflicts = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertFalse(hasConflicts.booleanValue());
    }

    @Test
    public void testAddedSameFeatureType() throws Exception {
        insertAndAdd(lines1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insert(points2);
        delete(points2);
        geogig.command(AddOp.class).call();
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insert(points2);
        delete(points2);
        geogig.command(AddOp.class).call();
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        Boolean hasConflicts = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertFalse(hasConflicts.booleanValue());
    }

    @Test
    public void testAddedDifferentFeatureType() throws Exception {
        insertAndAdd(lines1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insert(points2);
        delete(points2);
        geogig.command(AddOp.class).call();
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insert(points1B);
        delete(points1B);
        geogig.command(AddOp.class).call();
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(1, conflicts.getConflicts());
        assertEquals(0, conflicts.getUnconflicted());
        Boolean hasConflicts = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflicts.booleanValue());
    }

    @Test
    public void testModifiedDefaultFeatureTypeInBothBranches() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        geogig.getRepository().workingTree().updateTypeTree(pointsName, modifiedPointsType);
        insert(points1B);
        geogig.command(AddOp.class).call();
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        String modifiedPointsTypeSpecB = "sp:String,ip:Integer,pp:Point:srid=4326,extraB:String";
        SimpleFeatureType modifiedPointsTypeB = DataUtilities.createType(pointsNs, pointsName,
                modifiedPointsTypeSpecB);
        geogig.getRepository().workingTree().updateTypeTree(pointsName, modifiedPointsTypeB);
        insert(points1B);
        geogig.command(AddOp.class).call();
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(1, conflicts.getConflicts()); // the conflict in the feature type
        assertEquals(0, conflicts.getUnconflicted()); // the change in the feature is the
                                                      // same, so no conflict
        Boolean hasConflicts = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflicts.booleanValue());
    }

    @Test
    public void testModifiedFeatureTypeInOneBranchEditedAttributeValueInTheOther()
            throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points1_modified);
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insert(points1B);
        insert(points2);
        geogig.command(AddOp.class).call();
        RevCommit branchCommit = geogig.command(CommitOp.class).call();

        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(1, conflicts.getConflicts());
        assertEquals(1, conflicts.getUnconflicted());
        Boolean hasConflicts = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflicts.booleanValue());
    }

    @Test
    public void testModifiedFeatureTypeInOneBranch() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points3);
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insert(points1B);
        insert(points2);
        geogig.command(AddOp.class).call();
        RevCommit branchCommit = geogig.command(CommitOp.class).call();

        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit)
                .setConsumer(new TestMergeScenarioConsumer()).call();
        assertEquals(0, conflicts.getConflicts());
        assertEquals(2, conflicts.getUnconflicted());
        Boolean hasConflicts = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertFalse(hasConflicts.booleanValue());
    }

}
