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
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.BranchDeleteOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CloneOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.LogOp;
import org.locationtech.geogig.api.porcelain.MergeOp;
import org.locationtech.geogig.api.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.api.porcelain.TagCreateOp;
import org.locationtech.geogig.api.porcelain.TagListOp;
import org.locationtech.geogig.remote.RemoteRepositoryTestCase;
import org.opengis.feature.Feature;

import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class CloneOpTest extends RemoteRepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Test
    public void testClone() throws Exception {
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
        CloneOp clone = clone();
        clone.setDepth(0);
        clone.setRepositoryURL(remoteGeogig.envHome.getCanonicalPath()).call();

        // Make sure the local repository got all of the commits
        logs = localGeogig.geogig.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expected, logged);
    }

    @Test
    public void testCloneWithTags() throws Exception {
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();
        List<RevTag> tags = Lists.newArrayList();

        for (Feature f : features) {
            ObjectId oId = insertAndAdd(remoteGeogig.geogig, f);
            final RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
            expected.addFirst(commit);
            Optional<RevObject> childObject = remoteGeogig.geogig.command(RevObjectParse.class)
                    .setObjectId(oId).call();
            assertTrue(childObject.isPresent());
            RevTag tag = remoteGeogig.geogig.command(TagCreateOp.class).setCommitId(commit.getId())
                    .setName(f.getIdentifier().getID()).call();
            tags.add(tag);
        }

        // Make sure the remote has all of the commits
        Iterator<RevCommit> logs = remoteGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expected, logged);

        // Make sure the remote has all of the tags
        ImmutableList<RevTag> remoteTags = remoteGeogig.geogig.command(TagListOp.class).call();
        assertEquals(tags.size(), remoteTags.size());
        for (RevTag tag : tags) {
            assertTrue(remoteTags.contains(tag));
        }

        // Make sure the local repository has no commits prior to clone
        logs = localGeogig.geogig.command(LogOp.class).call();
        assertNotNull(logs);
        assertFalse(logs.hasNext());

        // clone from the remote
        CloneOp clone = clone();
        clone.setDepth(0);
        clone.setRepositoryURL(remoteGeogig.envHome.getCanonicalPath()).call();

        // Make sure the local repository got all of the commits
        logs = localGeogig.geogig.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expected, logged);

        /*
         * This is commented out, since the clone operation does not clone tags yet This test
         * verifies that no errors are raised when the repo to clone contains tags, but not to
         * verify that tags are also cloned, since that is not supported
         * 
         * I leave this dommented code here, to uncomment it once tag support is implemented for the
         * clone operation
         * 
         * 
         * // Make sure the local repository got all of the tags
         * 
         * ImmutableList<RevTag> localTags = localGeogig.geogig.command(TagListOp.class).call();
         * 
         * assertEquals(tags.size(), localTags.size());
         * 
         * for (RevTag tag : tags) {
         * 
         * assertTrue(localTags.contains(tag));
         * 
         * }
         */

    }

    @Test
    public void testCloneWithMergeCommit() throws Exception {
        // Commit several features to the remote

        LinkedList<RevCommit> expectedMaster = new LinkedList<RevCommit>();
        LinkedList<RevCommit> expectedBranch = new LinkedList<RevCommit>();

        insertAndAdd(remoteGeogig.geogig, points1);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);
        expectedBranch.addFirst(commit);

        insertAndAdd(remoteGeogig.geogig, points1_modified);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);
        expectedBranch.addFirst(commit);

        // Create and checkout branch1
        remoteGeogig.geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("Branch1")
                .call();

        // Commit a change to branch1
        insertAndAdd(remoteGeogig.geogig, points2);
        RevCommit branch1commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedBranch.addFirst(branch1commit);

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

        // Merge branch1 into master
        MergeReport report = remoteGeogig.geogig.command(MergeOp.class)
                .addCommit(Suppliers.ofInstance(branch1commit.getId())).call();

        expectedMaster.addFirst(report.getMergeCommit());

        // Delete Branch1

        // Create and checkout branch1
        remoteGeogig.geogig.command(BranchDeleteOp.class).setName("Branch1").call();

        // clone from the remote
        CloneOp clone = clone();
        clone.setRepositoryURL(remoteGeogig.envHome.getCanonicalPath()).setBranch("master").call();

        // Make sure the local repository got all of the commits
        logs = localGeogig.geogig.command(LogOp.class).setFirstParentOnly(true).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMaster, logged);
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
        CloneOp clone = clone();
        clone.setDepth(2);
        clone.setRepositoryURL(remoteGeogig.envHome.getCanonicalPath()).call();

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

    @Test
    public void testCloneRepoWithBranches() throws Exception {
        // Commit several features to the remote

        LinkedList<RevCommit> expectedMaster = new LinkedList<RevCommit>();
        LinkedList<RevCommit> expectedBranch = new LinkedList<RevCommit>();

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
    public void testCloneEmptyRepo() throws Exception {
        CloneOp clone = clone();
        clone.setRepositoryURL(remoteGeogig.envHome.getCanonicalPath()).call();
    }

    @Test
    public void testCloneNoRepoSpecified() throws Exception {
        CloneOp clone = clone();
        exception.expect(IllegalArgumentException.class);
        clone.call();
    }

    @Test
    public void testCloneEmptyRepoString() throws Exception {
        CloneOp clone = clone();
        exception.expect(IllegalArgumentException.class);
        clone.setRepositoryURL("").call();
    }

}
