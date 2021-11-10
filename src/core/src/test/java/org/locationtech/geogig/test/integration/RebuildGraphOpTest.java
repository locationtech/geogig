/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RebuildGraphOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.storage.GraphDatabase;

public class RebuildGraphOpTest extends RepositoryTestCase {

    protected GraphDatabase database;

    protected @Override void setUpInternal() throws Exception {
        // These values should be used during a commit to set author/committer
        // TODO: author/committer roles need to be defined better, but for
        // now they are the same thing.
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue("groldan").call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue("groldan@test.com").call();
        database = repo.context().graphDatabase();
    }

    @Test
    public void testRebuildGraphFull() throws Exception {
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
        final RevCommit c3 = repo.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        final RevCommit c4 = repo.command(CommitOp.class).setMessage("commit for " + idL1).call();

        // Delete the graph
        database.truncate();
        database.close();
        database.open();

        // Rebuild the graph
        List<ObjectId> updated = repo.command(RebuildGraphOp.class).call();
        assertEquals(4, updated.size());
        assertTrue(updated.contains(c1.getId()));
        assertTrue(updated.contains(c2.getId()));
        assertTrue(updated.contains(c3.getId()));
        assertTrue(updated.contains(c4.getId()));
    }

    @Test
    public void testRebuildGraphPartial() throws Exception {
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

        // Delete the graph
        database.truncate();
        database.close();
        database.open();

        // checkout master
        repo.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        final RevCommit c3 = repo.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        final RevCommit c4 = repo.command(CommitOp.class).setMessage("commit for " + idL1).call();

        // Rebuild the graph
        List<ObjectId> updated = repo.command(RebuildGraphOp.class).call();
        assertEquals(2, updated.size());
        assertTrue(updated.contains(c1.getId()));
        assertTrue(updated.contains(c2.getId()));
        assertFalse(updated.contains(c3.getId()));
        assertFalse(updated.contains(c4.getId()));
    }

    @Test
    public void testRebuildGraphWithNoErrors() throws Exception {
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
        repo.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        repo.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        repo.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        repo.command(CommitOp.class).setMessage("commit for " + idL1).call();

        // Rebuild the graph
        List<ObjectId> updated = repo.command(RebuildGraphOp.class).call();
        assertEquals(0, updated.size());
    }
}
