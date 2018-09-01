/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration.remoting;

import static com.google.common.base.Optional.absent;
import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.remotes.RemoteResolve;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.porcelain.ResetOp;
import org.locationtech.geogig.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.remotes.PushOp;
import org.locationtech.geogig.remotes.SynchronizationException;
import org.locationtech.geogig.remotes.SynchronizationException.StatusCode;
import org.locationtech.geogig.remotes.TransferSummary;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.test.TestSupport;
import org.opengis.feature.Feature;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;

public class PushOpTest extends RemoteRepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    private LinkedList<RevCommit> expectedMaster;

    private LinkedList<RevCommit> expectedBranch;

    private Repository remoteRepo, localRepo;

    private Remote remote;

    @Override
    protected void setUpInternal() throws Exception {
        // Commit several features to the remote
        remoteRepo = remoteGeogig.repo;
        localRepo = localGeogig.repo;

        expectedMaster = new LinkedList<RevCommit>();
        expectedBranch = new LinkedList<RevCommit>();

        insertAndAdd(remoteRepo, points1);
        RevCommit commit = remoteRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);
        expectedBranch.addFirst(commit);

        // Create and checkout branch1
        remoteRepo.command(BranchCreateOp.class).setAutoCheckout(true).setName("Branch1").call();

        // Commit some changes to branch1
        insertAndAdd(remoteRepo, points2);
        commit = remoteRepo.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        insertAndAdd(remoteRepo, points3);
        commit = remoteRepo.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        // Make sure Branch1 has all of the commits
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expectedBranch, logged);

        // Checkout master and commit some changes
        remoteRepo.command(CheckoutOp.class).setSource("master").call();

        insertAndAdd(remoteRepo, lines1);
        commit = remoteRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        insertAndAdd(remoteRepo, lines2);
        commit = remoteRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Make sure master has all of the commits
        logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expectedMaster, logged);

        // Make sure the local repository has no commits prior to clone
        logged = newArrayList(localRepo.command(LogOp.class).call());
        assertTrue(logged.isEmpty());

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).setBranch("Branch1").call();

        // Make sure the local repository got all of the commits
        logged = newArrayList(localRepo.command(LogOp.class).call());
        assertEquals(expectedBranch, logged);

        // Make sure the local master matches the remote
        localRepo.command(CheckoutOp.class).setSource("master").call();

        logged = newArrayList(localRepo.command(LogOp.class).call());
        assertEquals(expectedMaster, logged);

        remote = localRepo.command(RemoteResolve.class).setName(REMOTE_NAME).call().get();
    }

    @Test
    public void testPush() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localRepo, lines3);
        RevCommit commit = localRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        Optional<Ref> oldRef = getRef(remoteRepo, "master");
        Optional<Ref> newRef = getRef(localRepo, "master");

        // Push the commit
        PushOp push = pushOp();
        TransferSummary summary = push.setProgressListener(SIMPLE_PROGRESS).call();
        assertSummary(summary, remote.getPushURL(), oldRef, newRef);

        // verify that the remote got the commit
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expectedMaster, logged);

        // verify that the local reference of the remote master is updated
        Optional<Ref> ref = localRepo.command(RefParse.class)
                .setName(Ref.append(Ref.REMOTES_PREFIX, "origin/master")).call();
        assertTrue(ref.isPresent());
        assertEquals(logged.get(0).getId(), ref.get().getObjectId());
    }

    @Test
    public void testPushComplexHistory() throws Exception {
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);

        for (Feature f : features) {
            insertAndAdd(localRepo, f);
        }

        List<RevCommit> commitsToPush = Lists.newLinkedList();

        commitsToPush.add(commit(localRepo, "root commit"));

        createBranch(localRepo, "master_alt");
        checkout(localRepo, "master");

        insertAndAdd(localRepo, points1_modified);
        commitsToPush.add(commit(localRepo, "left modify 1"));

        createBranch(localRepo, "intermediate_left");
        checkout(localRepo, "master_alt");

        insertAndAdd(localRepo, points2_modified);
        commitsToPush.add(commit(localRepo, "right modify 1"));

        checkout(localRepo, "intermediate_left");

        commitsToPush.add(mergeNoFF(localRepo, "master_alt", "merge 1", true).getMergeCommit());

        createBranch(localRepo, "intermediate_right");
        checkout(localRepo, "master");

        insertAndAdd(localRepo, points3_modified);
        commitsToPush.add(commit(localRepo, "left modify 2"));

        checkout(localRepo, "intermediate_left");

        MergeReport merge2_left = mergeNoFF(localRepo, "master", "merge 2 left", true);
        commitsToPush.add(merge2_left.getMergeCommit());

        checkout(localRepo, "master");
        localRepo.command(ResetOp.class).setMode(ResetMode.HARD)
                .setCommit(Suppliers.ofInstance(merge2_left.getMergeCommit().getId())).call();

        checkout(localRepo, "master_alt");

        insertAndAdd(localRepo, lines1_modified);
        commitsToPush.add(commit(localRepo, "right modify 2"));

        checkout(localRepo, "intermediate_right");

        MergeReport merge2_right = mergeNoFF(localRepo, "master_alt", "merge 2 right", true);
        commitsToPush.add(merge2_right.getMergeCommit());

        checkout(localRepo, "master_alt");
        localRepo.command(ResetOp.class).setMode(ResetMode.HARD)
                .setCommit(Suppliers.ofInstance(merge2_right.getMergeCommit().getId())).call();

        checkout(localRepo, "master");

        commitsToPush.add(mergeNoFF(localRepo, "master_alt", "final merge", true).getMergeCommit());

        Optional<Ref> oldRef = getRef(remoteRepo, "master");
        Optional<Ref> newRef = getRef(localRepo, "master");

        // Push the commit
        PushOp push = pushOp();
        TransferSummary summary = push.setProgressListener(SIMPLE_PROGRESS).call();
        assertSummary(summary, remote.getPushURL(), oldRef, newRef);

        for (RevCommit commit : commitsToPush) {
            assertTrue(remoteRepo.objectDatabase().exists(commit.getId()));
            assertTrue(remoteRepo.objectDatabase().exists(commit.getTreeId()));
        }


        // Make sure the local repository got all of the commits
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());
        List<RevCommit> expected = newArrayList(localRepo.command(LogOp.class).call());

        assertEquals(expected, logged);
        TestSupport.verifySameContents(remoteRepo, localRepo);
    }

    @Test
    public void testNothingToPush() throws Exception {
        Optional<Ref> master = getRef(remoteRepo, "master");
        Optional<Ref> branch = getRef(remoteRepo, "Branch1");

        // Push the commit
        PushOp push = pushOp();
        TransferSummary summary = push.setProgressListener(SIMPLE_PROGRESS).call();
        assertTrue(summary.isEmpty());
        assertEquals(master, getRef(remoteRepo, "master"));
        assertEquals(branch, getRef(remoteRepo, "Branch1"));
    }

    @Test
    public void testPushToRemote() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localRepo, lines3);
        RevCommit commit = localRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        Optional<Ref> oldBranch = getRef(remoteRepo, "master");
        // Push the commit
        PushOp push = pushOp();
        TransferSummary summary = push.setRemote("origin").call();
        assertSummary(summary, remote.getPushURL(), oldBranch, getRef(localRepo, "master"));

        // verify that the remote got the commit
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expectedMaster, logged);

        // verify that the local reference of the remote master is updated
        Optional<Ref> ref = localRepo.command(RefParse.class)
                .setName(Ref.append(Ref.REMOTES_PREFIX, "origin/master")).call();
        assertTrue(ref.isPresent());
        assertEquals(logged.get(0).getId(), ref.get().getObjectId());

    }

    @Test
    public void testPushToRemoteHEAD() throws Exception {
        insertAndAdd(localRepo, lines3);
        localRepo.command(CommitOp.class).call();

        PushOp push = pushOp();
        try {
            push.setRemote("origin").addRefSpec("HEAD").call();
            fail();
        } catch (SynchronizationException e) {
            assertEquals(SynchronizationException.StatusCode.CANNOT_PUSH_TO_SYMBOLIC_REF,
                    e.statusCode);
        }

    }

    @Test
    public void testPushAll() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localRepo, lines3);
        RevCommit commit = localRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        localRepo.command(CheckoutOp.class).setSource("Branch1").call();
        insertAndAdd(localRepo, points1_modified);
        RevCommit commit2 = localRepo.command(CommitOp.class).call();
        expectedBranch.addFirst(commit2);

        Optional<Ref> oldMaster = getRef(remoteRepo, "master");
        Optional<Ref> oldBranch = getRef(remoteRepo, "Branch1");

        // Push the commit
        PushOp push = pushOp();
        TransferSummary summary = push.setAll(true).setProgressListener(SIMPLE_PROGRESS).call();
        assertSummary(summary, remote.getPushURL(), oldMaster, getRef(localRepo, "master"));
        assertSummary(summary, remote.getPushURL(), oldBranch, getRef(localRepo, "Branch1"));

        // verify that the remote got the commit on both branches
        remoteRepo.command(CheckoutOp.class).setSource("master").call();
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());
        assertEquals(expectedMaster, logged);

        remoteRepo.command(CheckoutOp.class).setSource("Branch1").call();
        logged = newArrayList(remoteRepo.command(LogOp.class).call());
        assertEquals(expectedBranch, logged);

    }

    @Test
    public void testPushWithRefSpec() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localRepo, lines3);
        RevCommit commit = localRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Push the commit
        PushOp push = pushOp();
        push.addRefSpec("master:NewRemoteBranch");
        TransferSummary summary = push.setProgressListener(SIMPLE_PROGRESS).call();
        assertSummary(summary, remote.getPushURL(), null,
                new Ref("refs/heads/NewRemoteBranch", commit.getId()));
        assertTrue(getRef(remoteRepo, "NewRemoteBranch").isPresent());

        // verify that the remote got the commit
        remoteRepo.command(CheckoutOp.class).setSource("NewRemoteBranch").call();
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expectedMaster, logged);

        // verify that the local reference of the remote master is updated
        Optional<Ref> ref = localRepo.command(RefParse.class)
                .setName(Ref.append(Ref.REMOTES_PREFIX, "origin/NewRemoteBranch")).call();
        assertTrue(ref.isPresent());
        assertEquals(logged.get(0).getId(), ref.get().getObjectId());
        TestSupport.verifyRepositoryContents(remoteRepo);
    }

    @Test
    public void testPushWithMultipleRefSpecs() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localRepo, lines3);
        RevCommit commit = localRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        final Ref master = getRef(localRepo, "master").get();
        final Ref branch1 = getRef(localRepo, "Branch1").get();

        // Push the commit
        PushOp push = pushOp();
        push.addRefSpec("master:NewRemoteBranch");
        push.addRefSpec("Branch1:NewRemoteBranch2");
        TransferSummary summary = push.call();
        assertSummary(summary, remote.getPushURL(), absent(),
                Optional.of(new Ref("refs/heads/NewRemoteBranch", master.getObjectId())));
        assertSummary(summary, remote.getPushURL(), absent(),
                Optional.of(new Ref("refs/heads/NewRemoteBranch2", branch1.getObjectId())));

        assertTrue(
                remoteRepo.command(RefParse.class).setName("NewRemoteBranch").call().isPresent());
        assertTrue(
                remoteRepo.command(RefParse.class).setName("NewRemoteBranch2").call().isPresent());

        // verify that the remote got the commit
        remoteRepo.command(CheckoutOp.class).setSource("NewRemoteBranch").call();
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expectedMaster, logged);

        remoteRepo.command(CheckoutOp.class).setSource("NewRemoteBranch2").call();
        logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expectedBranch, logged);
        TestSupport.verifyRepositoryContents(remoteRepo);
    }

    @Test
    public void testPushNewBranch() throws Exception {
        localRepo.command(BranchCreateOp.class).setName("newbranch").call();
        localRepo.command(CheckoutOp.class).setSource("newbranch").call();
        // Add a commit to the local repository
        insertAndAdd(localRepo, lines3);
        insertAndAdd(localRepo, points1_modified);
        localRepo.command(CommitOp.class).call();

        final Ref branch1 = getRef(localRepo, "newbranch").get();

        // Push the commit
        PushOp push = pushOp();
        push.addRefSpec("newbranch");
        TransferSummary summary = push.call();
        assertSummary(summary, remote.getPushURL(), absent(),
                Optional.of(new Ref("refs/heads/newbranch", branch1.getObjectId())));

        TestSupport.verifyRepositoryContents(remoteRepo);
    }

    @Test
    public void deleteRemoteBranchByName() throws Exception {
        testDeleteRemoteRef(":Branch1");
    }

    @Test
    public void deleteRemoteBranchByFullName() throws Exception {
        testDeleteRemoteRef(":refs/heads/Branch1");
    }

    @Test
    public void deleteRemoteBranchByPartialName() throws Exception {
        testDeleteRemoteRef(":heads/Branch1");
    }

    private void testDeleteRemoteRef(String refSpec) throws Exception {
        Preconditions.checkArgument(refSpec.startsWith(":") && refSpec.length() > 1);
        String refName = refSpec.substring(1);

        Optional<Ref> ref = getRef(remoteRepo, refName);
        assertTrue(ref.isPresent());

        PushOp push = pushOp();
        push.addRefSpec(refSpec);
        TransferSummary summary = push.call();
        assertSummary(summary, remote.getPushURL(), ref, absent());
        assertFalse(remoteRepo.command(RefParse.class).setName(refName).call().isPresent());
    }

    @Test
    public void testPushWithDefaultRefSpec() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localRepo, lines3);
        RevCommit commit = localRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        Optional<Ref> oldBranch = getRef(remoteRepo, "master");

        // Push the commit
        PushOp push = pushOp();
        push.addRefSpec(":");
        TransferSummary summary = push.call();
        assertSummary(summary, remote.getPushURL(), oldBranch, getRef(localRepo, "master"));

        // verify that the remote got the commit
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPushBranch() throws Exception {
        // Add a commit to the local repository
        localRepo.command(CheckoutOp.class).setSource("Branch1").call();
        insertAndAdd(localRepo, lines3);
        RevCommit commit = localRepo.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);
        localRepo.command(CheckoutOp.class).setSource("master").call();

        Optional<Ref> oldBranch = getRef(remoteRepo, "Branch1");
        // Push the commit
        PushOp push = pushOp();
        push.addRefSpec("Branch1");
        TransferSummary summary = push.call();
        assertSummary(summary, remote.getPushURL(), oldBranch, getRef(localRepo, "Branch1"));

        // verify that the remote got the commit
        Optional<Ref> remoteRef = remoteRepo.command(RefParse.class).setName("Branch1").call();
        assertTrue(remoteRef.isPresent());
        assertTrue(remoteRef.get().getName().startsWith(Ref.HEADS_PREFIX));
        remoteRepo.command(CheckoutOp.class).setSource("Branch1").call();
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expectedBranch, logged);
    }

    @Test
    public void testPushBranchForce() throws Exception {
        // Add a commit to the remote repository
        remoteRepo.command(CheckoutOp.class).setSource("Branch1").call();
        insertAndAdd(remoteRepo, points1_modified);
        remoteRepo.command(CommitOp.class).call();

        // Add a commit to the local repository
        localRepo.command(CheckoutOp.class).setSource("Branch1").call();
        insertAndAdd(localRepo, lines3);
        RevCommit commit = localRepo.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);
        localRepo.command(CheckoutOp.class).setSource("master").call();

        Optional<Ref> oldBranch = getRef(remoteRepo, "Branch1");

        // Push the commit
        PushOp push = pushOp();
        push.addRefSpec("+Branch1");
        TransferSummary summary = push.setProgressListener(SIMPLE_PROGRESS).call();
        assertSummary(summary, remote.getPushURL(), oldBranch, getRef(localRepo, "Branch1"));

        // verify that the remote got the commit
        remoteRepo.command(CheckoutOp.class).setSource("Branch1").call();
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expectedBranch, logged);

        TestSupport.verifyRepositoryContents(remoteRepo);
    }

    @Test
    public void testPushBranchNoForce() throws Exception {
        // Add a commit to the remote repository
        remoteRepo.command(CheckoutOp.class).setSource("Branch1").call();
        insertAndAdd(remoteRepo, points1_modified);
        RevCommit remotesTip = remoteRepo.command(CommitOp.class).call();

        // Add a commit to the local repository
        localRepo.command(CheckoutOp.class).setSource("Branch1").call();
        insertAndAdd(localRepo, lines3);
        localRepo.command(CommitOp.class).call();

        // Push the commit
        PushOp push = pushOp();
        push.addRefSpec("Branch1");
        try {
            push.setProgressListener(SIMPLE_PROGRESS).call();
            fail();
        } catch (SynchronizationException e) {
            assertEquals(StatusCode.REMOTE_HAS_CHANGES, e.statusCode);
        }
        // verify that the remote got the commit
        remoteRepo.command(CheckoutOp.class).setSource("Branch1").call();
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals("remote shouldn't have changed", remotesTip, logged.get(0));
    }

    @Test
    public void testPushTooManyRefArgs() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localRepo, lines3);
        RevCommit commit = localRepo.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        // Push the commit
        PushOp push = pushOp();
        push.addRefSpec("Branch1:master:HEAD");
        exception.expect(IllegalArgumentException.class);
        push.call();
    }
}
