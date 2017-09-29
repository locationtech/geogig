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

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.LinkedList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.remotes.PushOp;
import org.locationtech.geogig.remotes.SynchronizationException;
import org.locationtech.geogig.remotes.SynchronizationException.StatusCode;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.test.TestSupport;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public class PushOpTest extends RemoteRepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    private LinkedList<RevCommit> expectedMaster;

    private LinkedList<RevCommit> expectedBranch;

    private Repository remoteRepo, localRepo;

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
    }

    @Test
    public void testPush() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localRepo, lines3);
        RevCommit commit = localRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Push the commit
        PushOp push = pushOp();
        push.setProgressListener(SIMPLE_PROGRESS).call();

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
    public void testPushToRemote() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localRepo, lines3);
        RevCommit commit = localRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Push the commit
        PushOp push = pushOp();
        push.setRemote("origin").call();

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

        // Push the commit
        PushOp push = pushOp();
        push.setAll(true).setProgressListener(SIMPLE_PROGRESS).call();

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
        push.setProgressListener(SIMPLE_PROGRESS).call();

        assertTrue(
                remoteRepo.command(RefParse.class).setName("NewRemoteBranch").call().isPresent());

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

        // Push the commit
        PushOp push = pushOp();
        push.addRefSpec("master:NewRemoteBranch");
        push.addRefSpec("Branch1:NewRemoteBranch2");
        push.call();

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
        assertTrue(remoteRepo.command(RefParse.class).setName(refName).call().isPresent());

        PushOp push = pushOp();
        push.addRefSpec(refSpec);
        push.call();

        assertFalse(remoteRepo.command(RefParse.class).setName(refName).call().isPresent());
    }

    @Test
    public void testPushWithDefaultRefSpec() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localRepo, lines3);
        RevCommit commit = localRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Push the commit
        PushOp push = pushOp();
        push.addRefSpec(":");
        push.call();

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

        // Push the commit
        PushOp push = pushOp();
        push.addRefSpec("Branch1");
        push.call();

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
        RevCommit remotesTip = remoteRepo.command(CommitOp.class).call();

        // Add a commit to the local repository
        localRepo.command(CheckoutOp.class).setSource("Branch1").call();
        insertAndAdd(localRepo, lines3);
        RevCommit commit = localRepo.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);
        localRepo.command(CheckoutOp.class).setSource("master").call();

        // Push the commit
        PushOp push = pushOp();
        push.addRefSpec("+Branch1");
        push.setProgressListener(SIMPLE_PROGRESS).call();

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
