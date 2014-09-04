/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import org.geotools.data.DataUtilities;
import org.junit.Test;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.plumbing.merge.CheckMergeScenarioOp;
import org.locationtech.geogig.api.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.api.plumbing.merge.ReportMergeScenarioOp;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.RemoveOp;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.collect.Lists;

public class ReportMergeConflictsOpTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
    }

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
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(0, conflicts.getUnconflicted().size());
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
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(0, conflicts.getUnconflicted().size());
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
        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(0, conflicts.getUnconflicted().size());
        assertEquals(1, conflicts.getMerged().size());
        Feature pointsMerged = feature(pointsType, idP1, "StringProp1_2", new Integer(2000),
                "POINT(1 1)");
        assertEquals(pointsMerged, conflicts.getMerged().get(0).getFeature());
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
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(1, conflicts.getUnconflicted().size());
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
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit).call();
        assertEquals(1, conflicts.getConflicts().size());
        assertEquals(0, conflicts.getUnconflicted().size());
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
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit).call();
        assertEquals(1, conflicts.getConflicts().size());
        assertEquals(0, conflicts.getUnconflicted().size());
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

        geogig.command(RemoveOp.class).addPathToRemove(pointsName).call();
        geogig.command(AddOp.class).call();

        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        MergeScenarioReport conflicts = geogig.command(ReportMergeScenarioOp.class)
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit).call();
        assertEquals(1, conflicts.getConflicts().size());
        assertEquals(1, conflicts.getUnconflicted().size());
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
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(1, conflicts.getUnconflicted().size());
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
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(0, conflicts.getUnconflicted().size());
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
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit).call();
        assertEquals(1, conflicts.getConflicts().size());
        assertEquals(0, conflicts.getUnconflicted().size());
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
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit).call();
        assertEquals(1, conflicts.getConflicts().size()); // the conflict in the feature type
        assertEquals(0, conflicts.getUnconflicted().size()); // the change in the feature is the
                                                             // same, so no conflict
        Boolean hasConflicts = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertTrue(hasConflicts.booleanValue());
    }

    @Test
    public void testModifiedFeatureTypeInOneBranchEditedAttributeValueInTheOther() throws Exception {
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
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit).call();
        assertEquals(1, conflicts.getConflicts().size());
        assertEquals(1, conflicts.getUnconflicted().size());
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
                .setMergeIntoCommit(masterCommit).setToMergeCommit(branchCommit).call();
        assertEquals(0, conflicts.getConflicts().size());
        assertEquals(2, conflicts.getUnconflicted().size());
        Boolean hasConflicts = geogig.command(CheckMergeScenarioOp.class)
                .setCommits(Lists.newArrayList(masterCommit, branchCommit)).call();
        assertFalse(hasConflicts.booleanValue());
    }

}
