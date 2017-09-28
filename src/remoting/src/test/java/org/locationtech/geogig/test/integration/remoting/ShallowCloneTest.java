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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.TagCreateOp;
import org.locationtech.geogig.porcelain.TagListOp;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.remotes.FetchOp;
import org.opengis.feature.Feature;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

/**
 * Integration test suite for shallow clones
 *
 */
public class ShallowCloneTest extends RemoteRepositoryTestCase {
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
            CloneOp clone = cloneOp();
            // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
            clone.setRemoteURI(remoteGeogig.envHome.toURI())
                    .setCloneURI(localGeogig.envHome.toURI()).call();
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
    public void testFetchDepth() throws Exception {
        prepareForFetch(false);

        // clone the repository
        CloneOp clone = cloneOp();
        clone.setDepth(2);
        String repositoryURL = remoteGeogig.envHome.toURI().toString();
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).setCloneURI(localGeogig.envHome.toURI())
                .call();

        FetchOp fetch = fetchOp();
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
        CloneOp clone = cloneOp();
        clone.setDepth(2);
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).setCloneURI(localGeogig.envHome.toURI())
                .call();

        FetchOp fetch = fetchOp();
        fetch.setFullDepth(true);
        fetch.call();

        verifyFetch();
    }

    @Test
    public void testFetchNewCommitsWithShallowClone() throws Exception {
        prepareForFetch(false);

        // clone the repository
        CloneOp clone = cloneOp();
        clone.setDepth(2);
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).setCloneURI(localGeogig.envHome.toURI())
                .call();

        // Checkout master and commit some changes
        remoteGeogig.geogig.command(CheckoutOp.class).setSource("master").call();

        insertAndAdd(remoteGeogig.geogig, points1_modified);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        FetchOp fetch = fetchOp();
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
        CloneOp clone = cloneOp();
        clone.setDepth(2);
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).setCloneURI(localGeogig.envHome.toURI())
                .call();

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

        FetchOp fetch = fetchOp();
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
        CloneOp clone = cloneOp();
        clone.setDepth(2);
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).setCloneURI(localGeogig.envHome.toURI())
                .call();

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

        FetchOp fetch = fetchOp();
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

        FetchOp fetch = fetchOp();
        fetch.setDepth(2);
        fetch.call();

        verifyFetch();
    }

    @Test
    public void testFetchFullDepthWithFullRepo() throws Exception {
        prepareForFetch(true);

        FetchOp fetch = fetchOp();
        fetch.setFullDepth(true);
        fetch.call();

        verifyFetch();
    }

    @Test
    public void testShallowClone() throws Exception {
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();

        for (Feature f : features) {
            ObjectId oId = insertAndAdd(remoteGeogig.geogig, f);
            final RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
            expected.addFirst(commit);
            Optional<RevObject> childObject = remoteGeogig.geogig.command(RevObjectParse.class)
                    .setObjectId(oId).call();
            assertTrue(childObject.isPresent());
        }

        // Make sure the remote has all of the commits
        Iterator<RevCommit> logs = remoteGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expected, logged);

        // Make sure the local repository has no commits prior to clone
        logs = localGeogig.geogig.command(LogOp.class).call();
        assertNotNull(logs);
        assertFalse(logs.hasNext());

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setDepth(2);
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI());
        clone.setCloneURI(localGeogig.envHome.toURI());
        clone.call();

        // Make sure the local repository got only 2 commits
        logs = localGeogig.geogig.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(2, logged.size());
        assertEquals(expected.get(0), logged.get(0));
        assertEquals(expected.get(1), logged.get(1));
    }
}
