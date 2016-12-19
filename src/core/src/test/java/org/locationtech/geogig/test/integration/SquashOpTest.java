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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.porcelain.SquashOp;
import org.opengis.feature.Feature;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class SquashOpTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testSquash() throws Exception {
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        List<RevCommit> commits = Lists.newArrayList();
        for (Feature f : features) {
            insertAndAdd(f);
            final RevCommit commit = geogig.command(CommitOp.class).call();
            commits.add(commit);
        }

        geogig.command(SquashOp.class).setSince(commits.get(1)).setUntil(commits.get(4)).call();
        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        ArrayList<RevCommit> logentries = Lists.newArrayList(log);
        assertEquals(3, logentries.size());
        RevCommit headCommit = logentries.get(0);
        RevCommit squashedCommit = logentries.get(1);
        RevCommit presquashCommit = logentries.get(2);
        assertEquals(commits.get(5).getTreeId(), headCommit.getTreeId());
        assertEquals(commits.get(1).getMessage(), squashedCommit.getMessage());
        assertEquals(commits.get(4).getAuthor().getTimestamp(),
                squashedCommit.getAuthor().getTimestamp());
        assertEquals(commits.get(0).getTreeId(), presquashCommit.getTreeId());
    }

    @Test
    public void testSquashWithMessage() throws Exception {
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        List<RevCommit> commits = Lists.newArrayList();
        for (Feature f : features) {
            insertAndAdd(f);
            final RevCommit commit = geogig.command(CommitOp.class).setMessage("Squashed").call();
            commits.add(commit);
        }

        geogig.command(SquashOp.class).setSince(commits.get(1)).setUntil(commits.get(4)).call();
        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        ArrayList<RevCommit> logentries = Lists.newArrayList(log);
        assertEquals(3, logentries.size());
        RevCommit headCommit = logentries.get(0);
        RevCommit squashedCommit = logentries.get(1);
        RevCommit presquashCommit = logentries.get(2);
        assertEquals(commits.get(5).getTreeId(), headCommit.getTreeId());
        assertEquals("Squashed", squashedCommit.getMessage());
        assertEquals(commits.get(4).getAuthor().getTimestamp(),
                squashedCommit.getAuthor().getTimestamp());
        assertEquals(commits.get(0).getTreeId(), presquashCommit.getTreeId());
    }

    @Test
    public void testSquashAtBranchTip() throws Exception {
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        List<RevCommit> commits = Lists.newArrayList();
        for (Feature f : features) {
            insertAndAdd(f);
            final RevCommit commit = geogig.command(CommitOp.class).call();
            commits.add(commit);
        }

        geogig.command(SquashOp.class).setSince(commits.get(1)).setUntil(commits.get(5)).call();
        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        ArrayList<RevCommit> logentries = Lists.newArrayList(log);
        assertEquals(2, logentries.size());
        RevCommit squashedCommit = logentries.get(0);
        RevCommit presquashCommit = logentries.get(1);
        assertEquals(commits.get(5).getTreeId(), squashedCommit.getTreeId());
        assertEquals(commits.get(1).getMessage(), squashedCommit.getMessage());
        assertEquals(commits.get(5).getAuthor().getTimestamp(),
                squashedCommit.getAuthor().getTimestamp());
        assertEquals(commits.get(0).getTreeId(), presquashCommit.getTreeId());
    }

    @Test
    public void testSquashAtHistoryOrigin() throws Exception {
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        List<RevCommit> commits = Lists.newArrayList();
        for (Feature f : features) {
            insertAndAdd(f);
            final RevCommit commit = geogig.command(CommitOp.class).call();
            commits.add(commit);
        }

        try {
            geogig.command(SquashOp.class).setSince(commits.get(0)).setUntil(commits.get(4)).call();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("no parents"));
        }
    }

    @Test
    public void testSquashwithSameSinceAndUntilCommit() throws Exception {
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        List<RevCommit> commits = Lists.newArrayList();
        for (Feature f : features) {
            insertAndAdd(f);
            final RevCommit commit = geogig.command(CommitOp.class)
                    .setMessage(f.getIdentifier().getID()).call();
            commits.add(commit);
        }
        geogig.command(SquashOp.class).setSince(commits.get(1)).setUntil(commits.get(1))
                .setMessage("squashed").call();
        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        ArrayList<RevCommit> logentries = Lists.newArrayList(log);
        assertEquals(6, logentries.size());
        RevCommit squashedCommit = logentries.get(4);
        assertEquals(commits.get(1).getTreeId(), squashedCommit.getTreeId());
        assertEquals("squashed", squashedCommit.getMessage());
    }

    @Test
    public void testSquash2() throws Exception {
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        List<RevCommit> commits = Lists.newArrayList();
        for (Feature f : features) {
            insertAndAdd(f);
            final RevCommit commit = geogig.command(CommitOp.class)
                    .setMessage(f.getIdentifier().getID()).call();
            commits.add(commit);
        }
        geogig.command(SquashOp.class).setSince(commits.get(1)).setUntil(commits.get(2))
                .setMessage("squashed").call();
        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        ArrayList<RevCommit> logentries = Lists.newArrayList(log);
        assertEquals(5, logentries.size());
        RevCommit squashedCommit = logentries.get(3);
        assertEquals(commits.get(2).getTreeId(), squashedCommit.getTreeId());
        assertEquals("squashed", squashedCommit.getMessage());
    }

    @Test
    public void testUncleanWorkingTree() throws Exception {
        insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        insert(points2);
        exception.expect(IllegalStateException.class);
        geogig.command(SquashOp.class).setSince(c1).setUntil(c1).call();
    }

    @Test
    public void testUncleanIndex() throws Exception {
        insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        insertAndAdd(points2);
        exception.expect(IllegalStateException.class);
        geogig.command(SquashOp.class).setSince(c1).setUntil(c1).call();
    }

    @Test
    public void testSquashWithMergedBranch() throws Exception {
        // Try to squash the commits marked (*) in this history
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added*
        // | |
        // o | - Lines 1 added*
        // |/
        // o - master - HEAD - Merge commit
        insertAndAdd(points1);
        @SuppressWarnings("unused")
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();
        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        geogig.command(MergeOp.class).addCommit(branch1.getObjectId())
                .setMessage("My merge message.").call();
        geogig.command(SquashOp.class).setSince(c3).setUntil(c4).setMessage("Squashed").call();
        // check that the commit added after the squashed has all the parents
        ArrayList<RevCommit> log = Lists
                .newArrayList(geogig.command(LogOp.class).setFirstParentOnly(true).call());
        assertEquals(3, log.size());
        ImmutableList<ObjectId> parents = log.get(0).getParentIds();
        assertEquals(2, parents.size());
        assertEquals("Squashed", log.get(1).getMessage());
        assertEquals(log.get(1).getId(), parents.get(0));
        assertEquals(c2.getId(), parents.get(1));

    }

    @Test
    public void testSquashInMergedBranch() throws Exception {
        // Try to squash the commits marked (*) in this history
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added*
        // | |
        // | o - Points 3 added*
        // | |
        // o | - Lines 1 added
        // |/
        // o - master - HEAD - Merge commit*
        insertAndAdd(points1);
        @SuppressWarnings("unused")
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();
        insertAndAdd(points3);
        @SuppressWarnings("unused")
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(lines1);
        @SuppressWarnings("unused")
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();
        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        MergeReport mergeReport = geogig.command(MergeOp.class).addCommit(branch1.getObjectId())
                .setMessage("My merge message.").call();
        try {
            geogig.command(SquashOp.class).setSince(c2).setUntil(mergeReport.getMergeCommit())
                    .setMessage("Squashed").call();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage()
                    .equals("Cannot reach 'since' from 'until' commit through first parentage"));
        }

    }

    @Test
    public void testSquashWithMergeCommit() throws Exception {
        // Try to squash the commits marked (*) in this history
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added*
        // | |
        // o | - Lines 1 added*
        // |/
        // o - master - HEAD - Merge commit*
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        @SuppressWarnings("unused")
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();
        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        MergeReport mergeReport = geogig.command(MergeOp.class).addCommit(branch1.getObjectId())
                .setMessage("My merge message.").call();
        geogig.command(SquashOp.class).setSince(c3).setUntil(mergeReport.getMergeCommit())
                .setMessage("Squashed").call();
        ArrayList<RevCommit> log = Lists
                .newArrayList(geogig.command(LogOp.class).setFirstParentOnly(true).call());
        assertEquals(2, log.size());
        ImmutableList<ObjectId> parents = log.get(0).getParentIds();
        assertEquals(c1.getId(), parents.get(0));
        assertEquals(c2.getId(), parents.get(1));

    }

    @Test
    public void testSquashIncludingBranchStartingPoint() throws Exception {
        // Try to squash the commits marked (*) in this history
        // o
        // |
        // o - Lines 2 added
        // |
        // o - Points 1 added*
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added*
        // | |
        // o | - Lines 1 added*
        // |/
        // o - master - HEAD - Merge commit*
        insertAndAdd(lines2);
        @SuppressWarnings("unused")
        final RevCommit c0 = geogig.command(CommitOp.class).setMessage("commit for " + idL2).call();
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        @SuppressWarnings("unused")
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        @SuppressWarnings("unused")
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        @SuppressWarnings("unused")
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();
        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        MergeReport mergeReport = geogig.command(MergeOp.class).addCommit(branch1.getObjectId())
                .setMessage("My merge message.").call();
        try {
            geogig.command(SquashOp.class).setSince(c1).setUntil(mergeReport.getMergeCommit())
                    .setMessage("Squashed").call();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equals(
                    "The commits to squash include a branch starting point. Squashing that type of commit is not supported."));
        }
    }

    @Test
    public void testSquashwithBranchCreatedInChildren() throws Exception {
        // Try to squash the commits marked (*) in this history
        // o
        // |
        // o - Points 1 added
        // |
        // o - Points 2 added*
        // |
        // o - Points 3 added*
        // |
        // o - Lines 1 added
        // |\
        // | o - Lines 2 added
        // |
        // o - Lines 3 added

        insertAndAdd(points1);
        @SuppressWarnings("unused")
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();
        insertAndAdd(points3);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        @SuppressWarnings("unused")
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();
        geogig.command(BranchCreateOp.class).setName("branch1").setAutoCheckout(true).call();
        insertAndAdd(lines2);
        @SuppressWarnings("unused")
        final RevCommit c5 = geogig.command(CommitOp.class).setMessage("commit for " + idL2).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(lines3);
        @SuppressWarnings("unused")
        final RevCommit c6 = geogig.command(CommitOp.class).setMessage("commit for " + idL3).call();

        try {
            geogig.command(SquashOp.class).setSince(c2).setUntil(c3).setMessage("Squashed").call();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equals(
                    "The commits after the ones to squash include a branch starting point. This scenario is not supported."));
        }
    }

    @Test
    public void testSquashwithBranchWithoutCommitsCreatedInChildren() throws Exception {
        insertAndAdd(points1);
        @SuppressWarnings("unused")
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();
        insertAndAdd(points3);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        @SuppressWarnings("unused")
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();
        insertAndAdd(lines2);
        @SuppressWarnings("unused")
        final RevCommit c5 = geogig.command(CommitOp.class).setMessage("commit for " + idL2).call();

        try {
            geogig.command(SquashOp.class).setSince(c2).setUntil(c3).setMessage("Squashed").call();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equals(
                    "The commits after the ones to squash include a branch starting point. This scenario is not supported."));
        }
    }

    @Override
    protected void setUpInternal() throws Exception {
    }

}
