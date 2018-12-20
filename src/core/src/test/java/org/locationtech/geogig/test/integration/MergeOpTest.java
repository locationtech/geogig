/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import static org.locationtech.geogig.model.NodeRef.appendChild;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.plumbing.merge.ConflictsQueryOp;
import org.locationtech.geogig.plumbing.merge.ReadMergeCommitMessageOp;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConflictsException;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.porcelain.NothingToCommitException;
import org.locationtech.geogig.porcelain.StatusOp;
import org.locationtech.geogig.porcelain.StatusOp.StatusSummary;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.ProgressListener;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public class MergeOpTest extends RepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    private static final String COMMITTER_NAME = "groldan";

    private static final String COMMITTER_EMAIL = "groldan@boundlessgeo.com";

    @Rule
    public TestName testName = new TestName();

    @Override
    protected void setUpInternal() throws Exception {
        // These values should be used during a commit to set author/committer
        // TODO: author/committer roles need to be defined better, but for
        // now they are the same thing.
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue(COMMITTER_NAME).call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue(COMMITTER_EMAIL).call();
    }

    @Test
    public void testMerge() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added
        // | |
        // o | - Lines 1 added
        // |/
        // o - master - HEAD - Merge commit

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        MergeReport mergeReport = geogig.command(MergeOp.class).addCommit(branch1.getObjectId())
                .setMessage("My merge message.").call();

        RevTree mergedTree = repo.getTree(mergeReport.getMergeCommit().getTreeId());

        String path = appendChild(pointsName, points2.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        path = appendChild(pointsName, points1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        path = appendChild(pointsName, points3.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        path = appendChild(linesName, lines1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();

        // Merge Commit
        RevCommit logCmerge = log.next();
        assertEquals("My merge message.", logCmerge.getMessage());
        assertEquals(2, logCmerge.getParentIds().size());
        assertEquals(c4.getId(), logCmerge.getParentIds().get(0));
        assertEquals(c2.getId(), logCmerge.getParentIds().get(1));

        // Commit 4
        RevCommit logC4 = log.next();
        assertEquals(c4.getAuthor(), logC4.getAuthor());
        assertEquals(c4.getCommitter(), logC4.getCommitter());
        assertEquals(c4.getMessage(), logC4.getMessage());
        assertEquals(c4.getTreeId(), logC4.getTreeId());

        // Commit 3
        RevCommit logC3 = log.next();
        assertEquals(c3.getAuthor(), logC3.getAuthor());
        assertEquals(c3.getCommitter(), logC3.getCommitter());
        assertEquals(c3.getMessage(), logC3.getMessage());
        assertEquals(c3.getTreeId(), logC3.getTreeId());

        // Commit 2
        RevCommit logC2 = log.next();
        assertEquals(c2.getAuthor(), logC2.getAuthor());
        assertEquals(c2.getCommitter(), logC2.getCommitter());
        assertEquals(c2.getMessage(), logC2.getMessage());
        assertEquals(c2.getTreeId(), logC2.getTreeId());

        // Commit 1
        RevCommit logC1 = log.next();
        assertEquals(c1.getAuthor(), logC1.getAuthor());
        assertEquals(c1.getCommitter(), logC1.getCommitter());
        assertEquals(c1.getMessage(), logC1.getMessage());
        assertEquals(c1.getTreeId(), logC1.getTreeId());

    }

    private void verifyCancelledCleanly(RevCommit oldHeadCommit) {
        StatusSummary summary = geogig.command(StatusOp.class).call();
        assertEquals(0, summary.getCountConflicts());
        assertEquals(0, summary.getCountStaged());
        assertEquals(0, summary.getCountUnstaged());

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        assertTrue(log.hasNext());
        assertEquals(oldHeadCommit, log.next());
    }

    @Test
    public void testCancelScenario1() throws Exception {
        // Test cancel before merge work begins
        ProgressListener listener = mock(ProgressListener.class);
        when(listener.isCanceled()).thenReturn(true);

        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        RevCommit master = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        MergeReport mergeReport = geogig.command(MergeOp.class).addCommit(branch1.getObjectId())
                .setMessage("My merge message.").setProgressListener(listener).call();

        assertNull(mergeReport);
        verify(listener, times(1)).isCanceled();

        verifyCancelledCleanly(master);
    }

    @Test
    public void testCancelScenario2() throws Exception {
        // Test cancel while merging with conflicts
        ProgressListener listener = mock(ProgressListener.class);
        when(listener.isCanceled()).thenReturn(false, true);

        insertAndAdd(points2);
        geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points1_modified);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1 + " modified").call();
        insertAndAdd(lines1);
        RevCommit master = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        MergeReport mergeReport = geogig.command(MergeOp.class).addCommit(branch1.getObjectId())
                .setMessage("My merge message.").setProgressListener(listener).call();

        assertNull(mergeReport);
        verify(listener, times(2)).isCanceled();

        verifyCancelledCleanly(master);
    }

    @Test
    public void testCancelScenario3() throws Exception {
        // Test cancel during non-conflicting merge
        ProgressListener listener = mock(ProgressListener.class);
        when(listener.isCanceled()).thenReturn(false, true);

        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        RevCommit master = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        MergeReport mergeReport = geogig.command(MergeOp.class).addCommit(branch1.getObjectId())
                .setMessage("My merge message.").setProgressListener(listener).call();

        assertNull(mergeReport);
        verify(listener, times(2)).isCanceled();

        verifyCancelledCleanly(master);
    }

    @Test
    public void testCancelScenario4() throws Exception {
        // Test cancel during octopus merge
        ProgressListener listener = mock(ProgressListener.class);
        when(listener.isCanceled()).thenReturn(false, false, true);

        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master, then create branch2 and checkout
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch2").call();
        insertAndAdd(lines1);
        geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(lines2);
        final RevCommit master = geogig.command(CommitOp.class).setMessage("commit for " + idL2)
                .call();

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        Ref branch2 = geogig.command(RefParse.class).setName("branch2").call().get();
        final MergeReport mergeReport = geogig.command(MergeOp.class)
                .addCommit(branch1.getObjectId()).addCommit(branch2.getObjectId())
                .setMessage("My merge message.").setProgressListener(listener).call();

        assertNull(mergeReport);
        verify(listener, times(3)).isCanceled();

        verifyCancelledCleanly(master);
    }

    @Test
    public void testSpecifyAuthor() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added
        // | |
        // o | - Lines 1 added
        // |/
        // o - master - HEAD - Merge commit

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        geogig.command(MergeOp.class).setAuthor("Merge Author", "merge@author.com")
                .addCommit(branch1.getObjectId()).setMessage("My merge message.").call();

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();

        // Merge Commit
        RevCommit logCmerge = log.next();
        assertEquals("My merge message.", logCmerge.getMessage());
        assertEquals("Merge Author", logCmerge.getAuthor().getName().get());
        assertEquals("merge@author.com", logCmerge.getAuthor().getEmail().get());
        assertEquals(2, logCmerge.getParentIds().size());
        assertEquals(c4.getId(), logCmerge.getParentIds().get(0));
        assertEquals(c2.getId(), logCmerge.getParentIds().get(1));
    }

    @Test
    public void testMergeMultiple() throws Exception {
        // Create the following revision graph
        // . o
        // . |
        // . o - Points 1 added
        // . |\
        // . | o - branch1 - Points 2 added
        // . |
        // . o - Points 3 added
        // ./|
        // o | - branch 2 - Lines 1 added
        // . |
        // . o - master - HEAD - Lines 2 added
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master, then create branch2 and checkout
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch2").call();
        insertAndAdd(lines1);
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(lines2);
        final RevCommit c5 = geogig.command(CommitOp.class).setMessage("commit for " + idL2).call();

        // Merge branch1 and branch2 into master to create the following revision graph
        // . o
        // . |
        // . o - Points 1 added
        // . |\
        // . | o - branch1 - Points 2 added
        // . | |
        // . o | - Points 3 added
        // ./| |
        // o | | - branch 2 - Lines 1 added
        // | | |
        // | o | - Lines 2 added
        // .\|/
        // . o - master - HEAD - Merge commit

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        Ref branch2 = geogig.command(RefParse.class).setName("branch2").call().get();
        final MergeReport mergeReport = geogig.command(MergeOp.class)
                .addCommit(branch1.getObjectId()).addCommit(branch2.getObjectId())
                .setMessage("My merge message.").call();

        RevTree mergedTree = repo.getTree(mergeReport.getMergeCommit().getTreeId());

        String path = appendChild(pointsName, points1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        path = appendChild(pointsName, points2.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        path = appendChild(pointsName, points3.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        path = appendChild(linesName, lines1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        path = appendChild(linesName, lines2.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        Iterator<RevCommit> log = geogig.command(LogOp.class).setFirstParentOnly(true).call();

        // Commit 4
        RevCommit logC4 = log.next();
        assertEquals("My merge message.", logC4.getMessage());
        assertEquals(3, logC4.getParentIds().size());
        assertEquals(c5.getId(), logC4.getParentIds().get(0));
        assertEquals(c2.getId(), logC4.getParentIds().get(1));
        assertEquals(c4.getId(), logC4.getParentIds().get(2));

        // Commit 3
        RevCommit logC3 = log.next();
        assertEquals(c5.getAuthor(), logC3.getAuthor());
        assertEquals(c5.getCommitter(), logC3.getCommitter());
        assertEquals(c5.getMessage(), logC3.getMessage());
        assertEquals(c5.getTreeId(), logC3.getTreeId());

        // Commit 2
        RevCommit logC2 = log.next();
        assertEquals(c3.getAuthor(), logC2.getAuthor());
        assertEquals(c3.getCommitter(), logC2.getCommitter());
        assertEquals(c3.getMessage(), logC2.getMessage());
        assertEquals(c3.getTreeId(), logC2.getTreeId());

        // Commit 1
        RevCommit logC1 = log.next();
        assertEquals(c1.getAuthor(), logC1.getAuthor());
        assertEquals(c1.getCommitter(), logC1.getCommitter());
        assertEquals(c1.getMessage(), logC1.getMessage());
        assertEquals(c1.getTreeId(), logC1.getTreeId());

    }

    @Test
    public void testMergeNoCommitMessage() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added
        // | |
        // o | - Lines 1 added
        // |/
        // o - master - HEAD - Merge commit

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        final MergeReport mergeReport = geogig.command(MergeOp.class)
                .addCommit(branch1.getObjectId()).call();

        RevTree mergedTree = repo.getTree(mergeReport.getMergeCommit().getTreeId());

        String path = appendChild(pointsName, points2.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        path = appendChild(pointsName, points1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        path = appendChild(pointsName, points3.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        path = appendChild(linesName, lines1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        Iterator<RevCommit> log = geogig.command(LogOp.class).setFirstParentOnly(true).call();

        // Commit 4
        RevCommit logC4 = log.next();
        assertTrue(logC4.getMessage().contains("refs/heads/branch1"));
        assertEquals(2, logC4.getParentIds().size());
        assertEquals(c4.getId(), logC4.getParentIds().get(0));
        assertEquals(c2.getId(), logC4.getParentIds().get(1));

        // Commit 3
        RevCommit logC3 = log.next();
        assertEquals(c4.getAuthor(), logC3.getAuthor());
        assertEquals(c4.getCommitter(), logC3.getCommitter());
        assertEquals(c4.getMessage(), logC3.getMessage());
        assertEquals(c4.getTreeId(), logC3.getTreeId());

        // Commit 2
        RevCommit logC2 = log.next();
        assertEquals(c3.getAuthor(), logC2.getAuthor());
        assertEquals(c3.getCommitter(), logC2.getCommitter());
        assertEquals(c3.getMessage(), logC2.getMessage());
        assertEquals(c3.getTreeId(), logC2.getTreeId());

        // Commit 1
        RevCommit logC1 = log.next();
        assertEquals(c1.getAuthor(), logC1.getAuthor());
        assertEquals(c1.getCommitter(), logC1.getCommitter());
        assertEquals(c1.getMessage(), logC1.getMessage());
        assertEquals(c1.getTreeId(), logC1.getTreeId());

    }

    @Test
    public void testMergeTwice() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - master - HEAD - Points 3 added
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added
        // |/
        // o - master - HEAD - Merge commit

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        geogig.command(MergeOp.class).addCommit(branch1.getObjectId()).call();

        exception.expect(NothingToCommitException.class);
        geogig.command(MergeOp.class).addCommit(branch1.getObjectId()).call();
    }

    @Test
    public void testMergeFastForward() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - master - HEAD - Points 1 added
        // .\
        // . o - branch1 - Points 2 added
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |
        // o - master - HEAD - branch1 - Points 2 added

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        final MergeReport mergeReport = geogig.command(MergeOp.class)
                .addCommit(branch1.getObjectId()).call();

        RevTree mergedTree = repo.getTree(mergeReport.getMergeCommit().getTreeId());

        String path = appendChild(pointsName, points1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        path = appendChild(pointsName, points2.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();

        // Commit 2
        RevCommit logC2 = log.next();
        assertEquals(c2.getAuthor(), logC2.getAuthor());
        assertEquals(c2.getCommitter(), logC2.getCommitter());
        assertEquals(c2.getMessage(), logC2.getMessage());
        assertEquals(c2.getTreeId(), logC2.getTreeId());

        // Commit 1
        RevCommit logC1 = log.next();
        assertEquals(c1.getAuthor(), logC1.getAuthor());
        assertEquals(c1.getCommitter(), logC1.getCommitter());
        assertEquals(c1.getMessage(), logC1.getMessage());
        assertEquals(c1.getTreeId(), logC1.getTreeId());

    }

    @Test
    public void testMergeFastForwardSecondCase() throws Exception {
        // Create the following revision graph
        // o - master - HEAD
        // .\
        // . o - branch1 - Points 1 added

        // create branch1 and checkout
        geogig.command(UpdateRef.class).setName(Ref.HEADS_PREFIX + "branch1")
                .setNewValue(ObjectId.NULL).call();
        geogig.command(UpdateSymRef.class).setName(Ref.HEAD)
                .setNewValue(Ref.HEADS_PREFIX + "branch1").call();
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // checkout master
        geogig.command(UpdateSymRef.class).setName(Ref.HEAD)
                .setNewValue(Ref.HEADS_PREFIX + "master").call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - master - HEAD - branch1 - Points 1 added

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        final MergeReport mergeReport = geogig.command(MergeOp.class)
                .addCommit(branch1.getObjectId()).call();

        RevTree mergedTree = repo.getTree(mergeReport.getMergeCommit().getTreeId());

        String path = appendChild(pointsName, points1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path).call()
                .isPresent());

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();

        // Commit 1
        RevCommit logC1 = log.next();
        assertEquals(c1.getAuthor(), logC1.getAuthor());
        assertEquals(c1.getCommitter(), logC1.getCommitter());
        assertEquals(c1.getMessage(), logC1.getMessage());
        assertEquals(c1.getTreeId(), logC1.getTreeId());

    }

    @Test
    public void testFastForwardOnly() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Point 1 added
        // |\
        // | o - TestBranch - Point 1 modified
        // |
        // o - master - HEAD - Point 1 modified
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        MergeOp mergeOp = geogig.command(MergeOp.class).addCommit(branch.getObjectId());
        mergeOp.setFastForwardOnly(true);
        exception.expect(IllegalStateException.class);
        mergeOp.call();
    }

    @Test
    public void testNoFastForward() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Master - Point 1 added
        // |
        // o - TestBranch - Point 2 added

        insertAndAdd(points1);
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points2);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        MergeOp mergeOp = geogig.command(MergeOp.class).addCommit(branch.getObjectId());
        mergeOp.setNoFastForward(true).call();
        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        RevCommit mergeCommit = log.next();
        assertEquals(2, mergeCommit.getParentIds().size());
        assertEquals(masterCommit.getId(), mergeCommit.getParentIds().get(0));
        assertEquals(branchCommit.getId(), mergeCommit.getParentIds().get(1));
        assertEquals(COMMITTER_NAME, mergeCommit.getAuthor().getName().get());
        assertEquals(COMMITTER_EMAIL, mergeCommit.getAuthor().getEmail().get());
        assertEquals(COMMITTER_NAME, mergeCommit.getCommitter().getName().get());
        assertEquals(COMMITTER_EMAIL, mergeCommit.getCommitter().getEmail().get());
    }

    @Test
    public void testNoFastForwardSpecifyAuthor() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Master - Point 1 added
        // |
        // o - TestBranch - Point 2 added

        insertAndAdd(points1);
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points2);
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        MergeOp mergeOp = geogig.command(MergeOp.class).setAuthor("Test Author", "author@test.com")
                .addCommit(branch.getObjectId());
        mergeOp.setNoFastForward(true).call();
        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        RevCommit mergeCommit = log.next();
        assertEquals(2, mergeCommit.getParentIds().size());
        assertEquals(masterCommit.getId(), mergeCommit.getParentIds().get(0));
        assertEquals(branchCommit.getId(), mergeCommit.getParentIds().get(1));
        assertEquals("Test Author", mergeCommit.getAuthor().getName().get());
        assertEquals("author@test.com", mergeCommit.getAuthor().getEmail().get());
        assertEquals(COMMITTER_NAME, mergeCommit.getCommitter().getName().get());
        assertEquals(COMMITTER_EMAIL, mergeCommit.getCommitter().getEmail().get());

        assertEquals(masterCommit.getId(),
                geogig.command(RevParse.class).setRefSpec("master~1").call().get());
        assertEquals(masterCommit.getId(),
                geogig.command(RevParse.class).setRefSpec("master^1").call().get());
        assertEquals(branchCommit.getId(),
                geogig.command(RevParse.class).setRefSpec("master^2").call().get());
    }

    @Test
    public void testMergeNoCommits() throws Exception {
        exception.expect(IllegalArgumentException.class);
        geogig.command(MergeOp.class).call();
    }

    @Test
    public void testMergeNullCommit() throws Exception {
        exception.expect(IllegalArgumentException.class);
        geogig.command(MergeOp.class).addCommit(ObjectId.NULL).call();
    }

    @Test
    public void testMergeConflictingBranches() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1,2 added
        // |\
        // | o - TestBranch - Points 1 modified, 2 removed, 3 added
        // |
        // o - master - HEAD - Points 1 modifiedB, 2 removed
        insertAndAdd(points1, points2);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insert(points1Modified);
        delete(points2);
        insert(points3);
        geogig.command(AddOp.class).call();
        RevCommit masterCommit = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insert(points1ModifiedB);
        delete(points2);
        geogig.command(AddOp.class).call();
        RevCommit branchCommit = geogig.command(CommitOp.class).call();
        // Now try to merge branch into master
        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        try {
            geogig.command(MergeOp.class).addCommit(branch.getObjectId()).call();
            fail();
        } catch (MergeConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        Optional<Ref> ref = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertTrue(ref.isPresent());
        assertEquals(masterCommit.getId(), ref.get().getObjectId());
        ref = geogig.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertTrue(ref.isPresent());
        assertEquals(branch.getObjectId(), ref.get().getObjectId());

        String msg = geogig.command(ReadMergeCommitMessageOp.class).call();
        assertFalse(Strings.isNullOrEmpty(msg));

        List<Conflict> conflicts = Lists
                .newArrayList(geogig.command(ConflictsQueryOp.class).call());
        assertEquals(1, conflicts.size());
        String path = NodeRef.appendChild(pointsName, idP1);
        assertEquals(conflicts.get(0).getPath(), path);
        assertEquals(conflicts.get(0).getOurs(),
                RevFeature.builder().build(points1Modified).getId());
        assertEquals(conflicts.get(0).getTheirs(),
                RevFeature.builder().build(points1ModifiedB).getId());

        // try to commit
        try {
            geogig.command(CommitOp.class).call();
            fail();
        } catch (ConflictsException e) {
            assertEquals(e.getMessage(),
                    "Cannot run operation while merge or rebase conflicts exist.");
        }

        // solve, and commit
        Feature points1Merged = feature(pointsType, idP1, "StringProp1_2", new Integer(2000),
                "POINT(1 1)");
        insert(points1Merged);
        geogig.command(AddOp.class).call();
        RevCommit commit = geogig.command(CommitOp.class).call();
        assertTrue(commit.getMessage().contains(idP1));
        List<ObjectId> parents = commit.getParentIds();
        assertEquals(2, parents.size());
        assertEquals(masterCommit.getId(), parents.get(0));
        assertEquals(branchCommit.getId(), parents.get(1));
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(RevFeature.builder().build(points1Merged), revFeature.get());
        path = NodeRef.appendChild(pointsName, idP2);
        revFeature = geogig.command(RevObjectParse.class).setRefSpec(Ref.HEAD + ":" + path)
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());
        path = NodeRef.appendChild(pointsName, idP3);
        revFeature = geogig.command(RevObjectParse.class).setRefSpec(Ref.HEAD + ":" + path)
                .call(RevFeature.class);
        assertTrue(revFeature.isPresent());

        ref = geogig.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertFalse(ref.isPresent());

    }

    @Test
    public void testConflictingOctopusMerge() throws Exception {
        // Create the following revision graph
        // . o
        // . |
        // . o - Points 1 added
        // . |\
        // . | o - branch1 - Points 1 modified
        // . |
        // . o - Points 2 added
        // ./|
        // o | - branch2 - Point 1 modified B
        // . |
        // . o - master - HEAD - Point 1 modified C
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();
        insertAndAdd(points2);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("branch2").call();
        Feature points1ModifiedC = feature(pointsType, idP1, "StringProp1_4", new Integer(3000),
                "POINT(1 3)");
        insertAndAdd(points1ModifiedC);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch1").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch2").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 2)");
        insertAndAdd(points1ModifiedB);
        geogig.command(CommitOp.class).call();

        // Now try to merge all branches into master
        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        Ref branch2 = geogig.command(RefParse.class).setName("branch2").call().get();
        MergeOp mergeOp = geogig.command(MergeOp.class);
        mergeOp.addCommit(branch1.getObjectId());
        mergeOp.addCommit(branch2.getObjectId());
        try {
            mergeOp.call();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage()
                    .contains("Cannot merge more than two commits when conflicts exist"));
        }

    }

    @Test
    public void testMergeConflictingBranchesOurs() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - TestBranch - Points 1 modified and points 2 added
        // |
        // o - master - HEAD - Points 1 modifiedB
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        geogig.command(CommitOp.class).call();

        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        geogig.command(MergeOp.class).addCommit(branch.getObjectId()).setOurs(true).call();

        String path = NodeRef.appendChild(pointsName, idP1);
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(RevFeature.builder().build(points1Modified), revFeature.get());
        path = NodeRef.appendChild(pointsName, idP2);
        revFeature = geogig.command(RevObjectParse.class).setRefSpec(Ref.HEAD + ":" + path)
                .call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(RevFeature.builder().build(points2), revFeature.get());
    }

    @Test
    public void testMergeConflictingBranchesTheirs() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - TestBranch - Points 1 modified
        // |
        // o - master - HEAD - Points 1 modifiedB
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        geogig.command(CommitOp.class).call();

        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        geogig.command(MergeOp.class).addCommit(branch.getObjectId()).setTheirs(true).call();

        String path = NodeRef.appendChild(pointsName, idP1);
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(RevFeature.builder().build(points1ModifiedB), revFeature.get());
    }

    @Test
    public void testOursAndTheirs() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        try {
            geogig.command(MergeOp.class).addCommit(branch.getObjectId()).setTheirs(true)
                    .setOurs(true).call();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testNoCommitMerge() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - master - HEAD - Points 3 added
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        RevCommit lastCommit = geogig.command(CommitOp.class).setMessage("commit for " + idP3)
                .call();
        Ref branch = geogig.command(RefParse.class).setName("branch1").call().get();

        geogig.command(MergeOp.class).addCommit(branch.getObjectId()).setNoCommit(true).call();

        String path = NodeRef.appendChild(pointsName, idP2);
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec(Ref.STAGE_HEAD + ":" + path).call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(RevFeature.builder().build(points2), revFeature.get());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec(Ref.HEAD + ":" + path)
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec(Ref.WORK_HEAD + ":" + path)
                .call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(RevFeature.builder().build(points2), revFeature.get());

        Optional<Ref> ref = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertTrue(ref.isPresent());
        assertEquals(lastCommit.getId(), ref.get().getObjectId());
        ref = geogig.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertTrue(ref.isPresent());
        assertEquals(branch.getObjectId(), ref.get().getObjectId());

    }

    @Test
    public void testConflictingMergeInterceptor() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - TestBranch - Points 1 modified and points 2 added
        // |
        // o - master - HEAD - Points 1 modifiedB
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        geogig.command(CommitOp.class).call();

        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        try {
            geogig.command(MergeOp.class).addCommit(branch.getObjectId()).call();
            fail();
        } catch (MergeConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        try {
            geogig.command(CommitOp.class).call();
            fail();
        } catch (ConflictsException e) {
            assertEquals(e.getMessage(),
                    "Cannot run operation while merge or rebase conflicts exist.");
        }

    }

    @Test
    public void testMergeConflictingPolygon() throws Exception {
        String polyId = "polyId";
        String polygonTypeSpec = "poly:Polygon:srid=4326";
        SimpleFeatureType polygonType = DataUtilities.createType("http://geogig.polygon",
                "polygons", polygonTypeSpec);
        Feature polygonOriginal = feature(polygonType, polyId,
                "POLYGON((0 0,1 0,2 0,3 0,4 0,5 0,5 1,4 1,3 1,2 1,1 1,1 0,0 0))");
        insertAndAdd(polygonOriginal);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature polygonMaster = feature(polygonType, polyId,
                "POLYGON((0 0,1 0,2 0.2,3 0.2,4 0,5 0,5 1,4 1,3 1,2 1,1 1,1 0,0 0))");
        insertAndAdd(polygonMaster);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature polygonBranch = feature(polygonType, polyId,
                "POLYGON((0 0,1 0,2 0,3 0,4 0,5 0,5 1,4 1,3 0.8,2 0.8,1 1,1 0,0 0))");
        insertAndAdd(polygonBranch);
        geogig.command(CommitOp.class).call();

        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();

        exception.expect(MergeConflictsException.class);
        exception.expectMessage("Merge conflict in polygons/polyId");
        geogig.command(MergeOp.class).addCommit(branch.getObjectId()).call();
    }

    @Test
    public void testMergeWithFeatureMerge() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - TestBranch - Points 1 modified and points 2 added
        // |
        // o - master - HEAD - Points 1 modifiedB
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
        insertAndAdd(points2);
        geogig.command(CommitOp.class).call();

        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        geogig.command(MergeOp.class).addCommit(branch.getObjectId()).call();

        String path = appendChild(pointsName, points1.getIdentifier().getID());

        Optional<RevFeature> feature = repo.command(RevObjectParse.class)
                .setRefSpec(/*
                             * mergeCommit. getId ().toString () + ":" +
                             */"WORK_HEAD" + ":" + path).call(RevFeature.class);
        assertTrue(feature.isPresent());

        Feature mergedFeature = feature(pointsType, idP1, "StringProp1_2", new Integer(2000),
                "POINT(1 1)");
        RevFeature expected = RevFeature.builder().build(mergedFeature);
        assertEquals(expected, feature.get());

    }

    @Test
    public void testMergeTwoBranchesWithNewFeatureType() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();
        insertAndAdd(lines1);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch1").call();
        insertAndAdd(poly1);
        RevCommit commit2 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        geogig.command(MergeOp.class).addCommit(commit2.getId()).call();

        Optional<NodeRef> ref = geogig.command(FindTreeChild.class).setChildPath(polyName).call();

        assertTrue(ref.isPresent());
        assertFalse(ref.get().getMetadataId().equals(ObjectId.NULL));
    }

    @Test
    public void testOctopusMerge() throws Exception {
        insertAndAdd(points1);
        RevCommit initialCommit = geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();
        geogig.command(BranchCreateOp.class).setName("branch2").call();
        geogig.command(BranchCreateOp.class).setName("branch3").call();
        geogig.command(BranchCreateOp.class).setName("branch4").call();
        geogig.command(BranchCreateOp.class).setName("branch5").call();
        geogig.command(BranchCreateOp.class).setName("branch6").call();
        geogig.command(CheckoutOp.class).setSource("branch1").call();
        ObjectId points2Id = insertAndAdd(points2);
        RevCommit branch1 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch2").call();
        ObjectId points3Id = insertAndAdd(points3);
        RevCommit branch2 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch3").call();
        ObjectId lines1Id = insertAndAdd(lines1);
        RevCommit branch3 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch4").call();
        ObjectId lines2Id = insertAndAdd(lines2);
        RevCommit branch4 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch5").call();
        ObjectId lines3Id = insertAndAdd(lines3);
        RevCommit branch5 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch6").call();
        ObjectId points1Id = insertAndAdd(points1_modified);
        RevCommit branch6 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        geogig.command(MergeOp.class).addCommit(branch1.getId()).addCommit(branch2.getId())
                .addCommit(branch3.getId()).addCommit(branch4.getId()).addCommit(branch5.getId())
                .addCommit(branch6.getId()).call();

        Optional<NodeRef> ref = geogig.command(FindTreeChild.class)
                .setChildPath(pointsName + "/" + idP1).call();
        assertTrue(ref.isPresent());
        assertEquals(points1Id, ref.get().getNode().getObjectId());
        ref = geogig.command(FindTreeChild.class).setChildPath(pointsName + "/" + idP2).call();
        assertTrue(ref.isPresent());
        assertEquals(points2Id, ref.get().getNode().getObjectId());
        ref = geogig.command(FindTreeChild.class).setChildPath(pointsName + "/" + idP3).call();
        assertTrue(ref.isPresent());
        assertEquals(points3Id, ref.get().getNode().getObjectId());
        ref = geogig.command(FindTreeChild.class).setChildPath(linesName + "/" + idL1).call();
        assertTrue(ref.isPresent());
        assertEquals(lines1Id, ref.get().getNode().getObjectId());
        ref = geogig.command(FindTreeChild.class).setChildPath(linesName + "/" + idL2).call();
        assertTrue(ref.isPresent());
        assertEquals(lines2Id, ref.get().getNode().getObjectId());
        ref = geogig.command(FindTreeChild.class).setChildPath(linesName + "/" + idL3).call();
        assertTrue(ref.isPresent());
        assertEquals(lines3Id, ref.get().getNode().getObjectId());

        Iterator<RevCommit> log = geogig.command(LogOp.class).setFirstParentOnly(true).call();

        // MergeCommit
        RevCommit logMerge = log.next();
        assertEquals(7, logMerge.getParentIds().size());

        // Initial Commit
        RevCommit initial = log.next();
        assertEquals(initialCommit.getMessage(), initial.getMessage());
        assertEquals(initialCommit.getCommitter().getName(), initial.getCommitter().getName());
        assertEquals(initialCommit.getCommitter().getEmail(), initial.getCommitter().getEmail());
        assertEquals(initialCommit.getAuthor().getTimeZoneOffset(),
                initial.getAuthor().getTimeZoneOffset());
        assertEquals(initialCommit.getCommitter().getTimeZoneOffset(),
                initial.getCommitter().getTimeZoneOffset());
        assertEquals(initialCommit.getTreeId(), initial.getTreeId());
        assertEquals(initialCommit.getId(), initial.getId());

        assertFalse(log.hasNext());
    }

    @Test
    public void testOctopusMergeWithAutomerge() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();
        geogig.command(BranchCreateOp.class).setName("branch2").call();
        geogig.command(BranchCreateOp.class).setName("branch3").call();
        geogig.command(BranchCreateOp.class).setName("branch4").call();
        geogig.command(BranchCreateOp.class).setName("branch5").call();
        geogig.command(BranchCreateOp.class).setName("branch6").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch1").call();
        insertAndAdd(points2);
        RevCommit branch1 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch2").call();
        insertAndAdd(points3);
        RevCommit branch2 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch3").call();
        insertAndAdd(lines1);
        RevCommit branch3 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch4").call();
        insertAndAdd(lines2);
        RevCommit branch4 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch5").call();
        insertAndAdd(lines3);
        RevCommit branch5 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch6").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        RevCommit branch6 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        MergeOp mergeOp = geogig.command(MergeOp.class).addCommit(branch1.getId())
                .addCommit(branch2.getId()).addCommit(branch3.getId()).addCommit(branch4.getId())
                .addCommit(branch5.getId()).addCommit(branch6.getId());
        try {
            mergeOp.call();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage()
                    .contains("Cannot merge more than two commits when conflicts exist"));
        }
    }

    @Test
    public void testOctopusMergeSameFeatureChanges() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();
        geogig.command(BranchCreateOp.class).setName("branch2").call();
        geogig.command(BranchCreateOp.class).setName("branch3").call();
        insertAndAdd(points1_modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch1").call();
        insertAndAdd(points2);
        RevCommit branch1 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch2").call();
        insertAndAdd(points3);
        RevCommit branch2 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("branch3").call();
        insertAndAdd(points1_modified);
        RevCommit branch3 = geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        geogig.command(MergeOp.class).addCommit(branch1.getId()).addCommit(branch2.getId())
                .addCommit(branch3.getId()).call();
        String path = NodeRef.appendChild(pointsName, idP1);
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(RevFeature.builder().build(points1_modified), revFeature.get());
    }

    @Test
    public void testBothBranchesSameGeometryChange() throws Exception {
        String ancestorLine = "LINESTRING (-75.1195282 38.7801263, -75.1195626 38.7806208, -75.1195701 38.780762, -75.1195916 38.7816402, -75.1195154 38.7820072)";
        String leftLine = "LINESTRING (-75.1195282 38.7801263, -75.1195626 38.7806208, -75.1195645 38.7807768, -75.1195916 38.7816402, -75.1195841 38.7817429, -75.1195702 38.7818159, -75.1195333 38.7819121, -75.119487 38.7819971)";
        String rightLine = "LINESTRING (-75.1195282 38.7801263, -75.1195626 38.7806208, -75.1195645 38.7807768, -75.1195916 38.7816402, -75.1195841 38.7817429, -75.1195702 38.7818159, -75.1195333 38.7819121, -75.119487 38.7819971)";

        final String fid = "112233";
        final Feature ancestor = super.feature(linesType, fid, "secondary", 1, ancestorLine);
        final Feature left = super.feature(linesType, fid, "secondary", 1, leftLine);
        final Feature right = super.feature(linesType, fid, "primary", 1, rightLine);

        super.insertAndAdd(ancestor);
        super.commit("common ancestor");

        geogig.command(BranchCreateOp.class).setName("branch").call();

        super.insertAndAdd(left);
        super.commit("master change");

        assertEquals("branch", geogig.command(CheckoutOp.class).setSource("branch").call()
                .getNewRef().localName());
        super.insertAndAdd(right);
        final RevCommit branchCommit = super.commit("branch change");

        geogig.command(CheckoutOp.class).setSource("master").call();

        geogig.command(MergeOp.class).addCommit(branchCommit.getId()).call();
    }

}
