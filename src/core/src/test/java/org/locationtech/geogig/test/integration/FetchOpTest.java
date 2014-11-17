/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.BranchDeleteOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CloneOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.FetchOp;
import org.locationtech.geogig.api.porcelain.LogOp;
import org.locationtech.geogig.api.porcelain.TagCreateOp;
import org.locationtech.geogig.api.porcelain.TagListOp;
import org.locationtech.geogig.remote.RemoteRepositoryTestCase;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

public class FetchOpTest extends RemoteRepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    LinkedList<RevCommit> expectedMaster;

    LinkedList<RevCommit> expectedBranch;

    @Override
    protected void setUpInternal() throws Exception {
    }

    private void prepareForFetch(boolean doClone) throws Exception {
        if (doClone) {
            // clone the repository
            CloneOp clone = clone();
            clone.setRepositoryURL(remoteGeogig.envHome.getCanonicalPath()).call();
        }

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
        List<RevCommit> logged = Lists.newArrayList(logs);
        assertEquals(expectedBranch, logged);

        // Checkout master and commit some changes
        remoteGeogig.geogig.command(CheckoutOp.class).setSource("master").call();

        insertAndAdd(remoteGeogig.geogig, lines1);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        insertAndAdd(remoteGeogig.geogig, lines2);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        remoteGeogig.geogig.command(TagCreateOp.class) //
            .setMessage("TestTag") //
            .setCommitId(commit.getId()) //
            .setName("test") //
            .call();

        // Make sure master has all of the commits
        logs = remoteGeogig.geogig.command(LogOp.class).call();
        logged = Lists.newArrayList(logs);
        assertEquals(expectedMaster, logged);
    }

    private void verifyFetch() throws Exception {
        // Make sure the local repository got all of the commits from master
        localGeogig.geogig.command(CheckoutOp.class).setSource("refs/remotes/origin/master").call();
        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = Lists.newArrayList(logs);

        assertEquals(expectedMaster, logged);

        // Make sure the local repository got all of the commits from Branch1
        localGeogig.geogig.command(CheckoutOp.class).setSource("refs/remotes/origin/Branch1")
                .call();
        logs = localGeogig.geogig.command(LogOp.class).call();
        logged = Lists.newArrayList(logs);
        assertEquals(expectedBranch, logged);

        List<RevTag> tags = localGeogig.geogig.command(TagListOp.class).call();
        assertEquals(1, tags.size());
    }

    private void verifyPrune() throws Exception {
        // Make sure the local repository got all of the commits from master
        localGeogig.geogig.command(CheckoutOp.class).setForce(true)
                .setSource("refs/remotes/origin/master").call();
        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = Lists.newArrayList(logs);

        assertEquals(expectedMaster, logged);

        // Make sure the local repository no longer has Branch1
        Optional<Ref> missing = localGeogig.geogig.command(RefParse.class)
                .setName("refs/remotes/origin/Branch1").call();

        assertFalse(missing.isPresent());
    }

    @Test
    public void testFetch() throws Exception {

        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetch();
        fetch.call();

        verifyFetch();
    }

    @Test
    public void testFetchDepth() throws Exception {
        prepareForFetch(false);

        // clone the repository
        CloneOp clone = clone();
        clone.setDepth(2);
        String repositoryURL = remoteGeogig.envHome.getCanonicalPath();
        clone.setRepositoryURL(repositoryURL).call();

        FetchOp fetch = fetch();
        fetch.setDepth(3);
        fetch.call();

        // Make sure the local repository got all of the commits from master
        localGeogig.geogig.command(CheckoutOp.class).setSource("refs/remotes/origin/master").call();
        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = Lists.newArrayList(logs);

        assertEquals(3, logged.size());

        assertEquals(expectedMaster.get(0), logged.get(0));
        assertEquals(expectedMaster.get(1), logged.get(1));
        assertEquals(expectedMaster.get(2), logged.get(2));

        // Make sure the local repository got all of the commits from Branch1
        localGeogig.geogig.command(CheckoutOp.class).setSource("refs/remotes/origin/Branch1")
                .call();
        logs = localGeogig.geogig.command(LogOp.class).call();
        logged = Lists.newArrayList(logs); 

        assertEquals(3, logged.size());

        assertEquals(expectedBranch.get(0), logged.get(0));
        assertEquals(expectedBranch.get(1), logged.get(1));
        assertEquals(expectedBranch.get(2), logged.get(2));
    }

    @Test
    public void testFetchFullDepth() throws Exception {
        prepareForFetch(false);

        // clone the repository
        CloneOp clone = clone();
        clone.setDepth(2);
        clone.setRepositoryURL(remoteGeogig.envHome.getCanonicalPath()).call();

        FetchOp fetch = fetch();
        fetch.setFullDepth(true);
        fetch.call();

        verifyFetch();
    }

    @Test
    public void testFetchNewCommitsWithShallowClone() throws Exception {
        prepareForFetch(false);

        // clone the repository
        CloneOp clone = clone();
        clone.setDepth(2);
        clone.setRepositoryURL(remoteGeogig.envHome.getCanonicalPath()).call();

        // Checkout master and commit some changes
        remoteGeogig.geogig.command(CheckoutOp.class).setSource("master").call();

        insertAndAdd(remoteGeogig.geogig, points1_modified);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        FetchOp fetch = fetch();
        fetch.call();

        // Make sure the local repository got all of the commits from master
        localGeogig.geogig.command(CheckoutOp.class).setSource("refs/remotes/origin/master").call();
        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = Lists.newArrayList(logs);

        assertEquals(3, logged.size());

        assertEquals(expectedMaster.get(0), logged.get(0));
        assertEquals(expectedMaster.get(1), logged.get(1));
        assertEquals(expectedMaster.get(2), logged.get(2));
    }

    @Test
    public void testFetchNewCommitsWithShallowClone2() throws Exception {
        insertAndAdd(remoteGeogig.geogig, points1);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).setMessage("1").call();
        insertAndAdd(remoteGeogig.geogig, points2);
        commit = remoteGeogig.geogig.command(CommitOp.class).setMessage("2").call();
        insertAndAdd(remoteGeogig.geogig, points3);
        commit = remoteGeogig.geogig.command(CommitOp.class).setMessage("3").call();

        // clone the repository
        CloneOp clone = clone();
        clone.setDepth(2);
        clone.setRepositoryURL(remoteGeogig.envHome.getCanonicalPath()).call();

        // Checkout master and commit some changes
        remoteGeogig.geogig.command(CheckoutOp.class).setSource("master").call();

        insertAndAdd(remoteGeogig.geogig, lines1);
        commit = remoteGeogig.geogig.command(CommitOp.class).setMessage("4").call();
        insertAndAdd(remoteGeogig.geogig, points1_modified);
        commit = remoteGeogig.geogig.command(CommitOp.class).setMessage("5").call();
        insertAndAdd(remoteGeogig.geogig, lines2);
        commit = remoteGeogig.geogig.command(CommitOp.class).setMessage("6").call();
        insertAndAdd(remoteGeogig.geogig, lines3);
        commit = remoteGeogig.geogig.command(CommitOp.class).setMessage("7").call();

        FetchOp fetch = fetch();
        // fetch.setDepth(2);
        fetch.call();

        localGeogig.geogig.command(CheckoutOp.class).setSource("refs/remotes/origin/master").call();
        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = Lists.newArrayList(logs);

        // Should have the previous 2 commits, plus all 4 new commits.
        assertEquals(6, logged.size());

    }

    @Test
    public void testFetchNewRefWithShallowClone() throws Exception {
        // Commit several features to the remote

        expectedMaster = new LinkedList<RevCommit>();
        expectedBranch = new LinkedList<RevCommit>();

        insertAndAdd(remoteGeogig.geogig, points1);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        RevCommit originCommit = commit;
        expectedMaster.addFirst(commit);
        expectedBranch.addFirst(commit);

        insertAndAdd(remoteGeogig.geogig, lines1);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        insertAndAdd(remoteGeogig.geogig, lines2);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        // Make sure master has all of the commits
        Iterator<RevCommit> logs = remoteGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = Lists.newArrayList(logs);

        assertEquals(expectedMaster, logged);

        // clone the repository
        CloneOp clone = clone();
        clone.setDepth(2);
        clone.setRepositoryURL(remoteGeogig.envHome.getCanonicalPath()).call();

        // Create and checkout branch1
        remoteGeogig.geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("Branch1")
                .setSource(originCommit.getId().toString()).call();

        // Commit some changes to branch1
        insertAndAdd(remoteGeogig.geogig, points2);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        insertAndAdd(remoteGeogig.geogig, points3);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        // Make sure Branch1 has all of the commits
        logs = remoteGeogig.geogig.command(LogOp.class).call();
        logged = Lists.newArrayList(logs);

        assertEquals(expectedBranch, logged);

        FetchOp fetch = fetch();
        fetch.call();

        // Make sure the local repository got all of the commits from master
        localGeogig.geogig.command(CheckoutOp.class).setSource("refs/remotes/origin/master").call();
        logs = localGeogig.geogig.command(LogOp.class).call();
        logged = Lists.newArrayList(logs);

        assertEquals(2, logged.size());

        assertEquals(expectedMaster.get(0), logged.get(0));
        assertEquals(expectedMaster.get(1), logged.get(1));

        // Make sure the local repository got all of the commits from Branch1
        localGeogig.geogig.command(CheckoutOp.class).setSource("refs/remotes/origin/Branch1")
                .call();
        logs = localGeogig.geogig.command(LogOp.class).call();
        logged = Lists.newArrayList(logs);

        assertEquals(2, logged.size());

        assertEquals(expectedBranch.get(0), logged.get(0));
        assertEquals(expectedBranch.get(1), logged.get(1));
    }

    @Test
    public void testFetchDepthWithFullRepo() throws Exception {
        prepareForFetch(true);

        FetchOp fetch = fetch();
        fetch.setDepth(2);
        fetch.call();

        verifyFetch();
    }

    @Test
    public void testFetchFullDepthWithFullRepo() throws Exception {
        prepareForFetch(true);

        FetchOp fetch = fetch();
        fetch.setFullDepth(true);
        fetch.call();

        verifyFetch();
    }

    @Test
    public void testFetchAll() throws Exception {
        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetch();
        fetch.setAll(true).call();

        verifyFetch();
    }

    @Test
    public void testFetchSpecificRemote() throws Exception {
        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetch();
        fetch.addRemote("origin").call();

        verifyFetch();
    }

    @Test
    public void testFetchSpecificRemoteAndAll() throws Exception {
        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetch();
        fetch.addRemote("origin").setAll(true).call();

        verifyFetch();
    }

    @Test
    public void testFetchNoRemotes() throws Exception {
        FetchOp fetch = fetch();
        exception.expect(IllegalStateException.class);
        fetch.call();
    }

    @Test
    public void testFetchNoChanges() throws Exception {
        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetch();
        fetch.addRemote("origin").setAll(true).call();

        verifyFetch();

        // fetch again
        fetch.call();

        verifyFetch();
    }

    @Test
    public void testFetchWithPrune() throws Exception {
        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetch();
        fetch.addRemote("origin").setAll(true).call();

        verifyFetch();

        // Remove a branch from the remote
        remoteGeogig.geogig.command(BranchDeleteOp.class).setName("Branch1").call();

        // fetch again
        fetch = fetch();
        fetch.setPrune(true).call();

        verifyPrune();
    }

    @Test
    public void testFetchWithPruneAndBranchAdded() throws Exception {
        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetch();
        fetch.addRemote("origin").setAll(true).call();

        verifyFetch();

        // Remove a branch from the remote
        remoteGeogig.geogig.command(BranchDeleteOp.class).setName("Branch1").call();

        // Add another branch
        remoteGeogig.geogig.command(BranchCreateOp.class).setName("Branch2").call();

        // fetch again
        fetch = fetch();
        fetch.setPrune(true).call();

        verifyPrune();

        // Make sure the local repository has Branch2
        Optional<Ref> missing = localGeogig.geogig.command(RefParse.class)
                .setName("refs/remotes/origin/Branch2").call();

        assertTrue(missing.isPresent());
    }
}
