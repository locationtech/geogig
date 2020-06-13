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

import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.BranchDeleteOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.transaction.GeogigTransaction;
import org.locationtech.geogig.transaction.TransactionBegin;

public class BranchDeleteOpTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected @Override void setUpInternal() throws Exception {
        repo.context().configDatabase().put("user.name", "groldan");
        repo.context().configDatabase().put("user.email", "groldan@test.com");
    }

    @Test
    public void NoBranchNameTest() {
        BranchDeleteOp testOp = new BranchDeleteOp();
        testOp.setName(null);

        exception.expect(IllegalStateException.class);

        testOp.call();
    }

    @Test
    public void BranchNotPresentTest() {
        Optional<? extends Ref> branchref = repo.command(BranchDeleteOp.class).setName("noBranch")
                .call();
        assertEquals(Optional.empty(), branchref);
    }

    @Test
    public void BranchPresentTest() throws Exception {
        insertAndAdd(points1);
        repo.command(AddOp.class).call();
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        repo.command(BranchDeleteOp.class).setName("TestBranch").call();

        Optional<Ref> result = repo.command(RefParse.class).setName("TestBranch").call();

        assertFalse(result.isPresent());
    }

    @Test
    public void BranchIsHeadTest() throws Exception {
        insertAndAdd(points1);
        repo.command(AddOp.class).call();
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestMasterBranch").call();

        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();

        insertAndAdd(points2);
        repo.command(AddOp.class).call();
        repo.command(CommitOp.class).call();

        exception.expect(IllegalArgumentException.class);
        repo.command(BranchDeleteOp.class).setName("TestBranch").call();
    }

    @Test
    public void InvalidBranchNameTest() throws Exception {
        insertAndAdd(points1);
        repo.command(AddOp.class).call();
        repo.command(CommitOp.class).call();
        Ref testBranch = repo.command(BranchCreateOp.class).setName("TestBranch").call();

        testBranch = repo.command(UpdateRef.class).setName("TestBranch")
                .setNewValue(testBranch.getObjectId()).setReason("test init").call().get();

        exception.expect(IllegalArgumentException.class);
        repo.command(BranchDeleteOp.class).setName("TestBranch").call();
    }

    @Test
    public void DeleteInTransactionTest() throws Exception {
        insertAndAdd(points1);
        commit("points1");
        super.branch("branch1");
        super.branch("branch2");
        GeogigTransaction tx1 = repo.command(TransactionBegin.class).call();
        GeogigTransaction tx2 = repo.command(TransactionBegin.class).call();

        final Context noTx = repo.context();

        assertEquals("branch1", checkout(tx1, "branch1").getNewRef().localName());
        insertAndAdd(tx1, points2);
        commit(tx1, "added points2");
        assertEquals("master", checkout(tx1, "master").getNewRef().localName());

        Optional<? extends Ref> b1deleted = tx1.command(BranchDeleteOp.class).setName("branch1")
                .call();
        assertTrue(b1deleted.isPresent());
        // branch1 is deleted in tx1, but present in repo and tx2
        assertTrue(refParse(noTx, "branch1").isPresent());
        assertTrue(refParse(noTx, "branch2").isPresent());
        assertTrue(refParse(tx2, "branch1").isPresent());
        assertTrue(refParse(tx2, "branch2").isPresent());

        assertTrue(refParse(tx1, "branch2").isPresent());
        assertFalse(refParse(tx1, "branch1").isPresent());

        tx1.commit();
        // branch1 is no longer in the repo
        assertFalse(refParse(noTx, "branch1").isPresent());
        assertTrue(refParse(noTx, "branch2").isPresent());
        // tx2 still has both branches
        assertTrue(refParse(tx2, "branch1").isPresent());
        assertTrue(refParse(tx2, "branch2").isPresent());

        Optional<? extends Ref> b2deleted = tx2.command(BranchDeleteOp.class).setName("branch2")
                .call();
        assertTrue(b2deleted.isPresent());
        // tx2 still has branch1, branch2 is gone
        assertTrue(refParse(tx2, "branch1").isPresent());
        assertTrue(refParse(noTx, "branch2").isPresent());
        assertFalse(refParse(tx2, "branch2").isPresent());

        tx2.commit();
        assertFalse(refParse(noTx, "branch2").isPresent());
    }

    private Optional<Ref> refParse(Context context, String refName) {
        return context.command(RefParse.class).setName(refName).call();
    }
}
