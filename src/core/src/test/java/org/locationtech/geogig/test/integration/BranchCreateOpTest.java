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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CommitOp;

import com.google.common.base.Optional;

public class BranchCreateOpTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        injector.configDatabase().put("user.name", "groldan");
        injector.configDatabase().put("user.email", "groldan@boundlessgeo.com");
    }

    @Test
    public void testCreateBranch() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("Commit1").call();
        geogig.command(BranchCreateOp.class).setName("branch1").setAutoCheckout(true).call();

        Optional<Ref> branch1 = geogig.command(RefParse.class).setName("branch1").call();

        assertTrue(branch1.isPresent());

        Optional<Ref> master = geogig.command(RefParse.class).setName("master").call();

        assertEquals(master.get().getObjectId(), branch1.get().getObjectId());
    }

    @Test
    public void testNullNameForBranch() {
        exception.expect(IllegalStateException.class);
        geogig.command(BranchCreateOp.class).setName(null).call();
    }

    @Test
    public void testInvalidNameForBranch() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Component of ref cannot have two consecutive dots (..) anywhere.");
        geogig.command(BranchCreateOp.class).setName("ma..er").call();
    }

    @Test
    public void testCreateBranchWithTheSameNameAsExistingBranch() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("Commit1").call();
        geogig.command(BranchCreateOp.class).setName("branch1").setAutoCheckout(true).call();

        exception.expect(IllegalArgumentException.class);
        geogig.command(BranchCreateOp.class).setName("branch1").call();
    }

    @Test
    public void testCreateBranchWithTheSameNameAsExistingBranchAndForce() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("Commit1").call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();
        insertAndAdd(points2);
        RevCommit newCommit = geogig.command(CommitOp.class).setMessage("Commit2").call();
        geogig.command(BranchCreateOp.class).setName("branch1").setForce(true).call();
        Optional<Ref> branch1 = geogig.command(RefParse.class).setName("branch1").call();
        assertTrue(branch1.isPresent());
        assertEquals(branch1.get().getObjectId(), newCommit.getId());
    }

    @Test
    public void testCreateBranchFromMasterWithNoCommitsMade() {
        exception.expect(IllegalArgumentException.class);
        geogig.command(BranchCreateOp.class).setName("branch1").call();
    }

    @Test
    public void testCreateBranchFromBranchOtherThanMaster() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("Commit1").call();
        geogig.command(BranchCreateOp.class).setName("branch1").setAutoCheckout(false).call();

        insertAndAdd(points2);
        geogig.command(CommitOp.class).setMessage("Commit2").call();
        geogig.command(BranchCreateOp.class).setName("branch2").setAutoCheckout(true)
                .setSource("branch1").call();

        Optional<Ref> branch1 = geogig.command(RefParse.class).setName("branch1").call();

        assertTrue(branch1.isPresent());

        Optional<Ref> branch2 = geogig.command(RefParse.class).setName("branch2").call();

        assertTrue(branch2.isPresent());

        assertEquals(branch1.get().getObjectId(), branch2.get().getObjectId());
    }

    @Test
    public void testCreateBranchFromCommit() throws Exception {
        insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("Commit1").call();
        insertAndAdd(points2);
        geogig.command(CommitOp.class).setMessage("Commit2").call();
        insertAndAdd(points3);
        geogig.command(CommitOp.class).setMessage("Commit3").call();

        geogig.command(BranchCreateOp.class).setName("branch1").setAutoCheckout(true)
                .setSource(c1.getId().toString()).call();

        Optional<Ref> branch1 = geogig.command(RefParse.class).setName("branch1").call();

        assertTrue(branch1.isPresent());

        assertEquals(c1.getId(), branch1.get().getObjectId());

        Optional<Ref> master = geogig.command(RefParse.class).setName("master").call();

        assertFalse(master.get().getObjectId().equals(branch1.get().getObjectId()));
    }

    @Test
    public void testCreateBranchFromNonExistentCommit() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("Commit1").call();

        exception.expect(IllegalArgumentException.class);
        geogig.command(BranchCreateOp.class).setName("branch1").setAutoCheckout(true)
                .setSource("Nonexistent Commit").call();
    }

    @Test
    public void testCreateBranchFromSomethingOtherThanCommit() throws Exception {
        insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("Commit1").call();
        insertAndAdd(points2);
        geogig.command(CommitOp.class).setMessage("Commit2").call();
        insertAndAdd(points3);
        geogig.command(CommitOp.class).setMessage("Commit3").call();

        exception.expect(IllegalArgumentException.class);
        geogig.command(BranchCreateOp.class).setName("branch1").setAutoCheckout(true)
                .setSource(c1.getTreeId().toString()).call();
    }

    @Test
    public void testOrphan() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("Commit1").call();
        Ref branch1 = geogig.command(BranchCreateOp.class).setName("branch1").setAutoCheckout(true)
                .setOrphan(true).call();

        assertEquals(ObjectId.NULL, branch1.getObjectId());
        assertEquals(Ref.HEADS_PREFIX + "branch1", branch1.getName());
    }
}
