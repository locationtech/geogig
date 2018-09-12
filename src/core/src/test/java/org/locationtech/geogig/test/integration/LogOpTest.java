/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import static com.google.common.collect.Lists.newArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.geotools.util.Range;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.porcelain.ResetOp;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.ProgressListener;
import org.opengis.feature.Feature;

import com.google.common.base.Suppliers;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

public class LogOpTest extends RepositoryTestCase {

    private LogOp logOp;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        logOp = geogig.command(LogOp.class);
    }

    @Test
    public void testComplex() throws Exception {
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);

        for (Feature f : features) {
            insertAndAdd(f);
        }

        geogig.command(CommitOp.class).setMessage("initial commit").call();

        createBranch("branch1");
        checkout("master");

        insertAndAdd(points1_modified);
        commit("left modify 1");

        createBranch("intermediate_left");
        checkout("branch1");

        insertAndAdd(points2_modified);
        commit("right modify 1");

        checkout("intermediate_left");

        mergeNoFF("branch1", "merge 1", true);

        createBranch("intermediate_right");
        checkout("master");

        insertAndAdd(points3_modified);
        commit("left modify 2");

        checkout("intermediate_left");

        MergeReport merge2_left = mergeNoFF("master", "merge 2 left", true);

        checkout("master");
        geogig.command(ResetOp.class).setMode(ResetOp.ResetMode.HARD)
                .setCommit(Suppliers.ofInstance(merge2_left.getMergeCommit().getId())).call();

        checkout("branch1");

        insertAndAdd(lines1_modified);
        commit("right modify 2");

        checkout("intermediate_right");

        MergeReport merge2_right = mergeNoFF("branch1", "merge 2 right", true);

        checkout("branch1");
        geogig.command(ResetOp.class).setMode(ResetOp.ResetMode.HARD)
                .setCommit(Suppliers.ofInstance(merge2_right.getMergeCommit().getId())).call();

        checkout("master");

        mergeNoFF("branch1", "final merge", true);

        // both arrays should have 9 elements and contain the same commits (in different order)
        List<RevCommit> log_topo = newArrayList(
                geogig.command(LogOp.class).setTopoOrder(true).call());

        assertEquals(log_topo.size(), 9);

        List<RevCommit> log_chrono = newArrayList(
                geogig.command(LogOp.class).setTopoOrder(false).call());

        assertEquals(log_chrono.size(), 9);

        List<ObjectId> log_topo_ids = log_topo.stream().map(c -> c.getId()).sorted().collect(Collectors.toList());
        List<ObjectId> log_chrono_ids = log_chrono.stream().map(c -> c.getId()).sorted().collect(Collectors.toList());

        assertTrue(log_topo_ids.equals(log_chrono_ids));
    }

    protected static final ProgressListener SIMPLE_PROGRESS = new DefaultProgressListener() {
        public @Override
        void setDescription(String msg, Object... args) {
            System.err.printf(msg+"\n", args);
        }
    };

    protected void createBranch(String branch) {
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName(branch)
                .setProgressListener(SIMPLE_PROGRESS).call();
    }

    protected MergeReport mergeNoFF(String branch, String mergeMessage,
                                    boolean mergeOurs) {
        Ref branchRef = geogig.command(RefParse.class).setName(branch).call().get();
        ObjectId updatesBranchTip = branchRef.getObjectId();
        MergeReport mergeReport = geogig.command(MergeOp.class)//
                .setMessage(mergeMessage)//
                .setNoFastForward(true)//
                .addCommit(updatesBranchTip)//
                .setOurs(mergeOurs)//
                .setTheirs(!mergeOurs)//
                .setProgressListener(SIMPLE_PROGRESS)//
                .call();
        return mergeReport;
    }


    @Test
    public void testEmptyRepo() throws Exception {
        Iterator<RevCommit> logs = logOp.call();
        assertNotNull(logs);
        assertFalse(logs.hasNext());
    }

    @Test
    public void testHeadWithSingleCommit() throws Exception {

        insertAndAdd(points1);
        final RevCommit firstCommit = geogig.command(CommitOp.class).call();

        Iterator<RevCommit> iterator = logOp.call();
        assertNotNull(iterator);

        assertTrue(iterator.hasNext());
        assertEquals(firstCommit, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testHeadWithTwoCommits() throws Exception {

        insertAndAdd(points1);
        final RevCommit firstCommit = geogig.command(CommitOp.class).call();

        insertAndAdd(lines1);
        final RevCommit secondCommit = geogig.command(CommitOp.class).call();

        Iterator<RevCommit> iterator = logOp.call();
        assertNotNull(iterator);

        assertTrue(iterator.hasNext());
        // by default returns most recent first
        assertEquals(secondCommit, iterator.next());

        assertTrue(iterator.hasNext());
        assertEquals(firstCommit, iterator.next());

        assertFalse(iterator.hasNext());
    }

    @Test
    public void testHeadWithMultipleCommits() throws Exception {

        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();

        for (Feature f : features) {
            insertAndAdd(f);
            final RevCommit commit = geogig.command(CommitOp.class).call();
            expected.addFirst(commit);
        }

        Iterator<RevCommit> logs = logOp.call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expected, logged);
    }

    @Test
    public void testPathFilterSingleFeature() throws Exception {

        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        List<RevCommit> allCommits = Lists.newArrayList();
        RevCommit expectedCommit = null;

        for (Feature f : features) {
            insertAndAdd(f);
            String id = f.getIdentifier().getID();
            final RevCommit commit = geogig.command(CommitOp.class).call();
            if (id.equals(lines1.getIdentifier().getID())) {
                expectedCommit = commit;
            }
            allCommits.add(commit);
        }

        String path = NodeRef.appendChild(linesName, lines1.getIdentifier().getID());

        List<RevCommit> feature2_1Commits = toList(logOp.addPath(path).call());
        assertEquals(1, feature2_1Commits.size());
        assertEquals(Collections.singletonList(expectedCommit), feature2_1Commits);
    }

    @Test
    public void testMultiplePaths() throws Exception {
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        List<RevCommit> allCommits = Lists.newArrayList();
        RevCommit expectedLineCommit = null;
        RevCommit expectedPointCommit = null;
        for (Feature f : features) {
            insertAndAdd(f);
            String id = f.getIdentifier().getID();
            final RevCommit commit = geogig.command(CommitOp.class).call();
            if (id.equals(lines1.getIdentifier().getID())) {
                expectedLineCommit = commit;
            } else if (id.equals(points1.getIdentifier().getID())) {
                expectedPointCommit = commit;
            }
            allCommits.add(commit);
        }

        String linesPath = NodeRef.appendChild(linesName, lines1.getIdentifier().getID());
        String pointsPath = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());

        List<RevCommit> feature2_1Commits = toList(logOp.addPath(linesPath).call());
        List<RevCommit> featureCommits = toList(logOp.addPath(pointsPath).call());

        assertEquals(1, feature2_1Commits.size());
        assertEquals(2, featureCommits.size());

        assertEquals(Collections.singletonList(expectedLineCommit), feature2_1Commits);
        assertEquals(true, featureCommits.contains(expectedPointCommit)
                && featureCommits.contains(expectedLineCommit));
    }

    @Test
    public void testPathFilterByTypeName() throws Exception {

        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        LinkedList<RevCommit> commits = Lists.newLinkedList();

        LinkedList<RevCommit> typeName1Commits = Lists.newLinkedList();

        for (Feature f : features) {
            insertAndAdd(f);
            final RevCommit commit = geogig.command(CommitOp.class)
                    .setMessage(f.getIdentifier().toString()).call();
            commits.addFirst(commit);
            if (pointsName.equals(f.getType().getName().getLocalPart())) {
                typeName1Commits.addFirst(commit);
            }
        }

        // path to filter commits on type1
        String path = pointsName;

        List<RevCommit> logCommits = toList(logOp.addPath(path).call());
        assertEquals(typeName1Commits.size(), logCommits.size());
        assertEquals(typeName1Commits, logCommits);
    }

    @Test
    public void testLimit() throws Exception {

        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);

        for (Feature f : features) {
            insertAndAdd(f);
            geogig.command(CommitOp.class).call();
        }

        assertEquals(3, Iterators.size(logOp.setLimit(3).call()));
        assertEquals(1, Iterators.size(logOp.setLimit(1).call()));
        assertEquals(4, Iterators.size(logOp.setLimit(4).call()));

        exception.expect(IllegalArgumentException.class);
        logOp.setLimit(-1).call();
    }

    @Test
    public void testSkip() throws Exception {
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);

        for (Feature f : features) {
            insertAndAdd(f);
            geogig.command(CommitOp.class).call();
        }

        logOp.setSkip(2).call();

        exception.expect(IllegalArgumentException.class);
        logOp.setSkip(-1).call();
    }

    @Test
    public void testTemporalConstraint() throws Exception {

        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        List<Long> timestamps = Arrays.asList(Long.valueOf(1000), Long.valueOf(2000),
                Long.valueOf(3000), Long.valueOf(4000), Long.valueOf(5000), Long.valueOf(6000));

        LinkedList<RevCommit> allCommits = new LinkedList<RevCommit>();

        for (int i = 0; i < features.size(); i++) {
            Feature f = features.get(i);
            Long timestamp = timestamps.get(i);
            insertAndAdd(f);
            final RevCommit commit = geogig.command(CommitOp.class).setCommitterTimestamp(timestamp)
                    .call();
            allCommits.addFirst(commit);
        }

        // test time range exclusive
        boolean minInclusive = false;
        boolean maxInclusive = false;
        Range<Date> commitRange = new Range<Date>(Date.class, new Date(2000), minInclusive,
                new Date(5000), maxInclusive);
        logOp.setTimeRange(commitRange);

        List<RevCommit> logged = toList(logOp.call());
        List<RevCommit> expected = allCommits.subList(2, 4);
        assertEquals(expected, logged);

        // test time range inclusive
        minInclusive = true;
        maxInclusive = true;
        commitRange = new Range<Date>(Date.class, new Date(2000), minInclusive, new Date(5000),
                maxInclusive);
        logOp = geogig.command(LogOp.class).setTimeRange(commitRange);

        logged = toList(logOp.call());
        expected = allCommits.subList(1, 5);
        assertEquals(expected, logged);

        // test reset time range
        logOp = geogig.command(LogOp.class).setTimeRange(commitRange).setTimeRange(null);
        logged = toList(logOp.call());
        expected = allCommits;
        assertEquals(expected, logged);
    }

    @Test
    public void testSinceUntil() throws Exception {
        final ObjectId oid1_1 = insertAndAdd(points1);
        final RevCommit commit1_1 = geogig.command(CommitOp.class).call();

        insertAndAdd(points2);
        final RevCommit commit1_2 = geogig.command(CommitOp.class).call();

        insertAndAdd(lines1);
        final RevCommit commit2_1 = geogig.command(CommitOp.class).call();

        final ObjectId oid2_2 = insertAndAdd(lines2);
        final RevCommit commit2_2 = geogig.command(CommitOp.class).call();

        try {
            logOp = geogig.command(LogOp.class);
            logOp.setSince(oid1_1).call();
            fail("Expected ISE as since is not a commit");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("since"));
        }

        try {
            logOp = geogig.command(LogOp.class);
            logOp.setSince(null).setUntil(oid2_2).call();
            fail("Expected ISE as until is not a commit");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("until"));
        }

        List<RevCommit> logs;
        List<RevCommit> expected;

        logOp = geogig.command(LogOp.class);
        logs = toList(logOp.setSince(commit1_2.getId()).setUntil(null).call());
        expected = Arrays.asList(commit2_2, commit2_1);
        assertEquals(expected, logs);

        logOp = geogig.command(LogOp.class);
        logs = toList(logOp.setSince(commit2_2.getId()).setUntil(null).call());
        expected = Collections.emptyList();
        assertEquals(expected, logs);

        logOp = geogig.command(LogOp.class);
        logs = toList(logOp.setSince(commit1_2.getId()).setUntil(commit2_1.getId()).call());
        expected = Arrays.asList(commit2_1);
        assertEquals(expected, logs);

        logOp = geogig.command(LogOp.class);
        logs = toList(logOp.setSince(null).setUntil(commit2_1.getId()).call());
        expected = Arrays.asList(commit2_1, commit1_2, commit1_1);
        assertEquals(expected, logs);
    }

    @Test
    public void testMerged() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added
        // | |
        // o | - Lines 1 added
        // |/
        // o - master - HEAD - Merge commit

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        MergeReport mergeReport = geogig.command(MergeOp.class).addCommit(branch1.getObjectId())
                .setMessage("My merge message.").call();

        RevCommit mergeCommit = mergeReport.getMergeCommit();

        Iterator<RevCommit> iterator = logOp.call();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(mergeCommit, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c4, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c3, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c2, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c1, iterator.next());

        // test log using first parent only. It should not contain commit 2)
        LogOp op = geogig.command(LogOp.class).setFirstParentOnly(true);
        iterator = op.call();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(mergeCommit, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c4, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c3, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c1, iterator.next());
        assertFalse(iterator.hasNext());

        // Test topological order
        op = geogig.command(LogOp.class).setTopoOrder(true);
        iterator = op.call();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(mergeCommit, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c4, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c3, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c1, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c2, iterator.next());
        assertFalse(iterator.hasNext());

    }

    @Test
    public void testMergedWithPathFilter() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added
        // | |
        // o | - Lines 1 added
        // |/
        // o - master - HEAD - Merge commit

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        MergeReport mergeReport = geogig.command(MergeOp.class).addCommit(branch1.getObjectId())
                .setMessage("My merge message.").call();

        RevCommit mergeCommit = mergeReport.getMergeCommit();

        Iterator<RevCommit> iterator = logOp.addPath(pointsName + "/" + idP2).call();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(mergeCommit, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c2, iterator.next());

        // test log using first parent only. It should not contain commit 2)
        LogOp op = geogig.command(LogOp.class).addPath(pointsName + "/" + idP2)
                .setFirstParentOnly(true);
        iterator = op.call();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(mergeCommit, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testTopoWithMergedAndSince() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit points2Added = geogig.command(CommitOp.class)
                .setMessage("commit for " + idP2).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        RevCommit sinceCommit = geogig.command(CommitOp.class).setMessage("commit for " + idP3)
                .call();
        insertAndAdd(lines1);
        RevCommit lines1Added = geogig.command(CommitOp.class).setMessage("commit for " + idL1)
                .call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added ("since" commit)
        // | |
        // o | - Lines 1 added
        // |/
        // o - master - HEAD - Merge commit

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        MergeReport mergeReport = geogig.command(MergeOp.class).addCommit(branch1.getObjectId())
                .setMessage("My merge message.").call();

        RevCommit mergeCommit = mergeReport.getMergeCommit();

        Iterator<RevCommit> iterator = logOp.setTopoOrder(true).setSince(sinceCommit.getId())
                .call();

        // The log should include Merge commit, Lines 1 added, and Points 2 Added.
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(mergeCommit, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(lines1Added, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(points2Added, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testAll() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        LogOp op = geogig.command(LogOp.class);
        op.addCommit(c2.getId());
        op.addCommit(c4.getId());
        Iterator<RevCommit> iterator = op.call();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(c4, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c3, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c2, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c1, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testBranch() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        LogOp op = geogig.command(LogOp.class).addCommit(c2.getId());
        Iterator<RevCommit> iterator = op.call();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(c2, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c1, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testAuthorFilter() throws Exception {
        insertAndAdd(points1);
        final RevCommit firstCommit = geogig.command(CommitOp.class)
                .setAuthor("firstauthor", "firstauthor@boundlessgeo.com").call();

        insertAndAdd(lines1);
        final RevCommit secondCommit = geogig.command(CommitOp.class)
                .setAuthor("secondauthor", "secondauthor@boundlessgeo.com").call();

        Iterator<RevCommit> iterator = logOp.setAuthor("firstauthor").call();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(firstCommit, iterator.next());
        assertFalse(iterator.hasNext());

        iterator = logOp.setAuthor("secondauthor").call();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(secondCommit, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testCommitterFilter() throws Exception {
        insertAndAdd(points1);
        final RevCommit firstCommit = geogig.command(CommitOp.class)
                .setCommitter("firstcommitter", "firstcommitter@boundlessgeo.com").call();

        insertAndAdd(lines1);
        final RevCommit secondCommit = geogig.command(CommitOp.class)
                .setAuthor("secondcommitter", "secondcommitter@boundlessgeo.com").call();

        Iterator<RevCommit> iterator = logOp.setAuthor("firstcommitter").call();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(firstCommit, iterator.next());
        assertFalse(iterator.hasNext());

        iterator = logOp.setAuthor("secondcommitter").call();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(secondCommit, iterator.next());
        assertFalse(iterator.hasNext());
    }

}
