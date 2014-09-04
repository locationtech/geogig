/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CloneOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.LogOp;
import org.locationtech.geogig.api.porcelain.PushOp;
import org.locationtech.geogig.api.porcelain.SynchronizationException;
import org.locationtech.geogig.remote.RemoteRepositoryTestCase;

import com.google.common.base.Optional;

public class PushOpTest extends RemoteRepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    private LinkedList<RevCommit> expectedMaster;

    private LinkedList<RevCommit> expectedBranch;

    @Override
    protected void setUpInternal() throws Exception {
        // Commit several features to the remote

        expectedMaster = new LinkedList<RevCommit>();
        expectedBranch = new LinkedList<RevCommit>();

        insertAndAdd(remoteGeogig.geogig, points1);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);
        expectedBranch.addFirst(commit);

        // Create and checkout branch1
        remoteGeogig.geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("Branch1")
                .call();

        // Commit some changes to branch1
        insertAndAdd(remoteGeogig.geogig, points2);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        insertAndAdd(remoteGeogig.geogig, points3);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        // Make sure Branch1 has all of the commits
        Iterator<RevCommit> logs = remoteGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedBranch, logged);

        // Checkout master and commit some changes
        remoteGeogig.geogig.command(CheckoutOp.class).setSource("master").call();

        insertAndAdd(remoteGeogig.geogig, lines1);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        insertAndAdd(remoteGeogig.geogig, lines2);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Make sure master has all of the commits
        logs = remoteGeogig.geogig.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);

        // Make sure the local repository has no commits prior to clone
        logs = localGeogig.geogig.command(LogOp.class).call();
        assertNotNull(logs);
        assertFalse(logs.hasNext());

        // clone from the remote
        CloneOp clone = clone();
        clone.setRepositoryURL(remoteGeogig.envHome.getCanonicalPath()).setBranch("Branch1").call();

        // Make sure the local repository got all of the commits
        logs = localGeogig.geogig.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedBranch, logged);

        // Make sure the local master matches the remote
        localGeogig.geogig.command(CheckoutOp.class).setSource("master").call();

        logs = localGeogig.geogig.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPush() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localGeogig.geogig, lines3);
        RevCommit commit = localGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Push the commit
        PushOp push = push();
        push.call();

        // verify that the remote got the commit
        Iterator<RevCommit> logs = remoteGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);

        // verify that the local reference of the remote master is updated
        Optional<Ref> ref = localGeogig.geogig.command(RefParse.class)
                .setName(Ref.append(Ref.REMOTES_PREFIX, "origin/master")).call();
        assertTrue(ref.isPresent());
        assertEquals(logged.get(0).getId(), ref.get().getObjectId());
    }

    @Test
    public void testPushToRemote() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localGeogig.geogig, lines3);
        RevCommit commit = localGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Push the commit
        PushOp push = push();
        push.setRemote("origin").call();

        // verify that the remote got the commit
        Iterator<RevCommit> logs = remoteGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);

        // verify that the local reference of the remote master is updated
        Optional<Ref> ref = localGeogig.geogig.command(RefParse.class)
                .setName(Ref.append(Ref.REMOTES_PREFIX, "origin/master")).call();
        assertTrue(ref.isPresent());
        assertEquals(logged.get(0).getId(), ref.get().getObjectId());

    }

    @Test
    public void testPushToRemoteHEAD() throws Exception {
        insertAndAdd(localGeogig.geogig, lines3);
        localGeogig.geogig.command(CommitOp.class).call();

        PushOp push = push();
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
        insertAndAdd(localGeogig.geogig, lines3);
        RevCommit commit = localGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        localGeogig.geogig.command(CheckoutOp.class).setSource("Branch1").call();
        insertAndAdd(localGeogig.geogig, points1_modified);
        RevCommit commit2 = localGeogig.geogig.command(CommitOp.class).call();
        expectedBranch.addFirst(commit2);

        // Push the commit
        PushOp push = push();
        push.setAll(true).call();

        // verify that the remote got the commit on both branches
        remoteGeogig.geogig.command(CheckoutOp.class).setSource("master").call();
        Iterator<RevCommit> logs = remoteGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }
        assertEquals(expectedMaster, logged);

        remoteGeogig.geogig.command(CheckoutOp.class).setSource("Branch1").call();
        logs = remoteGeogig.geogig.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }
        assertEquals(expectedBranch, logged);

    }

    @Test
    public void testPushWithRefSpec() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localGeogig.geogig, lines3);
        RevCommit commit = localGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Push the commit
        PushOp push = push();
        push.addRefSpec("master:NewRemoteBranch");
        push.call();

        assertTrue(remoteGeogig.geogig.command(RefParse.class).setName("NewRemoteBranch").call()
                .isPresent());

        // verify that the remote got the commit
        remoteGeogig.geogig.command(CheckoutOp.class).setSource("NewRemoteBranch").call();
        Iterator<RevCommit> logs = remoteGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);

        // verify that the local reference of the remote master is updated
        Optional<Ref> ref = localGeogig.geogig.command(RefParse.class)
                .setName(Ref.append(Ref.REMOTES_PREFIX, "origin/NewRemoteBranch")).call();
        assertTrue(ref.isPresent());
        assertEquals(logged.get(0).getId(), ref.get().getObjectId());
    }

    @Test
    public void testPushWithMultipleRefSpecs() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localGeogig.geogig, lines3);
        RevCommit commit = localGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Push the commit
        PushOp push = push();
        push.addRefSpec("master:NewRemoteBranch");
        push.addRefSpec("Branch1:NewRemoteBranch2");
        push.call();

        assertTrue(remoteGeogig.geogig.command(RefParse.class).setName("NewRemoteBranch").call()
                .isPresent());
        assertTrue(remoteGeogig.geogig.command(RefParse.class).setName("NewRemoteBranch2").call()
                .isPresent());

        // verify that the remote got the commit
        remoteGeogig.geogig.command(CheckoutOp.class).setSource("NewRemoteBranch").call();
        Iterator<RevCommit> logs = remoteGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);

        remoteGeogig.geogig.command(CheckoutOp.class).setSource("NewRemoteBranch2").call();
        logs = remoteGeogig.geogig.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedBranch, logged);
    }

    @Test
    public void testDeleteRemoteBranch() throws Exception {
        PushOp push = push();
        push.addRefSpec(":Branch1");
        push.call();

        assertFalse(remoteGeogig.geogig.command(RefParse.class).setName("Branch1").call()
                .isPresent());
    }

    @Test
    public void testPushWithDefaultRefSpec() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localGeogig.geogig, lines3);
        RevCommit commit = localGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Push the commit
        PushOp push = push();
        push.addRefSpec(":");
        push.call();

        // verify that the remote got the commit
        Iterator<RevCommit> logs = remoteGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPushBranch() throws Exception {
        // Add a commit to the local repository
        localGeogig.geogig.command(CheckoutOp.class).setSource("Branch1").call();
        insertAndAdd(localGeogig.geogig, lines3);
        RevCommit commit = localGeogig.geogig.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);
        localGeogig.geogig.command(CheckoutOp.class).setSource("master").call();

        // Push the commit
        PushOp push = push();
        push.addRefSpec("Branch1");
        push.call();

        // verify that the remote got the commit
        Optional<Ref> remoteRef = remoteGeogig.geogig.command(RefParse.class).setName("Branch1")
                .call();
        assertTrue(remoteRef.isPresent());
        assertTrue(remoteRef.get().getName().startsWith(Ref.HEADS_PREFIX));
        remoteGeogig.geogig.command(CheckoutOp.class).setSource("Branch1").call();
        Iterator<RevCommit> logs = remoteGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedBranch, logged);
    }

    @Test
    public void testPushBranchForce() throws Exception {
        // Add a commit to the local repository
        localGeogig.geogig.command(CheckoutOp.class).setSource("Branch1").call();
        insertAndAdd(localGeogig.geogig, lines3);
        RevCommit commit = localGeogig.geogig.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);
        localGeogig.geogig.command(CheckoutOp.class).setSource("master").call();

        // Push the commit
        PushOp push = push();
        push.addRefSpec("+Branch1");
        push.call();

        // verify that the remote got the commit
        remoteGeogig.geogig.command(CheckoutOp.class).setSource("Branch1").call();
        Iterator<RevCommit> logs = remoteGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedBranch, logged);
    }

    @Test
    public void testPushTooManyRefArgs() throws Exception {
        // Add a commit to the local repository
        insertAndAdd(localGeogig.geogig, lines3);
        RevCommit commit = localGeogig.geogig.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        // Push the commit
        PushOp push = push();
        push.addRefSpec("Branch1:master:HEAD");
        exception.expect(IllegalArgumentException.class);
        push.call();
    }
}
