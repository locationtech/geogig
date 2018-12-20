/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import java.util.Iterator;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.merge.ConflictsCheckOp;
import org.locationtech.geogig.plumbing.merge.ConflictsQueryOp;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CheckoutResult;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.RebaseConflictsException;
import org.locationtech.geogig.porcelain.RebaseOp;
import org.locationtech.geogig.repository.Conflict;
import org.opengis.feature.Feature;

import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;

public class RebaseOpTest extends RepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        // These values should be used during a commit to set author/committer
        // TODO: author/committer roles need to be defined better, but for
        // now they are the same thing.
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue("groldan").call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue("groldan@boundlessgeo.com").call();
    }

    @Test
    public void testRebase() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - master - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - branch2 - HEAD - Lines 1 added
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master, then create branch2 and checkout
        geogig.command(CheckoutOp.class).setSource("master").call();
        final Ref branch2 = geogig.command(BranchCreateOp.class).setAutoCheckout(true)
                .setName("branch2").call();
        insertAndAdd(points3);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        // Rebase branch2 onto branch1 to create the following revision graph
        // o
        // |
        // o - master - Points 1 added
        // |
        // o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - branch2 - HEAD - Lines 1 added

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        geogig.command(RebaseOp.class).setUpstream(Suppliers.ofInstance(branch1.getObjectId()))
                .call();

        CheckoutResult result;
        result = geogig.command(CheckoutOp.class).setSource("branch1").call();
        assertEquals(c2.getTreeId(), result.getNewTree());
        assertTrue(geogig.command(RefParse.class).setName(Ref.HEAD).call().get() instanceof SymRef);
        assertEquals(branch1.getName(),
                ((SymRef) geogig.command(RefParse.class).setName(Ref.HEAD).call().get())
                        .getTarget());

        result = geogig.command(CheckoutOp.class).setSource("branch2").call();
        assertFalse(c4.getTreeId().equals(result.getNewTree()));
        assertTrue(geogig.command(RefParse.class).setName(Ref.HEAD).call().get() instanceof SymRef);
        assertEquals(branch2.getName(),
                ((SymRef) geogig.command(RefParse.class).setName(Ref.HEAD).call().get())
                        .getTarget());

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();

        // Commit 4
        RevCommit logC4 = log.next();
        assertEquals(c4.getAuthor(), logC4.getAuthor());
        assertEquals(c4.getCommitter().getName(), logC4.getCommitter().getName());
        assertEquals(c4.getCommitter().getEmail(), logC4.getCommitter().getEmail());
        assertEquals(c4.getMessage(), logC4.getMessage());
        assertEquals(c4.getAuthor().getTimeZoneOffset(), logC4.getAuthor().getTimeZoneOffset());
        assertEquals(c4.getAuthor().getTimestamp(), logC4.getAuthor().getTimestamp());
        assertEquals(c4.getCommitter().getTimeZoneOffset(),
                logC4.getCommitter().getTimeZoneOffset());
        assertFalse(c4.getCommitter().getTimestamp() == logC4.getCommitter().getTimestamp());
        assertFalse(c4.getTreeId().equals(logC4.getTreeId()));

        // Commit 3
        RevCommit logC3 = log.next();
        assertEquals(c3.getAuthor(), logC3.getAuthor());
        assertEquals(c3.getCommitter().getName(), logC3.getCommitter().getName());
        assertEquals(c3.getCommitter().getEmail(), logC3.getCommitter().getEmail());
        assertEquals(c3.getMessage(), logC3.getMessage());
        assertEquals(c3.getAuthor().getTimeZoneOffset(), logC3.getAuthor().getTimeZoneOffset());
        assertEquals(c3.getAuthor().getTimestamp(), logC3.getAuthor().getTimestamp());
        assertEquals(c3.getCommitter().getTimeZoneOffset(),
                logC3.getCommitter().getTimeZoneOffset());
        assertFalse(c3.getCommitter().getTimestamp() == logC3.getCommitter().getTimestamp());
        assertFalse(c3.getTreeId().equals(logC3.getTreeId()));

        // Commit 2
        RevCommit logC2 = log.next();
        assertEquals(c2.getAuthor(), logC2.getAuthor());
        assertEquals(c2.getCommitter().getName(), logC2.getCommitter().getName());
        assertEquals(c2.getMessage(), logC2.getMessage());
        assertEquals(c2.getAuthor().getTimeZoneOffset(), logC2.getAuthor().getTimeZoneOffset());
        assertEquals(c2.getAuthor().getTimestamp(), logC2.getAuthor().getTimestamp());
        assertEquals(c2.getCommitter().getTimeZoneOffset(),
                logC2.getCommitter().getTimeZoneOffset());
        assertEquals(c2.getCommitter().getTimestamp(), logC2.getCommitter().getTimestamp());
        assertEquals(c2.getTreeId(), logC2.getTreeId());

        // Commit 1
        RevCommit logC1 = log.next();
        assertEquals(c1.getAuthor(), logC1.getAuthor());
        assertEquals(c1.getCommitter().getName(), logC1.getCommitter().getName());
        assertEquals(c1.getMessage(), logC1.getMessage());
        assertEquals(c1.getAuthor().getTimeZoneOffset(), logC1.getAuthor().getTimeZoneOffset());
        assertEquals(c1.getAuthor().getTimestamp(), logC1.getAuthor().getTimestamp());
        assertEquals(c1.getCommitter().getTimeZoneOffset(),
                logC1.getCommitter().getTimeZoneOffset());
        assertEquals(c1.getCommitter().getTimestamp(), logC1.getCommitter().getTimestamp());
        assertEquals(c1.getTreeId(), logC1.getTreeId());

    }

    @Test
    public void testRebaseSquash() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - master - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - branch2 - HEAD - Lines 1 added
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master, then create branch2 and checkout
        geogig.command(CheckoutOp.class).setSource("master").call();
        final Ref branch2 = geogig.command(BranchCreateOp.class).setAutoCheckout(true)
                .setName("branch2").call();
        insertAndAdd(points3);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        assertNotNull(c3);
        insertAndAdd(lines1);
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        geogig.command(RebaseOp.class).setUpstream(Suppliers.ofInstance(branch1.getObjectId()))
                .setSquashMessage("squashed commit").call();

        CheckoutResult result;
        result = geogig.command(CheckoutOp.class).setSource("branch1").call();
        assertEquals(c2.getTreeId(), result.getNewTree());
        assertTrue(geogig.command(RefParse.class).setName(Ref.HEAD).call().get() instanceof SymRef);
        assertEquals(branch1.getName(),
                ((SymRef) geogig.command(RefParse.class).setName(Ref.HEAD).call().get())
                        .getTarget());

        result = geogig.command(CheckoutOp.class).setSource("branch2").call();
        assertFalse(c4.getTreeId().equals(result.getNewTree()));
        assertTrue(geogig.command(RefParse.class).setName(Ref.HEAD).call().get() instanceof SymRef);
        assertEquals(branch2.getName(),
                ((SymRef) geogig.command(RefParse.class).setName(Ref.HEAD).call().get())
                        .getTarget());

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();

        // Commit 4
        RevCommit logC4 = log.next();
        assertEquals(c4.getAuthor(), logC4.getAuthor());
        assertEquals(c4.getCommitter().getName(), logC4.getCommitter().getName());
        assertEquals(c4.getCommitter().getEmail(), logC4.getCommitter().getEmail());
        assertEquals("squashed commit", logC4.getMessage());
        assertEquals(c4.getAuthor().getTimeZoneOffset(), logC4.getAuthor().getTimeZoneOffset());
        assertEquals(c4.getAuthor().getTimestamp(), logC4.getAuthor().getTimestamp());
        assertEquals(c4.getCommitter().getTimeZoneOffset(),
                logC4.getCommitter().getTimeZoneOffset());
        assertFalse(c4.getCommitter().getTimestamp() == logC4.getCommitter().getTimestamp());
        assertFalse(c4.getTreeId().equals(logC4.getTreeId()));

        String path = NodeRef.appendChild(linesName, idL1);
        Optional<RevFeature> lines = geogig.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(lines.isPresent());
        path = NodeRef.appendChild(pointsName, idP3);
        Optional<RevFeature> points = geogig.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(points.isPresent());

        // Commit 2
        RevCommit logC2 = log.next();
        assertEquals(c2.getAuthor(), logC2.getAuthor());
        assertEquals(c2.getCommitter().getName(), logC2.getCommitter().getName());
        assertEquals(c2.getMessage(), logC2.getMessage());
        assertEquals(c2.getAuthor().getTimeZoneOffset(), logC2.getAuthor().getTimeZoneOffset());
        assertEquals(c2.getAuthor().getTimestamp(), logC2.getAuthor().getTimestamp());
        assertEquals(c2.getCommitter().getTimeZoneOffset(),
                logC2.getCommitter().getTimeZoneOffset());
        assertEquals(c2.getCommitter().getTimestamp(), logC2.getCommitter().getTimestamp());
        assertEquals(c2.getTreeId(), logC2.getTreeId());

        // Commit 1
        RevCommit logC1 = log.next();
        assertEquals(c1.getAuthor(), logC1.getAuthor());
        assertEquals(c1.getCommitter().getName(), logC1.getCommitter().getName());
        assertEquals(c1.getMessage(), logC1.getMessage());
        assertEquals(c1.getAuthor().getTimeZoneOffset(), logC1.getAuthor().getTimeZoneOffset());
        assertEquals(c1.getAuthor().getTimestamp(), logC1.getAuthor().getTimestamp());
        assertEquals(c1.getCommitter().getTimeZoneOffset(),
                logC1.getCommitter().getTimeZoneOffset());
        assertEquals(c1.getCommitter().getTimestamp(), logC1.getCommitter().getTimestamp());
        assertEquals(c1.getTreeId(), logC1.getTreeId());

    }

    @Test
    public void testRebaseOnto() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // |-o - Points 2 added
        // |-|
        // |-o - branch1 - Points 3 added
        // |--\
        // |---o - branch 2 - HEAD - Lines 1 added
        // |
        // o - master - Lines 2 added

        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();
        insertAndAdd(points3);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();

        // create branch2 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch2").call();
        insertAndAdd(lines1);
        final RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        // checkout master and make a commit
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(lines2);
        final RevCommit c5 = geogig.command(CommitOp.class).setMessage("commit for " + idL2).call();

        // checkout branch2
        geogig.command(CheckoutOp.class).setSource("branch2").call();

        // Rebase branch2 onto master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - Points 2 added
        // | |
        // | o - branch1 - Points 3 added
        // |
        // o - master - Lines 2 added
        // |
        // o - branch 2 - HEAD - Lines 1 added

        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();
        Ref branch2 = geogig.command(RefParse.class).setName("branch2").call().get();
        Optional<Ref> master = geogig.command(RefParse.class).setName("master").call();

        geogig.command(RebaseOp.class).setUpstream(Suppliers.ofInstance(branch1.getObjectId()))
                .setOnto(Suppliers.ofInstance(master.get().getObjectId())).call();

        CheckoutResult result;
        result = geogig.command(CheckoutOp.class).setSource("branch1").call();
        assertEquals(c3.getTreeId(), result.getNewTree());
        assertTrue(geogig.command(RefParse.class).setName(Ref.HEAD).call().get() instanceof SymRef);
        assertEquals(branch1.getName(),
                ((SymRef) geogig.command(RefParse.class).setName(Ref.HEAD).call().get())
                        .getTarget());

        result = geogig.command(CheckoutOp.class).setSource("branch2").call();
        assertFalse(c4.getTreeId().equals(result.getNewTree()));
        assertTrue(geogig.command(RefParse.class).setName(Ref.HEAD).call().get() instanceof SymRef);
        assertEquals(branch2.getName(),
                ((SymRef) geogig.command(RefParse.class).setName(Ref.HEAD).call().get())
                        .getTarget());

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();

        // Commit 3 -- Lines 1
        RevCommit logC3 = log.next();
        assertEquals(c4.getAuthor(), logC3.getAuthor());
        assertEquals(c4.getCommitter().getName(), logC3.getCommitter().getName());
        assertEquals(c4.getMessage(), logC3.getMessage());
        assertEquals(c4.getAuthor().getTimeZoneOffset(), logC3.getAuthor().getTimeZoneOffset());
        assertEquals(c4.getAuthor().getTimestamp(), logC3.getAuthor().getTimestamp());
        assertEquals(c4.getCommitter().getTimeZoneOffset(),
                logC3.getCommitter().getTimeZoneOffset());
        assertFalse(c4.getCommitter().getTimestamp() == logC3.getCommitter().getTimestamp());
        assertFalse(c4.getTreeId().equals(logC3.getTreeId()));

        // Commit 2 -- Lines 2
        RevCommit logC2 = log.next();
        assertEquals(c5, logC2);

        // Commit 1 -- Points 1
        RevCommit logC1 = log.next();
        assertEquals(c1, logC1);
    }

    @Test
    public void testRebaseEqualChanges() throws Exception {

        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        assertNotNull(c1);
        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points1_modified);
        RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit in branch").call();

        // checkout master and make a commit with identical changes
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points1_modified);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit in master").call();

        geogig.command(RefParse.class).setName("branch1").call().get();
        Optional<Ref> master = geogig.command(RefParse.class).setName("master").call();

        geogig.command(CheckoutOp.class).setSource("branch1").call();
        geogig.command(RebaseOp.class).setUpstream(Suppliers.ofInstance(master.get().getObjectId()))
                .call();

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        assertEquals(c2.getMessage(), log.next().getMessage());
        assertEquals(c3, log.next());

    }

    @Test
    public void testRebaseNoUpstream() throws Exception {
        exception.expect(IllegalStateException.class);
        geogig.command(RebaseOp.class).call();
    }

    @Test
    public void testRebaseNoCommits() throws Exception {
        Optional<Ref> master = geogig.command(RefParse.class).setName("master").call();
        exception.expect(IllegalStateException.class);
        geogig.command(RebaseOp.class).setUpstream(Suppliers.ofInstance(master.get().getObjectId()))
                .call();
    }

    @Test
    public void testRebaseNoUpstreamCommit() throws Exception {
        Optional<Ref> master = geogig.command(RefParse.class).setName("master").call();

        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        exception.expect(IllegalStateException.class);
        geogig.command(RebaseOp.class).setUpstream(Suppliers.ofInstance(master.get().getObjectId()))
                .call();
    }

    @Test
    public void testRebaseWithConflictAndContinue() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1,2 added
        // |\
        // | o - branch1 - Points 1 modifiedB, 2 removed, 3 added
        // |
        // o - Points 1 modified, 2 removed
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1, points2);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insert(points1Modified);
        delete(points2);
        geogig.command(AddOp.class).call();
        RevCommit masterCommit2 = geogig.command(CommitOp.class)
                .setMessage("adding points1 modified, deleting point2").call();
        insert(lines1);
        geogig.command(AddOp.class).call();
        RevCommit masterCommit = geogig.command(CommitOp.class).setMessage("adding lines.1").call();
        geogig.command(CheckoutOp.class).setSource("branch1").call();
        insert(points3);
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insert(points1ModifiedB);
        delete(points2);
        geogig.command(AddOp.class).call();
        RevCommit branchCommit = geogig.command(CommitOp.class).setMessage("branch commit").call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();

        try {
            geogig.command(RebaseOp.class).setUpstream(Suppliers.ofInstance(branch1.getObjectId()))
                    .call();
            fail();
        } catch (RebaseConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        Optional<Ref> ref = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertTrue(ref.isPresent());
        assertEquals(masterCommit.getId(), ref.get().getObjectId());

        List<Conflict> conflicts = Lists
                .newArrayList(geogig.command(ConflictsQueryOp.class).call());
        assertEquals(1, conflicts.size());
        String path = NodeRef.appendChild(pointsName, idP1);
        assertEquals(conflicts.get(0).getPath(), path);
        assertEquals(conflicts.get(0).getOurs(), RevFeature.builder().build(points1Modified).getId());
        assertEquals(conflicts.get(0).getTheirs(),
                RevFeature.builder().build(points1ModifiedB).getId());

        // solve, and continue
        Feature points1Merged = feature(pointsType, idP1, "StringProp1_2", new Integer(2000),
                "POINT(1 1)");
        insert(points1Merged);
        geogig.command(AddOp.class).call();
        geogig.command(RebaseOp.class).setContinue(true).call();

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        RevCommit logCommit1 = log.next();
        assertEquals(masterCommit.getAuthor(), logCommit1.getAuthor());
        assertEquals(masterCommit.getCommitter().getName(), logCommit1.getCommitter().getName());
        assertEquals(masterCommit.getMessage(), logCommit1.getMessage());
        assertEquals(masterCommit.getAuthor().getTimeZoneOffset(),
                logCommit1.getAuthor().getTimeZoneOffset());
        assertEquals(masterCommit.getAuthor().getTimestamp(),
                logCommit1.getAuthor().getTimestamp());
        assertEquals(masterCommit.getCommitter().getTimeZoneOffset(),
                logCommit1.getCommitter().getTimeZoneOffset());
        assertNotSame(masterCommit.getCommitter().getTimestamp(),
                logCommit1.getCommitter().getTimestamp());
        assertNotSame(masterCommit.getTreeId(), logCommit1.getTreeId());

        RevCommit logCommit2 = log.next();
        assertEquals(masterCommit2.getAuthor(), logCommit2.getAuthor());
        assertEquals(masterCommit2.getCommitter().getName(), logCommit2.getCommitter().getName());
        assertEquals(masterCommit2.getMessage(), logCommit2.getMessage());

        RevCommit logCommit3 = log.next();
        assertEquals(branchCommit.getAuthor(), logCommit3.getAuthor());
        assertEquals(branchCommit.getCommitter().getName(), logCommit3.getCommitter().getName());
        assertEquals(branchCommit.getMessage(), logCommit3.getMessage());

        ref = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertFalse(ref.isPresent());

    }

    @Test
    public void testRebaseSquashWithConflict() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1,2 added
        // |\
        // | o - branch1 - Points 1 modifiedB, 2 removed, 3 added
        // |
        // o - Points 1 modified, 2 removed
        // |
        // o - master - HEAD - Lines 1 added
        final RevCommit masterCommit;
        final RevCommit branchCommit;
        final Ref branch1;

        insertAndAdd(points1, points2);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insert(points1Modified);
        delete(points2);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("adding points1 modified, deleting point2")
                .call();
        insert(lines1);
        geogig.command(AddOp.class).call();
        masterCommit = geogig.command(CommitOp.class).setMessage("adding lines.1").call();
        geogig.command(CheckoutOp.class).setSource("branch1").call();
        insert(points3);
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insert(points1ModifiedB);
        delete(points2);
        geogig.command(AddOp.class).call();
        branchCommit = geogig.command(CommitOp.class).setMessage("branch commit").call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        branch1 = geogig.command(RefParse.class).setName("branch1").call().get();

        try {
            geogig.command(RebaseOp.class).setUpstream(Suppliers.ofInstance(branch1.getObjectId()))
                    .setSquashMessage("squashed commit").call();
            fail();
        } catch (RebaseConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        Optional<Ref> ref = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertTrue(ref.isPresent());
        assertEquals(masterCommit.getId(), ref.get().getObjectId());

        List<Conflict> conflicts = Lists
                .newArrayList(geogig.command(ConflictsQueryOp.class).call());
        assertEquals(1, conflicts.size());
        String path = NodeRef.appendChild(pointsName, idP1);
        assertEquals(conflicts.get(0).getPath(), path);
        assertEquals(conflicts.get(0).getOurs(), RevFeature.builder().build(points1Modified).getId());
        assertEquals(conflicts.get(0).getTheirs(),
                RevFeature.builder().build(points1ModifiedB).getId());

        // solve, and continue
        Feature points1Merged = feature(pointsType, idP1, "StringProp1_2", new Integer(2000),
                "POINT(1 1)");
        insert(points1Merged);
        geogig.command(AddOp.class).call();
        geogig.command(RebaseOp.class).setContinue(true).call();

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        RevCommit logCommit1 = log.next();
        assertEquals(masterCommit.getAuthor(), logCommit1.getAuthor());
        assertEquals(masterCommit.getCommitter().getName(), logCommit1.getCommitter().getName());
        assertEquals("squashed commit", logCommit1.getMessage());
        assertEquals(masterCommit.getAuthor().getTimeZoneOffset(),
                logCommit1.getAuthor().getTimeZoneOffset());
        assertEquals(masterCommit.getAuthor().getTimestamp(),
                logCommit1.getAuthor().getTimestamp());
        assertEquals(masterCommit.getCommitter().getTimeZoneOffset(),
                logCommit1.getCommitter().getTimeZoneOffset());
        assertNotSame(masterCommit.getCommitter().getTimestamp(),
                logCommit1.getCommitter().getTimestamp());
        assertNotSame(masterCommit.getTreeId(), logCommit1.getTreeId());

        RevCommit logCommit3 = log.next();
        assertEquals(branchCommit.getAuthor(), logCommit3.getAuthor());
        assertEquals(branchCommit.getCommitter().getName(), logCommit3.getCommitter().getName());
        assertEquals(branchCommit.getMessage(), logCommit3.getMessage());

        path = NodeRef.appendChild(linesName, idL1);
        Optional<RevFeature> lines = geogig.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(lines.isPresent());
        path = NodeRef.appendChild(pointsName, idP3);
        Optional<RevFeature> points = geogig.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(points.isPresent());
        path = NodeRef.appendChild(pointsName, idP1);
        points = geogig.command(RevObjectParse.class).setRefSpec(Ref.HEAD + ":" + path)
                .call(RevFeature.class);
        assertTrue(points.isPresent());
        assertEquals(RevFeature.builder().build(points1Merged), points.get());

        ref = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertFalse(ref.isPresent());

    }

    @Test
    public void testContinueSkipAndAbortWhileNotInConflictedState() throws Exception {
        try {
            geogig.command(RebaseOp.class).setContinue(true).call();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Cannot continue"));
        }
        try {
            geogig.command(RebaseOp.class).setAbort(true).call();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Cannot abort"));
        }
        try {
            geogig.command(RebaseOp.class).setSkip(true).call();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Cannot skip"));
        }
    }

    @Test
    public void testRebaseSkip() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1,2 added
        // |\
        // | o - branch1 - Points 1 modifiedB, 2 removed, 3 added
        // |
        // o - Points 1 modified, 2 removed
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1, points2);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insert(points1Modified);
        delete(points2);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("adding points1 modified, deleting point2")
                .call();
        insert(lines1);
        geogig.command(AddOp.class).call();
        RevCommit masterCommit = geogig.command(CommitOp.class).setMessage("adding lines.1").call();
        geogig.command(CheckoutOp.class).setSource("branch1").call();
        insert(points3);
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insert(points1ModifiedB);
        delete(points2);
        geogig.command(AddOp.class).call();
        RevCommit branchCommit = geogig.command(CommitOp.class).setMessage("branch commit").call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();

        try {
            geogig.command(RebaseOp.class).setUpstream(Suppliers.ofInstance(branch1.getObjectId()))
                    .call();
            fail();
        } catch (RebaseConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        String path = NodeRef.appendChild(pointsName, idP1);
        Optional<RevFeature> points = geogig.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(points.isPresent());
        assertEquals(RevFeature.builder().build(points1ModifiedB), points.get());

        boolean hasConflicts = geogig.command(ConflictsCheckOp.class).call().booleanValue();
        assertTrue(hasConflicts);

        geogig.command(RebaseOp.class).setSkip(true).call();

        hasConflicts = geogig.command(ConflictsCheckOp.class).call().booleanValue();
        assertFalse(hasConflicts);

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        RevCommit logCommit1 = log.next();
        assertEquals(masterCommit.getAuthor(), logCommit1.getAuthor());
        assertEquals(masterCommit.getCommitter().getName(), logCommit1.getCommitter().getName());
        assertEquals(masterCommit.getMessage(), logCommit1.getMessage());
        assertEquals(masterCommit.getAuthor().getTimeZoneOffset(),
                logCommit1.getAuthor().getTimeZoneOffset());
        assertEquals(masterCommit.getAuthor().getTimestamp(),
                logCommit1.getAuthor().getTimestamp());
        assertEquals(masterCommit.getCommitter().getTimeZoneOffset(),
                logCommit1.getCommitter().getTimeZoneOffset());
        assertNotSame(masterCommit.getCommitter().getTimestamp(),
                logCommit1.getCommitter().getTimestamp());
        assertNotSame(masterCommit.getTreeId(), logCommit1.getTreeId());

        RevCommit logCommit2 = log.next();
        assertEquals(branchCommit.getAuthor(), logCommit2.getAuthor());
        assertEquals(branchCommit.getCommitter().getName(), logCommit2.getCommitter().getName());
        assertEquals(branchCommit.getMessage(), logCommit2.getMessage());

        // check that the changes from conflicting commit haven't been introduced
        points = geogig.command(RevObjectParse.class).setRefSpec(Ref.HEAD + ":" + path)
                .call(RevFeature.class);
        assertTrue(points.isPresent());
        assertEquals(RevFeature.builder().build(points1ModifiedB), points.get());

        Optional<Ref> ref = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertFalse(ref.isPresent());

    }

    @Test
    public void testRebaseAbort() throws Exception {

        // Create the following revision graph
        // o
        // |
        // o - Points 1,2 added
        // |\
        // | o - branch1 - Points 1 modifiedB, 2 removed, 3 added
        // |
        // o - Points 1 modified, 2 removed
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1, points2);
        RevCommit firstCommit = geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insert(points1Modified);
        delete(points2);
        geogig.command(AddOp.class).call();
        RevCommit masterCommit2 = geogig.command(CommitOp.class)
                .setMessage("adding points1 modified, deleting point2").call();
        insert(lines1);
        geogig.command(AddOp.class).call();
        RevCommit masterCommit = geogig.command(CommitOp.class).setMessage("adding lines.1").call();
        geogig.command(CheckoutOp.class).setSource("branch1").call();
        insert(points3);
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insert(points1ModifiedB);
        delete(points2);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("branch commit").call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch1 = geogig.command(RefParse.class).setName("branch1").call().get();

        try {
            geogig.command(RebaseOp.class).setUpstream(Suppliers.ofInstance(branch1.getObjectId()))
                    .call();
            fail();
        } catch (RebaseConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        geogig.command(RebaseOp.class).setAbort(true).call();

        Optional<Ref> head = geogig.command(RefParse.class).setName(Ref.HEAD).call();
        assertTrue(head.isPresent());
        assertEquals(head.get().getObjectId(), masterCommit.getId());

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        RevCommit logCommit1 = log.next();
        assertEquals(masterCommit.getMessage(), logCommit1.getMessage());
        RevCommit logCommit2 = log.next();
        assertEquals(masterCommit2.getMessage(), logCommit2.getMessage());
        RevCommit logCommit3 = log.next();
        assertEquals(firstCommit.getMessage(), logCommit3.getMessage());

        String path = NodeRef.appendChild(pointsName, idP1);
        Optional<RevFeature> points = geogig.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(points.isPresent());
        assertEquals(RevFeature.builder().build(points1Modified), points.get());

    }

}
