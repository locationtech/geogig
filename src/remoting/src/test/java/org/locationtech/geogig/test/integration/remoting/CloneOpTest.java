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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;

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
import org.locationtech.geogig.porcelain.BranchConfig;
import org.locationtech.geogig.porcelain.BranchConfigOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.BranchDeleteOp;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.porcelain.ResetOp;
import org.locationtech.geogig.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.porcelain.TagCreateOp;
import org.locationtech.geogig.porcelain.TagListOp;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.test.TestSupport;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * {@link CloneOp} integration test suite for full clones (for shallow and sparse clones see
 * {@link ShallowCloneTest} and {@link SparseCloneTest})
 *
 */
public class CloneOpTest extends RemoteRepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected Repository remoteRepo;

    protected Repository cloneRepo;

    @Override
    protected void setUpInternal() throws Exception {
        remoteRepo = remoteGeogig.repo;
        cloneRepo = localGeogig.repo;
    }

    @Test
    public void testClone() throws Exception {
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();

        for (Feature f : features) {
            ObjectId oId = insertAndAdd(remoteRepo, f);
            final RevCommit commit = remoteRepo.command(CommitOp.class)
                    .setMessage("commit of " + f.getIdentifier()).call();
            expected.addFirst(commit);
            Optional<RevObject> childObject = remoteRepo.command(RevObjectParse.class)
                    .setObjectId(oId).call();
            assertTrue(childObject.isPresent());
        }

        // Make sure the remote has all of the commits
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expected, logged);

        // Make sure the local repository has no commits prior to clone
        logged = newArrayList(cloneRepo.command(LogOp.class).call());
        assertEquals(0, logged.size());

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setDepth(0);
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).setCloneURI(localGeogig.envHome.toURI())
                .call();

        // Make sure the local repository got all of the commits
        logged = newArrayList(cloneRepo.command(LogOp.class).call());

        assertEquals(expected, logged);
        TestSupport.verifySameRefs(remoteRepo, cloneRepo);
        TestSupport.verifySameContents(remoteRepo, cloneRepo);
        verifyBranchConfig(remoteRepo, cloneRepo);
    }

    private void verifyBranchConfig(Repository remote, Repository clone) {
        ImmutableList<Ref> remoteBranches = remote.command(BranchListOp.class).call();
        for (Ref remoteBranch : remoteBranches) {
            BranchConfig config = clone.command(BranchConfigOp.class)
                    .setName(remoteBranch.localName()).get();
            assertEquals(remoteBranch.localName(), config.getBranch().localName());
            assertEquals("origin", config.getRemoteName().get());
            assertEquals(remoteBranch.getName(), config.getRemoteBranch().get());
        }
    }

    @Test
    public void testCloneLargerTreeSingleCommit() throws Exception {
        final int nfeatures = 10_000;
        List<SimpleFeature> features = createPointFeatures(nfeatures);
        insert(remoteRepo, features);
        add(remoteRepo);

        remoteRepo.command(CommitOp.class).setMessage("single commit").call();

        // clone from the remote
        CloneOp clone = cloneOp();
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).setCloneURI(localGeogig.envHome.toURI())
                .call();
        TestSupport.verifySameRefs(remoteRepo, cloneRepo);
        TestSupport.verifySameContents(remoteRepo, cloneRepo);
    }

    @Test
    public void testCloneComplexHistory() throws Exception {
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);

        for (Feature f : features) {
            insertAndAdd(remoteRepo, f);
        }

        remoteRepo.command(CommitOp.class).setMessage("initial commit").call();

        createBranch(remoteRepo, "branch1");
        checkout(remoteRepo, "master");

        insertAndAdd(remoteRepo, points1_modified);
        commit(remoteRepo, "left modify 1");

        createBranch(remoteRepo, "intermediate_left");
        checkout(remoteRepo, "branch1");

        insertAndAdd(remoteRepo, points2_modified);
        commit(remoteRepo, "right modify 1");

        checkout(remoteRepo, "intermediate_left");

        mergeNoFF(remoteRepo, "branch1", "merge 1", true);

        createBranch(remoteRepo, "intermediate_right");
        checkout(remoteRepo, "master");

        insertAndAdd(remoteRepo, points3_modified);
        commit(remoteRepo, "left modify 2");

        checkout(remoteRepo, "intermediate_left");

        MergeReport merge2_left = mergeNoFF(remoteRepo, "master", "merge 2 left", true);

        checkout(remoteRepo, "master");
        remoteRepo.command(ResetOp.class).setMode(ResetMode.HARD)
                .setCommit(Suppliers.ofInstance(merge2_left.getMergeCommit().getId())).call();

        checkout(remoteRepo, "branch1");

        insertAndAdd(remoteRepo, lines1_modified);
        commit(remoteRepo, "right modify 2");

        checkout(remoteRepo, "intermediate_right");

        MergeReport merge2_right = mergeNoFF(remoteRepo, "branch1", "merge 2 right", true);

        checkout(remoteRepo, "branch1");
        remoteRepo.command(ResetOp.class).setMode(ResetMode.HARD)
                .setCommit(Suppliers.ofInstance(merge2_right.getMergeCommit().getId())).call();

        checkout(remoteRepo, "master");

        mergeNoFF(remoteRepo, "branch1", "final merge", true);

        // Make sure the local repository has no commits prior to clone
        List<RevCommit> logged = newArrayList(cloneRepo.command(LogOp.class).call());
        assertEquals(0, logged.size());

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setDepth(0);
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).setCloneURI(localGeogig.envHome.toURI())
                .call();

        // Make sure the local repository got all of the commits
        logged = newArrayList(cloneRepo.command(LogOp.class).call());
        List<RevCommit> expected = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expected, logged);
        TestSupport.verifySameRefs(remoteRepo, cloneRepo);
        TestSupport.verifySameContents(remoteRepo, cloneRepo);
    }

    @Test
    public void testCloneLargerTreeSeveralCommits() throws Exception {
        final int nfeatures = 10_000;
        List<SimpleFeature> features = createPointFeatures(nfeatures);

        final int featuresPerCommit = 100;
        List<RevCommit> commits = insertAndCommit(features, featuresPerCommit);

        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());
        assertEquals(commits, logged);

        // clone from the remote
        CloneOp clone = cloneOp();
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).setCloneURI(localGeogig.envHome.toURI())
                .call();

        logged = newArrayList(cloneRepo.command(LogOp.class).call());
        assertEquals(commits, logged);

        TestSupport.verifySameContents(remoteRepo, cloneRepo);
    }

    @Test
    public void testCloneLargerTreeSeveralCommitsAndChangeTypes() throws Exception {
        final int nfeatures = 1_000;
        List<SimpleFeature> features = createPointFeatures(nfeatures);
        // inserts
        {
            final int featuresPerCommit = 100;
            insertAndCommit(features, featuresPerCommit);
        }
        // updates
        {
            createBranch(remoteRepo, "updates_branch");

            final int featuresPerCommit = 10;
            List<SimpleFeature> updates = features.subList(0, nfeatures / 2);
            updates.forEach((f) -> f.setAttribute("sp", f.getAttribute("sp") + " changed"));
            insertAndCommit(updates, featuresPerCommit);

            checkout(remoteRepo, "master");
            createBranch(remoteRepo, "updates_branch2");

            updates.forEach((f) -> f.setAttribute("sp", f.getAttribute("sp") + " changed too"));
            insertAndCommit(updates, featuresPerCommit);
        }

        createBranch(remoteRepo, "deletes_branch");
        // delete every other feauture
        {
            final String parent = pointsName + "/";
            List<String> featurePaths = new ArrayList<>();
            for (int i = 0; i < features.size(); i += 2) {
                featurePaths.add(parent + features.get(i).getID());
            }
            remoteRepo.workingTree().delete(featurePaths.iterator(), new DefaultProgressListener());
            add(remoteRepo);
            remoteRepo.command(CommitOp.class).setMessage("several deletes")
                    .setProgressListener(SIMPLE_PROGRESS).call();
        }

        checkout(remoteRepo, "master");
        mergeNoFF(remoteRepo, "updates_branch", "merge branch updates_branch onto master", true);
        mergeNoFF(remoteRepo, "deletes_branch", "merge branch deletes_branch onto master", true);

        // clone from the remote
        CloneOp clone = cloneOp();
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI())//
                .setCloneURI(localGeogig.envHome.toURI())//
                .setProgressListener(SIMPLE_PROGRESS)//
                .call();

        TestSupport.verifySameRefs(remoteRepo, cloneRepo);
        TestSupport.verifySameContents(remoteRepo, cloneRepo);
    }

    @Test
    public void testCloneExpandedTree() throws Exception {
        final int nfeatures = 1_000;
        List<SimpleFeature> features = createPointFeatures(nfeatures);
        RevCommit leafTreeCommit, bucketTreeCommit;
        {
            List<SimpleFeature> leafTreeNodes = features.subList(0, 512);
            leafTreeCommit = insertAndCommit(leafTreeNodes, leafTreeNodes.size()).get(0);
            bucketTreeCommit = insertAndCommit(features, features.size()).get(0);
        }

        // clone from the remote
        CloneOp clone = cloneOp();
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI())//
                .setCloneURI(localGeogig.envHome.toURI())//
                .setProgressListener(SIMPLE_PROGRESS)//
                .call();

        TestSupport.verifySameRefs(remoteRepo, cloneRepo);
        TestSupport.verifySameContents(remoteRepo, cloneRepo);
    }

    @Test
    public void testCloneCollapsedTree() throws Exception {
        final int nfeatures = 1_000;
        List<SimpleFeature> features = createPointFeatures(nfeatures);
        RevCommit leafTreeCommit, bucketTreeCommit;
        {
            bucketTreeCommit = insertAndCommit(features, features.size()).get(0);
            List<SimpleFeature> removeNodes = features.subList(512, features.size());
            super.delete(remoteRepo, removeNodes);
            super.add(remoteRepo);
            String msg = "Deleted features " + removeNodes.get(0).getID() + " to "
                    + removeNodes.get(removeNodes.size() - 1).getID();
            leafTreeCommit = super.commit(remoteRepo, msg);
        }

        // clone from the remote
        CloneOp clone = cloneOp();
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI())//
                .setCloneURI(localGeogig.envHome.toURI())//
                .setProgressListener(SIMPLE_PROGRESS)//
                .call();

        TestSupport.verifySameRefs(remoteRepo, cloneRepo);
        TestSupport.verifySameContents(remoteRepo, cloneRepo);
    }

    private List<RevCommit> insertAndCommit(List<? extends Feature> features,
            final int featuresPerCommit) throws Exception {
        List<RevCommit> commits = new LinkedList<>();
        int i = 0;
        for (List<? extends Feature> partition : Iterables.partition(features, featuresPerCommit)) {
            insert(remoteRepo, partition);
            add(remoteRepo);
            String from = partition.get(0).getIdentifier().toString();
            String to = partition.get(partition.size() - 1).getIdentifier().toString();
            String message = String.format("commit features %s to %s", from, to);
            RevCommit commit = remoteRepo.command(CommitOp.class).setMessage(message)
                    .setProgressListener(SIMPLE_PROGRESS).call();
            commits.add(0, commit);
            i++;
        }
        return commits;
    }

    private List<SimpleFeature> createPointFeatures(final int nfeatures) {
        List<SimpleFeature> features;

        SimpleFeatureType type = super.pointsType;
        features = new ArrayList<>();
        IntStream.range(0, nfeatures).forEach((index) -> {
            String wkt = String.format("POINT(-0.%s 0.%s)", index, index);
            String fid = String.valueOf(index);
            Object[] values = { fid, Integer.valueOf(index), wkt };
            SimpleFeature feature = (SimpleFeature) super.feature(type, fid, values);
            features.add(feature);
        });

        return features;
    }

    @Test
    public void testCloneWithTags() throws Exception {
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();
        List<RevTag> tags = Lists.newArrayList();

        for (Feature f : features) {
            ObjectId oId = insertAndAdd(remoteRepo, f);
            final RevCommit commit = remoteRepo.command(CommitOp.class).call();
            expected.addFirst(commit);
            Optional<RevObject> childObject = remoteRepo.command(RevObjectParse.class)
                    .setObjectId(oId).call();
            assertTrue(childObject.isPresent());
            RevTag tag = remoteRepo.command(TagCreateOp.class).setCommitId(commit.getId())
                    .setName(f.getIdentifier().getID()).call();
            tags.add(tag);
        }

        // Make sure the remote has all of the commits
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expected, logged);

        // Make sure the remote has all of the tags
        ImmutableList<RevTag> remoteTags = remoteRepo.command(TagListOp.class).call();
        assertEquals(tags.size(), remoteTags.size());
        for (RevTag tag : tags) {
            assertTrue(remoteTags.contains(tag));
        }

        // Make sure the local repository has no commits prior to clone
        logged = newArrayList(cloneRepo.command(LogOp.class).call());
        assertEquals(0, logged.size());

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setDepth(0);
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).setCloneURI(localGeogig.envHome.toURI())
                .call();
        TestSupport.verifySameRefs(remoteRepo, cloneRepo);
        TestSupport.verifySameContents(remoteRepo, cloneRepo);

        // Make sure the local repository got all of the commits
        logged = newArrayList(cloneRepo.command(LogOp.class).call());

        assertEquals(expected, logged);

        // Make sure the local repository got all of the tags

        ImmutableList<RevTag> localTags = cloneRepo.command(TagListOp.class).call();

        assertEquals(tags.size(), localTags.size());

        for (RevTag tag : tags) {
            assertTrue(localTags.contains(tag));
        }
        TestSupport.verifyRepositoryContents(cloneRepo);
    }

    @Test
    public void testCloneWithMergeCommit() throws Exception {
        // Commit several features to the remote

        LinkedList<RevCommit> expectedMaster = new LinkedList<RevCommit>();
        LinkedList<RevCommit> expectedBranch = new LinkedList<RevCommit>();

        insertAndAdd(remoteRepo, points1);
        RevCommit commit = remoteRepo.command(CommitOp.class).setMessage("commit 1").call();
        expectedMaster.addFirst(commit);
        expectedBranch.addFirst(commit);

        insertAndAdd(remoteRepo, points1_modified);
        commit = remoteRepo.command(CommitOp.class).setMessage("commit 2").call();
        expectedMaster.addFirst(commit);
        expectedBranch.addFirst(commit);

        // Create and checkout branch1
        remoteRepo.command(BranchCreateOp.class).setAutoCheckout(true).setName("Branch1").call();

        // Commit a change to branch1
        insertAndAdd(remoteRepo, points2);
        RevCommit branch1commit = remoteRepo.command(CommitOp.class).setMessage("commit 3").call();
        expectedBranch.addFirst(branch1commit);

        // Make sure Branch1 has all of the commits
        List<RevCommit> logged = newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expectedBranch, logged);

        // Checkout master and commit some changes
        remoteRepo.command(CheckoutOp.class).setSource("master").call();

        insertAndAdd(remoteRepo, lines1);
        commit = remoteRepo.command(CommitOp.class).setMessage("commit 4").call();
        expectedMaster.addFirst(commit);

        insertAndAdd(remoteRepo, lines2);
        commit = remoteRepo.command(CommitOp.class).setMessage("commit 5").call();
        expectedMaster.addFirst(commit);

        // Make sure master has all of the commits
        logged = newArrayList(remoteRepo.command(LogOp.class).call());
        assertEquals(expectedMaster, logged);

        // Merge branch1 into master
        MergeReport report = remoteRepo.command(MergeOp.class).addCommit(branch1commit.getId())
                .call();

        expectedMaster.addFirst(report.getMergeCommit());

        // Delete Branch1

        // Delete branch1
        remoteRepo.command(BranchDeleteOp.class).setName("Branch1").call();

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setRemoteURI(remoteGeogig.envHome.toURI())//
                .setCloneURI(localGeogig.envHome.toURI())//
                .setBranch("master").call();

        TestSupport.verifySameRefs(remoteRepo, cloneRepo);
        TestSupport.verifySameContents(remoteRepo, cloneRepo);

        // Make sure the local repository got all of the commits
        logged = newArrayList(cloneRepo.command(LogOp.class).setFirstParentOnly(true).call());

        assertEquals(expectedMaster, logged);
    }

    @Test
    public void testCloneRepoWithBranches() throws Exception {
        // Commit several features to the remote

        LinkedList<RevCommit> expectedMaster = new LinkedList<RevCommit>();
        LinkedList<RevCommit> expectedBranch = new LinkedList<RevCommit>();

        insertAndAdd(remoteRepo, points1);
        RevCommit commit = remoteRepo.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);
        expectedBranch.addFirst(commit);

        // Create and checkout branch1
        remoteRepo.command(BranchCreateOp.class).setAutoCheckout(true).setName("Branch1").call();
        assertEquals("Branch1",
                remoteRepo.command(RefParse.class).setName("HEAD").call().get().peel().localName());
        // Commit some changes to branch1
        insertAndAdd(remoteRepo, points2);
        commit = remoteRepo.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        insertAndAdd(remoteRepo, points3);
        commit = remoteRepo.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        // Make sure Branch1 has all of the commits
        List<RevCommit> logged = Lists.newArrayList(remoteRepo.command(LogOp.class).call());

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
        logged = Lists.newArrayList(remoteRepo.command(LogOp.class).call());

        assertEquals(expectedMaster, logged);

        // Make sure the local repository has no commits prior to clone
        logged = Lists.newArrayList(cloneRepo.command(LogOp.class).call());
        assertEquals(0, logged.size());

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setRemoteURI(remoteGeogig.envHome.toURI())//
                .setCloneURI(localGeogig.envHome.toURI())//
                .setBranch("Branch1")//
                .call();
        // TestSupport.verifySameContents(remoteRepo, cloneRepo);
        // Make sure the specified branch was checked out on the new clone
        assertEquals("Branch1",
                cloneRepo.command(RefParse.class).setName("HEAD").call().get().peel().localName());

        // Make sure the local repository got all of the commits
        logged = Lists.newArrayList(cloneRepo.command(LogOp.class).call());

        assertEquals(expectedBranch, logged);

        // Make sure the local master matches the remote
        cloneRepo.command(CheckoutOp.class).setSource("master").call();

        logged = Lists.newArrayList(cloneRepo.command(LogOp.class).call());

        assertEquals(expectedMaster, logged);
        TestSupport.verifyRepositoryContents(cloneRepo);
    }

    @Test
    public void testCloneEmptyRepo() throws Exception {
        CloneOp clone = cloneOp();
        // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).setCloneURI(localGeogig.envHome.toURI())
                .call();
        TestSupport.verifyRepositoryContents(cloneRepo);
    }

    @Test
    public void testCloneNoRepoSpecified() throws Exception {
        CloneOp clone = cloneOp();
        exception.expect(IllegalArgumentException.class);
        clone.call();
    }

    @Test
    public void testCloneEmptyRepoString() throws Exception {
        CloneOp clone = cloneOp();
        exception.expect(IllegalArgumentException.class);
        // clone.setRepositoryURL("").call();
        clone.setRemoteURI(null).call();
    }

}
