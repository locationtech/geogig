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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.plumbing.remotes.RemoteRemoveOp;
import org.locationtech.geogig.plumbing.remotes.RemoteResolve;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ConflictsException;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.remotes.PullOp;
import org.locationtech.geogig.remotes.PullResult;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;

public class PullOpTest extends RemoteRepositoryTestCase {

    private LinkedList<RevCommit> expectedMaster;

    private LinkedList<RevCommit> expectedBranch;

    protected @Override void setUpInternal() throws Exception {
        // Commit several features to the remote

        expectedMaster = new LinkedList<RevCommit>();
        expectedBranch = new LinkedList<RevCommit>();

        insertAndAdd(originRepo, points1);
        RevCommit commit = originRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);
        expectedBranch.addFirst(commit);

        // Create and checkout branch1
        originRepo.command(BranchCreateOp.class).setAutoCheckout(true).setName("Branch1").call();

        // Commit some changes to branch1
        insertAndAdd(originRepo, points2);
        commit = originRepo.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        insertAndAdd(originRepo, points3);
        commit = originRepo.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        // Make sure Branch1 has all of the commits
        List<RevCommit> logged = log(originRepo);
        assertEquals(expectedBranch, logged);

        // Checkout master and commit some changes
        originRepo.command(CheckoutOp.class).setSource("master").call();

        insertAndAdd(originRepo, lines1);
        commit = originRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        insertAndAdd(originRepo, lines2);
        commit = originRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Make sure master has all of the commits
        logged = log(originRepo);
        assertEquals(expectedMaster, logged);

        // Make sure the local repository has no commits prior to clone
        logged = log(localRepo);
        assertTrue(logged.isEmpty());

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setRemoteURI(originRepo.getLocation()).setBranch("Branch1").call();

        // Make sure the local repository got all of the commits
        logged = log(localRepo);
        assertEquals(expectedBranch, logged);

        // Make sure the local master matches the remote
        localRepo.command(CheckoutOp.class).setSource("master").call();

