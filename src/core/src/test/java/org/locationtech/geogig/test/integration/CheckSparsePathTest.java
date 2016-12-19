/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.CheckSparsePath;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.storage.GraphDatabase;

public class CheckSparsePathTest extends RepositoryTestCase {
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
    public void testCheckSparsePath() throws Exception {
        // Create the following revision graph
        // o - commit1
        // |\
        // | o - commit2
        // | |
        // | o - commit3
        // | |\
        // | | o - commit4
        // | | |\
        // | | | o - commit5 (sparse)
        // | | | |
        // | | o | - commit6
        // | | |/
        // | | o - commit7
        // | |
        // o | - commit8
        // | |
        // | o - commit9
        // |/
        // o - commit10
        insertAndAdd(points1);
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("commit1").call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("commit2").call();
        insertAndAdd(points3);
        RevCommit commit3 = geogig.command(CommitOp.class).setMessage("commit3").call();
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch2").call();
        insertAndAdd(lines1);
        RevCommit commit4 = geogig.command(CommitOp.class).setMessage("commit4").call();
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch3").call();
        insertAndAdd(poly1);
        RevCommit commit5 = geogig.command(CommitOp.class).setMessage("commit5").call();
        geogig.getRepository().graphDatabase().setProperty(commit5.getId(),
                GraphDatabase.SPARSE_FLAG, "true");
        geogig.command(CheckoutOp.class).setSource("branch2").call();
        insertAndAdd(poly2);
        RevCommit commit6 = geogig.command(CommitOp.class).setMessage("commit6").call();
        RevCommit commit7 = geogig.command(MergeOp.class).setMessage("commit7")
                .addCommit(commit5.getId()).call().getMergeCommit();

        geogig.command(CheckoutOp.class).setSource("branch1").call();
        insertAndAdd(lines3);
        RevCommit commit9 = geogig.command(CommitOp.class).setMessage("commit9").call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(lines2);
        RevCommit commit8 = geogig.command(CommitOp.class).setMessage("commit8").call();

        RevCommit commit10 = geogig.command(MergeOp.class).setMessage("commit10")
                .addCommit(commit9.getId()).call().getMergeCommit();

        CheckSparsePath command = geogig.command(CheckSparsePath.class);

        assertTrue(command.setStart(commit7.getId()).setEnd(commit1.getId()).call());
        assertFalse(command.setStart(commit6.getId()).setEnd(commit1.getId()).call());
        assertTrue(command.setStart(commit5.getId()).setEnd(commit2.getId()).call());
        assertFalse(command.setStart(commit10.getId()).setEnd(commit1.getId()).call());
        assertFalse(command.setStart(commit10.getId()).setEnd(commit3.getId()).call());
        assertFalse(command.setStart(commit8.getId()).setEnd(commit1.getId()).call());
        assertFalse(command.setStart(commit4.getId()).setEnd(commit2.getId()).call());
        assertFalse(command.setStart(commit7.getId()).setEnd(commit5.getId()).call());

    }

    @Test
    public void testCheckSparsePath2() throws Exception {
        // Create the following revision graph
        // o - commit1
        // |\
        // | o - commit2 (sparse)
        // | |
        // | o - commit3
        // | |\
        // | | o - commit4
        // | | |\
        // | | | o - commit5 (sparse)
        // | | | |
        // | | o | - commit6
        // | | |/
        // | | o - commit7
        // | |
        // o | - commit8
        // | |
        // | o - commit9
        // |/
        // o - commit10
        insertAndAdd(points1);
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("commit1").call();

        // create branch1 and checkout
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        RevCommit commit2 = geogig.command(CommitOp.class).setMessage("commit2").call();
        geogig.getRepository().graphDatabase().setProperty(commit2.getId(),
                GraphDatabase.SPARSE_FLAG, "true");
        insertAndAdd(points3);
        RevCommit commit3 = geogig.command(CommitOp.class).setMessage("commit3").call();
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch2").call();
        insertAndAdd(lines1);
        RevCommit commit4 = geogig.command(CommitOp.class).setMessage("commit4").call();
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch3").call();
        insertAndAdd(poly1);
        RevCommit commit5 = geogig.command(CommitOp.class).setMessage("commit5").call();
        geogig.getRepository().graphDatabase().setProperty(commit5.getId(),
                GraphDatabase.SPARSE_FLAG, "true");
        geogig.command(CheckoutOp.class).setSource("branch2").call();
        insertAndAdd(poly2);
        RevCommit commit6 = geogig.command(CommitOp.class).setMessage("commit6").call();
        RevCommit commit7 = geogig.command(MergeOp.class).setMessage("commit7")
                .addCommit(commit5.getId()).call().getMergeCommit();

        geogig.command(CheckoutOp.class).setSource("branch1").call();
        insertAndAdd(lines3);
        RevCommit commit9 = geogig.command(CommitOp.class).setMessage("commit9").call();

        // checkout master
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(lines2);
        RevCommit commit8 = geogig.command(CommitOp.class).setMessage("commit8").call();

        RevCommit commit10 = geogig.command(MergeOp.class).setMessage("commit10")
                .addCommit(commit9.getId()).call().getMergeCommit();

        CheckSparsePath command = geogig.command(CheckSparsePath.class);

        assertTrue(command.setStart(commit7.getId()).setEnd(commit1.getId()).call());
        assertTrue(command.setStart(commit6.getId()).setEnd(commit1.getId()).call());
        assertTrue(command.setStart(commit5.getId()).setEnd(commit2.getId()).call());
        assertTrue(command.setStart(commit10.getId()).setEnd(commit1.getId()).call());
        assertFalse(command.setStart(commit10.getId()).setEnd(commit3.getId()).call());
        assertFalse(command.setStart(commit8.getId()).setEnd(commit1.getId()).call());
        assertFalse(command.setStart(commit4.getId()).setEnd(commit2.getId()).call());
        assertFalse(command.setStart(commit7.getId()).setEnd(commit5.getId()).call());

    }
}