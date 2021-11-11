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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.porcelain.CheckoutResult.Results.CHECKOUT_LOCAL_BRANCH;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.locationtech.geogig.dsl.Commands;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.feature.Feature;
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
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.ProgressListener;

import com.google.common.collect.Iterators;
import com.google.common.collect.Range;

public class LogOpTest extends RepositoryTestCase {

    private Geogig gg;

    private LogOp logOp;

    protected @Override void setUpInternal() throws Exception {
        logOp = repo.command(LogOp.class);
        this.gg = Geogig.of(super.repo.context());
    }

    @Test
    public void testComplex() throws Exception {
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);

        for (Feature f : features) {
            insertAndAdd(f);
        }

        repo.command(CommitOp.class).setMessage("initial commit").call();

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
        repo.command(ResetOp.class).setMode(ResetOp.ResetMode.HARD)
                .setCommit(merge2_left.getMergeCommit()::getId).call();

        checkout("branch1");

        insertAndAdd(lines1_modified);
        commit("right modify 2");

        checkout("intermediate_right");

        MergeReport merge2_right = mergeNoFF("branch1", "merge 2 right", true);

        checkout("branch1");
        repo.command(ResetOp.class).setMode(ResetOp.ResetMode.HARD)
                .setCommit(merge2_right.getMergeCommit()::getId).call();

        checkout("master");

        mergeNoFF("branch1", "final merge", true);

        // both arrays should have 9 elements and contain the same commits (in different
        // order)
        List<RevCommit> log_topo = newArrayList(
                repo.command(LogOp.class).setTopoOrder(true).call());

        assertEquals(log_topo.size(), 9);

        List<RevCommit> log_chrono = newArrayList(
                repo.command(LogOp.class).setTopoOrder(false).call());

        assertEquals(log_chrono.size(), 9);

        List<ObjectId> log_topo_ids = log_topo.stream().map(c -> c.getId()).sorted()
                .collect(Collectors.toList());
        List<ObjectId> log_chrono_ids = log_chrono.stream().map(c -> c.getId()).sorted()
                .collect(Collectors.toList());