        logged = log(localRepo);
        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPullRebase() throws Exception {
        // Add a commit to the remote
        insertAndAdd(originRepo, lines3);
        RevCommit commit = originRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Pull the commit
        PullOp pull = pullOp();
        pull.setRebase(true).setAll(true).call();

        List<RevCommit> logged = log(localRepo);
        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPullNullCurrentBranch() throws Exception {
        // Add a commit to the remote
        insertAndAdd(originRepo, lines3);
        RevCommit commit = originRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        localRepo.command(UpdateRef.class).setName("master").setNewValue(ObjectId.NULL)
                .setReason("test init").call();
        localRepo.command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue("master")
                .setReason("test init").call();

        // Pull the commit
        PullOp pull = pullOp();
        pull.setRebase(true).call();

        List<RevCommit> logged = log(localRepo);
        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPullMerge() throws Exception {
        // Add a commit to the remote
        insertAndAdd(originRepo, lines3);
        RevCommit commit = commit(originRepo, "lines3");
        expectedMaster.addFirst(commit);

        // Pull the commit
        PullOp pull = pullOp();
        pull.setRemote("origin").call();

        List<RevCommit> logged = log(localRepo);

        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPullMergeNothingToFetch() throws Exception {
        // Add a commit to the remote
        insertAndAdd(originRepo, lines3);
        RevCommit commit = commit(originRepo, "lines3");
        expectedMaster.addFirst(commit);

        // call fetch first so the missing objects are already in the local repo
        fetchOp().call();

        // Then Pull should update the target ref even if there's nothing to fetch
        PullOp pull = pullOp();
        pull.setRemote("origin").call();

        List<RevCommit> logged = log(localRepo);

        assertEquals(expectedMaster, logged);
    }

    /**
     * Pull from a remote that's not being saved as named remote in the repository
     */
    public @Test void testPullMergeNonPersistedRemote() throws Exception {
        // Add a commit to the remote
        insertAndAdd(originRepo, lines3);
        RevCommit commit = originRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        Remote removedOrigin = localRepo.command(RemoteRemoveOp.class).setName("origin").call();
        assertFalse(localRepo.command(RemoteResolve.class).setName("origin").call().isPresent());
        // Pull the commit
        PullOp pull = pullOp();

        pull.setRemote(removedOrigin).call();

        List<RevCommit> logged = log(localRepo);
        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPullRefspecs() throws Exception {
        // Add a commit to the remote
        insertAndAdd(originRepo, lines3);
        RevCommit commit = originRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        checkout(localRepo, "Branch1");
        List<RevCommit> logged = log(localRepo);
        assertEquals(3, logged.size());

        assertFalse(getRef(localRepo, "refs/remotes/origin/newbranch").isPresent());
        assertTrue(getRef(localRepo, "refs/remotes/origin/Branch1").isPresent());

        // Pull the commit
        PullOp pull = pullOp();
        pull.addRefSpec("master:newbranch");
        pull.addRefSpec("Branch1");
        PullResult result = pull.setRebase(true).call();

        assertTrue(getRef(localRepo, "refs/remotes/origin/newbranch").isPresent());
        assertTrue(getRef(localRepo, "refs/remotes/origin/Branch1").isPresent());

        logged = log(localRepo);
        assertEquals(8, logged.size());
    }

    @Test
    public void testPullRefspecForce() throws Exception {
        // Add a commit to the remote
        insertAndAdd(originRepo, lines3);
        RevCommit commit = originRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Pull the commit
        PullOp pull = pullOp();
        // fetch remote's master onto new ref refs/remotes/origin/newbranch, then pull from there
        pull.addRefSpec("+master:newbranch");
        PullResult result = pull.setRebase(true).call();

        Optional<Ref> fetchedToRef = getRef(localRepo, "refs/remotes/origin/newbranch");
        assertTrue(fetchedToRef.isPresent());

        List<RevCommit> logged = log(localRepo);
        assertEquals(expectedMaster, logged);

        Ref oldRef = result.getOldRef();
        Ref newRef = result.getNewRef();
        assertEquals("refs/heads/master", oldRef.getName());
        assertEquals("refs/heads/master", newRef.getName());
        assertEquals(fetchedToRef.get().getObjectId(), newRef.getObjectId());
    }

    @Test
    public void testPullMultipleRefspecs() throws Exception {
        // Add a commit to the remote
        checkout(originRepo, "master");
        insertAndAdd(originRepo, lines3);
        RevCommit commit = originRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        Feature points4 = feature(pointsType, "Points.4", "4", Integer.valueOf(4), "POINT(4 4)");
        checkout(originRepo, "Branch1");
        insertAndAdd(originRepo, points4);
        commit(originRepo, "Points.4");

        assertFalse(getRef(localRepo, "refs/remotes/origin/newbranch").isPresent());
        assertFalse(getRef(localRepo, "refs/remotes/origin/newbranch2").isPresent());

        checkout(localRepo, "Branch1");
        List<RevCommit> logged = log(localRepo);
        assertEquals(3, logged.size());

        // Pull the commit
        PullOp pull = pullOp();
        pull.addRefSpec("master:newbranch");
        pull.addRefSpec("Branch1:newbranch2");
        pull.setRebase(true).call();

        assertTrue(getRef(localRepo, "refs/remotes/origin/newbranch").isPresent());
        assertTrue(getRef(localRepo, "refs/remotes/origin/newbranch2").isPresent());

        logged = log(localRepo);
        assertEquals(9, logged.size());
    }

    @Test
    public void testPullMultipleRefspecsNonPersistedRemote() throws Exception {
        // Add a commit to the remote
        insertAndAdd(originRepo, lines3);
        RevCommit commit = originRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // remove the remote
        Remote removedOrigin = localRepo.command(RemoteRemoveOp.class).setName("origin").call();
        assertFalse(localRepo.command(RemoteResolve.class).setName("origin").call().isPresent());

        // Pull the commit
        PullOp pull = pullOp();
        pull.setRemote(removedOrigin);// client supplied remote
        pull.addRefSpec("master:newbranch");
        pull.addRefSpec("Branch1:newbranch2");
        pull.call();

        assertTrue(getRef(localRepo, "refs/remotes/origin/newbranch").isPresent());
        assertTrue(getRef(localRepo, "refs/remotes/origin/newbranch2").isPresent());

        assertEquals(7, log(localRepo).size());
    }

    @Test
    public void testPullTooManyRefs() throws Exception {
        // Add a commit to the remote
        insertAndAdd(originRepo, lines3);
        RevCommit commit = originRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Pull the commit
        PullOp pull = pullOp();
        pull.addRefSpec("master:newbranch:newbranch2");
        assertThrows(IllegalArgumentException.class, pull.setRebase(true)::call);
    }

    @Test
    public void testPullToCurrentBranch() throws Exception {
        // Add a commit to the remote
        insertAndAdd(originRepo, lines3);
        RevCommit commit = originRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Make sure the local master matches the remote
        localRepo.command(BranchCreateOp.class).setName("mynewbranch").setAutoCheckout(true).call();

        // Pull the commit
        PullOp pull = pullOp();
        pull.addRefSpec("master");
        pull.setRebase(true).call();

        final Optional<Ref> currHead = localRepo.command(RefParse.class).setName(Ref.HEAD).call();
        assertTrue(currHead.isPresent());
        assertTrue(currHead.get() instanceof SymRef);
        final SymRef headRef = (SymRef) currHead.get();
        final String currentBranch = Ref.localName(headRef.getTarget());
        assertEquals("mynewbranch", currentBranch);

        List<RevCommit> logged = log(localRepo);
        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testPullFailsToRunIfThereAreConflicts() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1,3 added
        // |\
        // | o - TestBranch - Points 1 modified and points 2 added
        // |
        // o - master - HEAD - Points 1 modifiedB
        Repository local = localRepo;
        {// set up a merge conflict scenario
            insertAndAdd(localRepo, points1, points3);
            local.command(CommitOp.class).call();
            local.command(BranchCreateOp.class).setName("TestBranch").call();
            Feature points1Modified = feature(pointsType, idP1, "StringProp1_2",
                    Integer.valueOf(1000), "POINT(1 1)");
            insertAndAdd(local, points1Modified);
            local.command(CommitOp.class).call();
            local.command(CheckoutOp.class).setSource("TestBranch").call();
            Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3",
                    Integer.valueOf(2000), "POINT(1 1)");
            insertAndAdd(local, points1ModifiedB);
            insertAndAdd(local, points2);
            local.command(CommitOp.class).call();

            local.command(CheckoutOp.class).setSource("master").call();
            Ref branch = local.command(RefParse.class).setName("TestBranch").call().get();
            try {
                local.command(MergeOp.class).addCommit(branch.getObjectId()).call();
                fail();
            } catch (MergeConflictsException e) {
                assertTrue(e.getMessage().contains("conflict"));
            }
        }
        try {
            local.command(PullOp.class).call();
            fail();
        } catch (ConflictsException e) {
            assertEquals(e.getMessage(),
                    "Cannot run operation while merge or rebase conflicts exist.");
        }

    }
}
