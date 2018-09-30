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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.plumbing.MapRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.remotes.RemoteAddOp;
import org.locationtech.geogig.plumbing.remotes.RemoteRemoveOp;
import org.locationtech.geogig.plumbing.remotes.RemoteResolve;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.BranchDeleteOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.TagCreateOp;
import org.locationtech.geogig.porcelain.TagListOp;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.remotes.FetchOp;
import org.locationtech.geogig.remotes.TransferSummary;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.test.TestSupport;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

/**
 * {@link FetchOp} integration test suite for full clones (for shallow and sparse clones see
 * {@link ShallowCloneTest} and {@link SparseCloneTest})
 *
 */
public class FetchOpTest extends RemoteRepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    LinkedList<RevCommit> expectedMaster;

    LinkedList<RevCommit> expectedBranch;

    protected Repository originRepo, localRepo, upstreamRepo;

    protected Remote origin, upstream;

    private Optional<Ref> originMaster, originBranch1, originTag;

    private Optional<Ref> upstreamMaster, upstreamBranch1, upstreamTag;

    private Optional<Ref> localOriginMaster;

    @After
    public final void tearDownUpstream() throws Exception {
        upstreamGeogig.tearDown();
    }

    @Override
    protected void setUpInternal() throws Exception {

        localRepo = localGeogig.repo;
        originRepo = remoteGeogig.repo;
        upstreamRepo = upstreamGeogig.repo;

        // clone the repository
        CloneOp clone = cloneOp();
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).setCloneURI(localGeogig.envHome.toURI())
                .call();

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

        upstreamRepo.command(CloneOp.class).setRemoteURI(originRepo.getLocation())
                .setRemoteName("origin").call();

        upstream = localRepo.command(RemoteAddOp.class).setName("upstream")
                .setURL(upstreamRepo.getLocation().toString()).call();

        localGeogig.addRemoteOverride(upstream, upstreamRepo);

        origin = localRepo.command(RemoteResolve.class).setName(REMOTE_NAME).call().get();

        originMaster = Optional.of(toRemote(origin, getRef(originRepo, "master").get()));
        originBranch1 = Optional.of(toRemote(origin, getRef(originRepo, "Branch1").get()));
        originTag = Optional.of(toRemote(origin, getRef(originRepo, "test").get()));

        upstreamMaster = Optional.of(toRemote(upstream, getRef(upstreamRepo, "master").get()));
        upstreamBranch1 = Optional.of(toRemote(upstream, getRef(upstreamRepo, "Branch1").get()));
        upstreamTag = Optional.of(toRemote(upstream, getRef(upstreamRepo, "test").get()));

        localOriginMaster = getRef(localRepo, "refs/remotes/origin/master");
    }

    private Ref toRemote(Remote remote, Ref local) {
        return localRepo.command(MapRef.class).setRemote(remote).add(local).convertToRemote().call()
                .get(0);
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

        TestSupport.verifyRepositoryContents(localGeogig.geogig.getRepository());
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
    public void testFetchNoArgsDefaultsToOrigin() throws Exception {
        // fetch from the remote
        FetchOp fetch = fetchOp();

        TransferSummary summary = fetch.call();
        assertNotNull(summary);
        assertEquals(1, summary.getRefDiffs().size());
        assertTrue(summary.getRefDiffs().containsKey(origin.getFetchURL()));
        assertSummary(summary, origin.getFetchURL(), localOriginMaster, originMaster);
        assertSummary(summary, origin.getFetchURL(), absent(), originBranch1);
        assertSummary(summary, origin.getFetchURL(), absent(), originTag);
        verifyFetch();
    }

    @Test
    public void testFetchAll() throws Exception {
        // fetch from the remote
        FetchOp fetch = fetchOp();
        TransferSummary summary = fetch.setAll(true).call();
        assertEquals(2, summary.getRefDiffs().size());
        assertTrue(summary.getRefDiffs().containsKey(origin.getFetchURL()));
        assertTrue(summary.getRefDiffs().containsKey(upstream.getFetchURL()));

        assertSummary(summary, origin.getFetchURL(), localOriginMaster, originMaster);
        assertSummary(summary, origin.getFetchURL(), absent(), originBranch1);
        assertSummary(summary, origin.getFetchURL(), absent(), originTag);

        assertSummary(summary, upstream.getFetchURL(), absent(), upstreamMaster);
        assertSummary(summary, upstream.getFetchURL(), absent(), upstreamBranch1);

        verifyFetch();
    }

    @Test
    public void testFetchSingleRef() throws Exception {
        final Repository remote = this.originRepo;
        final Repository local = this.localRepo;

        remote.command(BranchCreateOp.class).setName("branch2").setAutoCheckout(true).call();
        insertAndAdd(remote, lines1_modified, lines2_modified, lines3_modified);
        RevCommit branch2Tip = commit(remote, "modified lines on branch2");
        checkout(remote, "master");

        remote.command(BranchCreateOp.class).setName("branch3").setAutoCheckout(true).call();
        insertAndAdd(remote, points1_modified, points2_modified, points3_modified);
        RevCommit branch3Tip = commit(remote, "modified points on branch3");
        checkout(remote, "master");

        Optional<Ref> originBranch2 = Optional
                .of(toRemote(origin, getRef(remote, "branch2").get()));

        // fetch from the remote
        FetchOp fetch = fetchOp().setAutofetchTags(false);

        Remote singleRefOrigin = origin.fetch("refs/heads/branch2");

        TransferSummary summary = fetch.addRemote(singleRefOrigin).call();

        assertFalse(getRef(local, "refs/heads/branch2").isPresent());
        assertTrue(getRef(local, "refs/remotes/origin/branch2").isPresent());
        assertFalse(getRef(local, "branch3").isPresent());
        assertEquals(branch2Tip.getId(),
                getRef(local, "refs/remotes/origin/branch2").get().getObjectId());

        assertEquals(1, summary.getRefDiffs().size());
        assertTrue(summary.getRefDiffs().containsKey(origin.getFetchURL()));
        assertEquals(1, summary.getRefDiffs().get(origin.getFetchURL()).size());
        assertSummary(summary, origin.getFetchURL(), absent(), originBranch2);
        TestSupport.verifyRepositoryContents(local, "refs/remotes/origin/branch2");

    }

    public @Test void testFetchRespectsTargeRef() throws Exception {
        final Repository remote = this.originRepo;
        final Repository local = this.localRepo;

        remote.command(BranchCreateOp.class).setName("branch2").setAutoCheckout(true).call();
        insertAndAdd(remote, lines1_modified, lines2_modified, lines3_modified);
        RevCommit branch2Tip = commit(remote, "modified lines on branch2");
        checkout(remote, "master");

        remote.command(BranchCreateOp.class).setName("branch3").setAutoCheckout(true).call();
        insertAndAdd(remote, points1_modified, points2_modified, points3_modified);
        RevCommit branch3Tip = commit(remote, "modified points on branch3");
        checkout(remote, "master");

        // fetch from the remote, fetching remote's branch2 to local's refs/custom/branch2 and
        // branch3 to refs/custom/branch3 explicitly
        FetchOp fetch = fetchOp().setAutofetchTags(false);
        final String refSpec = "refs/heads/branch2:refs/custom/branch2;refs/heads/branch3:refs/custom/branch3;";
        Remote singleRefOrigin = origin.fetch(refSpec);

        TransferSummary summary = fetch.addRemote(singleRefOrigin).call();

        assertFalse(getRef(local, "refs/heads/branch2").isPresent());
        assertFalse(getRef(local, "refs/heads/branch3").isPresent());
        assertFalse(getRef(local, "refs/remotes/origin/branch2").isPresent());
        assertFalse(getRef(local, "refs/remotes/origin/branch3").isPresent());

        assertTrue(getRef(local, "refs/custom/branch2").isPresent());
        assertTrue(getRef(local, "refs/custom/branch2").isPresent());
        assertEquals(branch2Tip.getId(), getRef(local, "refs/custom/branch2").get().getObjectId());
        assertEquals(branch3Tip.getId(), getRef(local, "refs/custom/branch3").get().getObjectId());

        assertEquals(1, summary.getRefDiffs().size());
        assertTrue(summary.getRefDiffs().containsKey(origin.getFetchURL()));
        assertEquals(2, summary.getRefDiffs().get(origin.getFetchURL()).size());
        TestSupport.verifyRepositoryContents(local, "refs/custom/branch2", "refs/custom/branch3");
    }

    @Test
    public void testFetchSpecificRemote() throws Exception {
        // fetch from the remote
        FetchOp fetch = fetchOp();
        TransferSummary summary = fetch.addRemote("upstream").call();
        assertEquals(1, summary.getRefDiffs().size());
        assertTrue(summary.getRefDiffs().containsKey(upstream.getFetchURL()));

        assertSummary(summary, upstream.getFetchURL(), absent(), upstreamMaster);
        assertSummary(summary, upstream.getFetchURL(), absent(), upstreamTag);
        assertSummary(summary, upstream.getFetchURL(), absent(), upstreamBranch1);

        TestSupport.verifySameContents(upstreamRepo, localRepo);
    }

    @Test
    public void testFetchSpecificRemoteAndAll() throws Exception {
        // fetch from the remote
        FetchOp fetch = fetchOp();
        TransferSummary summary = fetch.addRemote("upstream").setAllRemotes(true).call();

        assertEquals(2, summary.getRefDiffs().size());
        assertTrue(summary.getRefDiffs().containsKey(origin.getFetchURL()));
        assertTrue(summary.getRefDiffs().containsKey(upstream.getFetchURL()));

        assertSummary(summary, origin.getFetchURL(), localOriginMaster, originMaster);
        assertSummary(summary, origin.getFetchURL(), absent(), originBranch1);
        assertSummary(summary, origin.getFetchURL(), absent(), originTag);

        assertSummary(summary, upstream.getFetchURL(), absent(), upstreamMaster);
        assertSummary(summary, upstream.getFetchURL(), absent(), upstreamBranch1);

        verifyFetch();
    }

    @Test
    public void testFetchNoRemotes() throws Exception {
        localGeogig.geogig.command(RemoteRemoveOp.class).setName(REMOTE_NAME).call();
        FetchOp fetch = fetchOp();
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Remote could not be resolved");
        fetch.call();
    }

    @Test
    public void testFetchNoChanges() throws Exception {
        // fetch from the remote
        FetchOp fetch = fetchOp();
        TransferSummary summary = fetch.addRemote("origin").call();
        assertEquals(1, summary.getRefDiffs().size());
        assertTrue(summary.getRefDiffs().containsKey(origin.getFetchURL()));
        assertSummary(summary, origin.getFetchURL(), localOriginMaster, originMaster);
        assertSummary(summary, origin.getFetchURL(), absent(), originBranch1);
        assertSummary(summary, origin.getFetchURL(), absent(), originTag);
        verifyFetch();

        // fetch again
        summary = fetch.call();
        assertTrue(summary.toString(), summary.isEmpty());
    }

    @Test
    public void testFetchWithPrune() throws Exception {
        // fetch from the remote
        FetchOp fetch = fetchOp();
        fetch.addRemote("origin").setAllRemotes(true).call();

        verifyFetch();
        Optional<Ref> localOriginBranch1 = getRef(localRepo, "refs/remotes/origin/Branch1");
        assertTrue(localOriginBranch1.isPresent());

        // Remove a branch from the remote
        remoteGeogig.geogig.command(BranchDeleteOp.class).setName("Branch1").call();

        // fetch again
        fetch = fetchOp();
        TransferSummary summary = fetch.setPrune(true).call();
        assertSummary(summary, origin.getFetchURL(), localOriginBranch1, absent());

        verifyPrune();
    }

    @Test
    public void testFetchWithPruneAndBranchAdded() throws Exception {
        // fetch from the remote
        FetchOp fetch = fetchOp();
        fetch.addRemote("origin").setAllRemotes(true).call();

        verifyFetch();

        Optional<Ref> localOriginBranch1 = getRef(localRepo, "refs/remotes/origin/Branch1");
        assertTrue(localOriginBranch1.isPresent());

        // Remove a branch from the remote
        remoteGeogig.geogig.command(BranchDeleteOp.class).setName("Branch1").call();

        // Add another branch
        Ref branch2 = remoteGeogig.geogig.command(BranchCreateOp.class).setName("Branch2").call();

        // fetch again
        fetch = fetchOp();
        TransferSummary summary = fetch.setPrune(true).call();
        assertEquals(1, summary.getRefDiffs().size());
        assertTrue(summary.getRefDiffs().containsKey(origin.getFetchURL()));
        assertSummary(summary, origin.getFetchURL(), localOriginBranch1, absent());

        Ref expectedNew = toRemote(origin, branch2);
        assertSummary(summary, origin.getFetchURL(), null, expectedNew);

        verifyPrune();

        Optional<Ref> pruned = getRef(localRepo, "refs/remotes/origin/Branch1");
        assertFalse(pruned.isPresent());
        Optional<Ref> missing = getRef(localRepo, "refs/remotes/origin/Branch2");
        assertTrue(missing.isPresent());
    }
}
