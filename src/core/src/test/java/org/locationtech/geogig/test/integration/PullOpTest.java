/*******************************************************************************
 * Copyright (c) 2012, 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CloneOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.LogOp;
import org.locationtech.geogig.api.porcelain.PullOp;
import org.locationtech.geogig.remote.RemoteRepositoryTestCase;

import com.google.common.base.Optional;

public class PullOpTest extends RemoteRepositoryTestCase {
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
    public void testPullRebase() throws Exception {
        // Add a commit to the remote
        insertAndAdd(remoteGeogig.geogig, lines3);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Pull the commit
        PullOp pull = pull();
        pull.setRebase(true).setAll(true).call();

        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPullNullCurrentBranch() throws Exception {
        // Add a commit to the remote
        insertAndAdd(remoteGeogig.geogig, lines3);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        localGeogig.geogig.command(UpdateRef.class).setName("master").setNewValue(ObjectId.NULL)
                .call();
        localGeogig.geogig.command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue("master")
                .call();

        // Pull the commit
        PullOp pull = pull();
        pull.setRebase(true).call();

        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPullMerge() throws Exception {
        // Add a commit to the remote
        insertAndAdd(remoteGeogig.geogig, lines3);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Pull the commit
        PullOp pull = pull();
        pull.setRemote("origin").call();

        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPullRefspecs() throws Exception {
        // Add a commit to the remote
        insertAndAdd(remoteGeogig.geogig, lines3);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Pull the commit
        PullOp pull = pull();
        pull.addRefSpec("master:newbranch");
        pull.setRebase(true).call();

        final Optional<Ref> currHead = localGeogig.geogig.command(RefParse.class).setName(Ref.HEAD)
                .call();
        assertTrue(currHead.isPresent());
        assertTrue(currHead.get() instanceof SymRef);
        final SymRef headRef = (SymRef) currHead.get();
        final String currentBranch = Ref.localName(headRef.getTarget());
        assertEquals("newbranch", currentBranch);

        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPullRefspecForce() throws Exception {
        // Add a commit to the remote
        insertAndAdd(remoteGeogig.geogig, lines3);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Pull the commit
        PullOp pull = pull();
        pull.addRefSpec("+master:newbranch");
        pull.setRebase(true).call();

        final Optional<Ref> currHead = localGeogig.geogig.command(RefParse.class).setName(Ref.HEAD)
                .call();
        assertTrue(currHead.isPresent());
        assertTrue(currHead.get() instanceof SymRef);
        final SymRef headRef = (SymRef) currHead.get();
        final String currentBranch = Ref.localName(headRef.getTarget());
        assertEquals("newbranch", currentBranch);

        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPullMultipleRefspecs() throws Exception {
        // Add a commit to the remote
        insertAndAdd(remoteGeogig.geogig, lines3);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Pull the commit
        PullOp pull = pull();
        pull.addRefSpec("master:newbranch");
        pull.addRefSpec("Branch1:newbranch2");
        pull.setRebase(true).call();

        final Optional<Ref> currHead = localGeogig.geogig.command(RefParse.class).setName(Ref.HEAD)
                .call();
        assertTrue(currHead.isPresent());
        assertTrue(currHead.get() instanceof SymRef);
        final SymRef headRef = (SymRef) currHead.get();
        final String currentBranch = Ref.localName(headRef.getTarget());
        assertEquals("newbranch2", currentBranch);

        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedBranch, logged);

        localGeogig.geogig.command(CheckoutOp.class).setSource("newbranch").call();
        logs = localGeogig.geogig.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPullTooManyRefs() throws Exception {
        // Add a commit to the remote
        insertAndAdd(remoteGeogig.geogig, lines3);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Pull the commit
        PullOp pull = pull();
        pull.addRefSpec("master:newbranch:newbranch2");
        exception.expect(IllegalArgumentException.class);
        pull.setRebase(true).call();
    }

    @Test
    public void testPullToCurrentBranch() throws Exception {
        // Add a commit to the remote
        insertAndAdd(remoteGeogig.geogig, lines3);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Make sure the local master matches the remote
        localGeogig.geogig.command(BranchCreateOp.class).setName("mynewbranch")
                .setAutoCheckout(true).call();

        // Pull the commit
        PullOp pull = pull();
        pull.addRefSpec("master");
        pull.setRebase(true).call();

        final Optional<Ref> currHead = localGeogig.geogig.command(RefParse.class).setName(Ref.HEAD)
                .call();
        assertTrue(currHead.isPresent());
        assertTrue(currHead.get() instanceof SymRef);
        final SymRef headRef = (SymRef) currHead.get();
        final String currentBranch = Ref.localName(headRef.getTarget());
        assertEquals("mynewbranch", currentBranch);

        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);
    }
}