        assertTrue(log_topo_ids.equals(log_chrono_ids));
    }

    protected static final ProgressListener SIMPLE_PROGRESS = new DefaultProgressListener() {
        public @Override void setDescription(String msg, Object... args) {
            System.err.printf(msg + "\n", args);
        }
    };

    protected void createBranch(String branch) {
        repo.command(BranchCreateOp.class).setAutoCheckout(true).setName(branch)
                .setProgressListener(SIMPLE_PROGRESS).call();
    }

    protected MergeReport mergeNoFF(String branch, String mergeMessage, boolean mergeOurs) {
        Ref branchRef = repo.command(RefParse.class).setName(branch).call().get();
        ObjectId updatesBranchTip = branchRef.getObjectId();
        MergeReport mergeReport = repo.command(MergeOp.class)//
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
        final RevCommit firstCommit = repo.command(CommitOp.class).call();

        Iterator<RevCommit> iterator = logOp.call();
        assertNotNull(iterator);

        assertTrue(iterator.hasNext());
        assertEquals(firstCommit, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testHeadWithTwoCommits() throws Exception {

        insertAndAdd(points1);
        final RevCommit firstCommit = repo.command(CommitOp.class).call();

        insertAndAdd(lines1);
        final RevCommit secondCommit = repo.command(CommitOp.class).call();

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
            final RevCommit commit = repo.command(CommitOp.class).call();
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
        List<RevCommit> allCommits = new ArrayList<>();
        RevCommit expectedCommit = null;

        for (Feature f : features) {
            insertAndAdd(f);
            String id = f.getId();
            final RevCommit commit = repo.command(CommitOp.class).call();
            if (id.equals(lines1.getId())) {
                expectedCommit = commit;
            }
            allCommits.add(commit);
        }

        String path = NodeRef.appendChild(linesName, lines1.getId());

        List<RevCommit> feature2_1Commits = toList(logOp.addPath(path).call());
        assertEquals(1, feature2_1Commits.size());
        assertEquals(Collections.singletonList(expectedCommit), feature2_1Commits);
    }

    @Test
    public void testMultiplePaths() throws Exception {
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);
        List<RevCommit> allCommits = new ArrayList<>();
        RevCommit expectedLineCommit = null;
        RevCommit expectedPointCommit = null;
        for (Feature f : features) {
            insertAndAdd(f);
            String id = f.getId();
            final RevCommit commit = repo.command(CommitOp.class).call();
            if (id.equals(lines1.getId())) {
                expectedLineCommit = commit;
            } else if (id.equals(points1.getId())) {
                expectedPointCommit = commit;
            }
            allCommits.add(commit);
        }

        String linesPath = NodeRef.appendChild(linesName, lines1.getId());
        String pointsPath = NodeRef.appendChild(pointsName, points1.getId());

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
        LinkedList<RevCommit> commits = new LinkedList<>();

        LinkedList<RevCommit> typeName1Commits = new LinkedList<>();

        for (Feature f : features) {
            insertAndAdd(f);
            final RevCommit commit = repo.command(CommitOp.class).setMessage(f.getId()).call();
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
            repo.command(CommitOp.class).call();
        }

        assertEquals(3, Iterators.size(logOp.setLimit(3).call()));
        assertEquals(1, Iterators.size(logOp.setLimit(1).call()));
        assertEquals(4, Iterators.size(logOp.setLimit(4).call()));

        assertThrows(IllegalArgumentException.class, logOp.setLimit(-1)::call);
    }

    @Test
    public void testSkip() throws Exception {
        List<Feature> features = Arrays.asList(points1, lines1, points2, lines2, points3, lines3);

        for (Feature f : features) {
            insertAndAdd(f);
            repo.command(CommitOp.class).call();
        }

        logOp.setSkip(2).call();

        assertThrows(IllegalArgumentException.class, logOp.setSkip(-1)::call);
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
            final RevCommit commit = repo.command(CommitOp.class).setCommitterTimestamp(timestamp)
                    .call();
            allCommits.addFirst(commit);
        }

        // test time range exclusive
        Range<Date> commitRange = Range.open(new Date(2000), new Date(5000));
        logOp.setTimeRange(commitRange);

        List<RevCommit> logged = toList(logOp.call());
        List<RevCommit> expected = allCommits.subList(2, 4);
        assertEquals(expected, logged);

        // test time range inclusive
        commitRange = Range.closed(new Date(2000), new Date(5000));
        logOp = repo.command(LogOp.class).setTimeRange(commitRange);

        logged = toList(logOp.call());
        expected = allCommits.subList(1, 5);
        assertEquals(expected, logged);

        // test reset time range
        logOp = repo.command(LogOp.class).setTimeRange(commitRange).setTimeRange(null);
        logged = toList(logOp.call());
        expected = allCommits;
        assertEquals(expected, logged);
    }

    @Test
    public void testSinceUntil() throws Exception {
        final ObjectId oid1_1 = insertAndAdd(points1);
        final RevCommit commit1_1 = repo.command(CommitOp.class).call();

        insertAndAdd(points2);
        final RevCommit commit1_2 = repo.command(CommitOp.class).call();

        insertAndAdd(lines1);
        final RevCommit commit2_1 = repo.command(CommitOp.class).call();

        final ObjectId oid2_2 = insertAndAdd(lines2);
        final RevCommit commit2_2 = repo.command(CommitOp.class).call();

        try {
            logOp = repo.command(LogOp.class);
            logOp.setSince(oid1_1).call();
            fail("Expected ISE as since is not a commit");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("since"));
        }

        try {
            logOp = repo.command(LogOp.class);
            logOp.setSince(null).setUntil(oid2_2).call();
            fail("Expected ISE as until is not a commit");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), Matchers.containsString("until"));
        }

        List<RevCommit> logs;
        List<RevCommit> expected;

        logOp = repo.command(LogOp.class);
        logs = toList(logOp.setSince(commit1_2.getId()).setUntil(null).call());
        expected = Arrays.asList(commit2_2, commit2_1);
        assertEquals(expected, logs);

        logOp = repo.command(LogOp.class);
        logs = toList(logOp.setSince(commit2_2.getId()).setUntil(null).call());
        expected = Collections.emptyList();
        assertEquals(expected, logs);

        logOp = repo.command(LogOp.class);
        logs = toList(logOp.setSince(commit1_2.getId()).setUntil(commit2_1.getId()).call());
        expected = Arrays.asList(commit2_1);
        assertEquals(expected, logs);

        logOp = repo.command(LogOp.class);
        logs = toList(logOp.setSince(null).setUntil(commit2_1.getId()).call());
        expected = Arrays.asList(commit2_1, commit1_2, commit1_1);
        assertEquals(expected, logs);
    }

    @Test
    public void testMerged() throws Exception {
        // Create the following revision graph
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - master - HEAD - Lines 1 added
        Commands commands = gg.commands();
        assertEquals(0, commands.logCall().count());

        insertAndAdd(points1);
        final RevCommit c1 = commands.commit("commit for %s", idP1).call();
        assertEquals(1, commands.logCall().count());

        // create branch1 and checkout
        commands.branch("branch1").call();
        commands.checkout("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = commands.commit("commit for %s", idP2).call();
        assertEquals(2, commands.logCall().count());
        assertEquals("branch1", gg.refs().head().get().peel().localName());

        // checkout master
        commands.checkout("master").call();
        assertEquals("master", gg.refs().head().get().peel().localName());
        insertAndAdd(points3);
        final RevCommit c3 = commands.commit("commit for %s", idP3).call();
        insertAndAdd(lines1);
        final RevCommit c4 = commands.commit("commit for %s", idL1).call();
        assertEquals(3, commands.logCall().count());

        // Merge branch1 into master to create the following revision graph
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added
        // | |
        // o | - Lines 1 added
        // |/
        // o - master - HEAD - Merge commit

        MergeReport mergeReport = commands.merge("branch1").setMessage("My merge message.").call();

        RevCommit mergeCommit = mergeReport.getMergeCommit();
        assertEquals(c4.getId(), mergeCommit.parentN(0).get());
        assertEquals(c2.getId(), mergeCommit.parentN(1).get());

        List<String> expected;
        List<String> actual;

        expected = Stream.of(mergeCommit, c4, c3, c2, c1).map(RevCommit::getMessage)
                .collect(Collectors.toList());
        actual = toList(repo.command(LogOp.class).call()).stream().map(RevCommit::getMessage)
                .collect(Collectors.toList());
        assertEquals(expected, actual);

        // test log using first parent only. It should not contain commit 2)
        LogOp op = repo.command(LogOp.class).setFirstParentOnly(true);
        expected = Stream.of(mergeCommit, c4, c3, c1).map(RevCommit::getMessage)
                .collect(Collectors.toList());
        actual = toList(op.call()).stream().map(RevCommit::getMessage).collect(Collectors.toList());
        assertEquals(expected, actual);

        // Test topological order
        op = repo.command(LogOp.class).setTopoOrder(true);
        expected = Stream.of(mergeCommit, c4, c3, c1, c2).map(RevCommit::getMessage)
                .collect(Collectors.toList());
        actual = toList(op.call()).stream().map(RevCommit::getMessage).collect(Collectors.toList());
        assertEquals(expected, actual);
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
        repo.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        repo.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = repo.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        repo.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        repo.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        repo.command(CommitOp.class).setMessage("commit for " + idL1).call();

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

        Ref branch1 = repo.command(RefParse.class).setName("branch1").call().get();
        MergeReport mergeReport = repo.command(MergeOp.class).addCommit(branch1.getObjectId())
                .setMessage("My merge message.").call();

        RevCommit mergeCommit = mergeReport.getMergeCommit();

        Iterator<RevCommit> iterator = logOp.addPath(pointsName + "/" + idP2).call();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(mergeCommit, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(c2, iterator.next());

        // test log using first parent only. It should not contain commit 2)
        LogOp op = repo.command(LogOp.class).addPath(pointsName + "/" + idP2)
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
        repo.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        repo.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit points2Added = repo.command(CommitOp.class).setMessage("commit for " + idP2)
                .call();

        // checkout master
        repo.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        RevCommit sinceCommit = repo.command(CommitOp.class).setMessage("commit for " + idP3)
                .call();
        insertAndAdd(lines1);
        RevCommit lines1Added = repo.command(CommitOp.class).setMessage("commit for " + idL1)
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

        Ref branch1 = repo.command(RefParse.class).setName("branch1").call().get();
        MergeReport mergeReport = repo.command(MergeOp.class).addCommit(branch1.getObjectId())
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
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - master - HEAD - Lines 1 added
        Commands commands = gg.commands();
        assertEquals(0, commands.logCall().count());
        insertAndAdd(points1);
        final RevCommit c1 = commands.commit("commit for %s", idP1).call();
        assertEquals(1, commands.logCall().count());

        // create branch1 and checkout
        commands.branch("branch1").call();
        assertEquals(CHECKOUT_LOCAL_BRANCH, commands.checkout("branch1").call().getResult());
        insertAndAdd(points2);
        final RevCommit c2 = commands.commit("commit for %s", idP2).call();
        assertEquals(2, commands.logCall().count());
        MatcherAssert.assertThat(c2.getCommitter().getTimestamp(),
                org.hamcrest.Matchers.greaterThan(c1.getCommitter().getTimestamp()));

        // checkout master
        assertEquals(CHECKOUT_LOCAL_BRANCH, commands.checkout("master").call().getResult());
        assertEquals(1, commands.logCall().count());
        insertAndAdd(points3);
        final RevCommit c3 = commands.commit("commit for %s", idP3).call();
        MatcherAssert.assertThat(c3.getCommitter().getTimestamp(),
                org.hamcrest.Matchers.greaterThan(c2.getCommitter().getTimestamp()));
        insertAndAdd(lines1);
        final RevCommit c4 = commands.commit("commit for %s", idL1).call();
        assertEquals(3, commands.logCall().count());
        Platform platform = repo.context().platform();
        platform.currentTimeMillis();
        MatcherAssert.assertThat(c4.getCommitter().getTimestamp(),
                org.hamcrest.Matchers.greaterThan(c3.getCommitter().getTimestamp()));

        LogOp op = repo.command(LogOp.class);
        op.addCommit(c2.getId());
        op.addCommit(c4.getId());
        Iterator<RevCommit> iterator = op.call();
        assertNotNull(iterator);

        List<String> expected = Arrays.asList(c4, c3, c2, c1).stream().map(RevCommit::getMessage)
                .collect(Collectors.toList());
        List<String> actual = toList(iterator).stream().map(RevCommit::getMessage)
                .collect(Collectors.toList());

        assertEquals(expected, actual);
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
        final RevCommit c1 = repo.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        repo.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = repo.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        repo.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        repo.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        repo.command(CommitOp.class).setMessage("commit for " + idL1).call();

        LogOp op = repo.command(LogOp.class).addCommit(c2.getId());
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
        final RevCommit firstCommit = repo.command(CommitOp.class)
                .setAuthor("firstauthor", "firstauthor@test.com").call();

        insertAndAdd(lines1);
        final RevCommit secondCommit = repo.command(CommitOp.class)
                .setAuthor("secondauthor", "secondauthor@test.com").call();

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
        final RevCommit firstCommit = repo.command(CommitOp.class)
                .setCommitter("firstcommitter", "firstcommitter@test.com").call();

        insertAndAdd(lines1);
        final RevCommit secondCommit = repo.command(CommitOp.class)
                .setAuthor("secondcommitter", "secondcommitter@test.com").call();

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
