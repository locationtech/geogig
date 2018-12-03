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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.plumbing.TransactionEnd;
import org.locationtech.geogig.plumbing.TransactionResolve;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.merge.ConflictsCountOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ConflictsException;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.repository.impl.GeogigTransaction;

import com.google.common.base.Optional;

public class GeogigTransactionTest extends RepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        injector.configDatabase().put("user.name", "groldan");
        injector.configDatabase().put("user.email", "groldan@boundlessgeo.com");
    }

    @Test
    public void testTransaction() throws Exception {
        LinkedList<RevCommit> expectedMain = new LinkedList<RevCommit>();
        LinkedList<RevCommit> expectedTransaction = new LinkedList<RevCommit>();

        // make a commit
        insertAndAdd(points1);
        RevCommit commit = geogig.command(CommitOp.class).call();
        expectedMain.addFirst(commit);
        expectedTransaction.addFirst(commit);

        // start a transaction
        GeogigTransaction t = geogig.command(TransactionBegin.class).call();

        // perform a commit in the transaction
        insertAndAdd(t, points2);
        commit = t.command(CommitOp.class).call();
        expectedTransaction.addFirst(commit);

        // Verify that the base repository is unchanged
        Iterator<RevCommit> logs = geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMain, logged);

        // Verify that the transaction has the commit
        logs = t.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedTransaction, logged);

        // Commit the transaction
        geogig.command(TransactionEnd.class).setTransaction(t).setRebase(true).call();

        // Verify that the base repository has the changes from the transaction
        logs = geogig.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedTransaction, logged);

    }

    @Test
    public void testResolveTransaction() throws Exception {
        GeogigTransaction tx = geogig.command(TransactionBegin.class).call();

        Optional<GeogigTransaction> txNew = geogig.command(TransactionResolve.class)
                .setId(tx.getTransactionId()).call();

        assertTrue(txNew.isPresent());
    }

    @Test
    public void testResolveNonexistentTransaction() throws Exception {
        Optional<GeogigTransaction> txNew = geogig.command(TransactionResolve.class)
                .setId(UUID.randomUUID()).call();

        assertFalse(txNew.isPresent());
    }

    @Test
    public void testSyncTransaction() throws Exception {
        LinkedList<RevCommit> expectedMain = new LinkedList<RevCommit>();
        LinkedList<RevCommit> expectedTransaction = new LinkedList<RevCommit>();

        // make a commit
        insertAndAdd(points1);
        RevCommit firstCommit = geogig.command(CommitOp.class).call();
        expectedMain.addFirst(firstCommit);
        expectedTransaction.addFirst(firstCommit);

        // start a transaction
        GeogigTransaction t = geogig.command(TransactionBegin.class).call();

        // perform a commit in the transaction
        insertAndAdd(t, points2);
        RevCommit transactionCommit = t.command(CommitOp.class).call();
        expectedTransaction.addFirst(transactionCommit);

        // perform a commit on the repo
        insertAndAdd(points3);
        RevCommit repoCommit = geogig.command(CommitOp.class).call();
        expectedMain.addFirst(repoCommit);

        // Verify that the base repository is unchanged
        Iterator<RevCommit> logs = geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMain, logged);

        // Verify that the transaction has the commit
        logs = t.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedTransaction, logged);

        // Commit the transaction
        geogig.command(TransactionEnd.class).setTransaction(t).call();

        // Verify that a merge commit was created
        logs = geogig.command(LogOp.class).call();
        RevCommit lastCommit = logs.next();
        assertFalse(lastCommit.equals(repoCommit));
        assertTrue(lastCommit.getMessage().contains("Merge commit"));
        assertEquals(lastCommit.getParentIds().get(0), transactionCommit.getId());
        assertEquals(lastCommit.getParentIds().get(1), repoCommit.getId());
        assertEquals(logs.next(), repoCommit);
        assertEquals(logs.next(), transactionCommit);
        assertEquals(logs.next(), firstCommit);
        assertFalse(logs.hasNext());

    }

    @Test
    public void testTransactionAuthor() throws Exception {
        LinkedList<RevCommit> expectedMain = new LinkedList<RevCommit>();
        LinkedList<RevCommit> expectedTransaction = new LinkedList<RevCommit>();

        // make a commit
        insertAndAdd(points1);
        RevCommit firstCommit = geogig.command(CommitOp.class).call();
        expectedMain.addFirst(firstCommit);
        expectedTransaction.addFirst(firstCommit);

        // start a transaction
        GeogigTransaction t = geogig.command(TransactionBegin.class).call();

        t.setAuthor("Transaction Author", "transaction@author.com");

        // perform a commit in the transaction
        insertAndAdd(t, points2);
        RevCommit transactionCommit = t.command(CommitOp.class).call();
        expectedTransaction.addFirst(transactionCommit);

        // perform a commit on the repo
        insertAndAdd(points3);
        RevCommit repoCommit = geogig.command(CommitOp.class).call();
        expectedMain.addFirst(repoCommit);

        // Verify that the base repository is unchanged
        Iterator<RevCommit> logs = geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMain, logged);

        // Verify that the transaction has the commit
        logs = t.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedTransaction, logged);

        // Commit the transaction
        t.commitSyncTransaction();

        // Verify that a merge commit was created
        logs = geogig.command(LogOp.class).call();
        RevCommit lastCommit = logs.next();
        assertFalse(lastCommit.equals(repoCommit));
        assertTrue(lastCommit.getMessage().contains("Merge commit"));
        assertEquals(lastCommit.getParentIds().get(0), transactionCommit.getId());
        assertEquals(lastCommit.getParentIds().get(1), repoCommit.getId());
        assertEquals("Transaction Author", lastCommit.getAuthor().getName().get());
        assertEquals("transaction@author.com", lastCommit.getAuthor().getEmail().get());
        assertEquals(logs.next(), repoCommit);
        assertEquals(logs.next(), transactionCommit);
        assertEquals(logs.next(), firstCommit);
        assertFalse(logs.hasNext());

    }

    @Test
    public void testMultipleTransaction() throws Exception {

        // make a commit
        insertAndAdd(points1);
        RevCommit mainCommit = geogig.command(CommitOp.class).setMessage("Commit1").call();

        // start the first transaction
        GeogigTransaction transaction1 = geogig.command(TransactionBegin.class).call();

        // perform a commit in the transaction
        insertAndAdd(transaction1, points2);
        RevCommit transaction1Commit = transaction1.command(CommitOp.class).setMessage("Commit2")
                .call();

        // Verify that the base repository is unchanged
        Iterator<RevCommit> logs = geogig.command(LogOp.class).call();
        assertEquals(mainCommit, logs.next());
        assertFalse(logs.hasNext());

        // Verify that the transaction has the commit
        logs = transaction1.command(LogOp.class).call();
        assertEquals(transaction1Commit, logs.next());
        assertEquals(mainCommit, logs.next());
        assertFalse(logs.hasNext());

        // start the second transaction
        GeogigTransaction transaction2 = geogig.command(TransactionBegin.class).call();

        // perform a commit in the transaction
        insertAndAdd(transaction2, points3);
        RevCommit transaction2Commit = transaction2.command(CommitOp.class).setMessage("Commit3")
                .call();

        // Verify that the base repository is unchanged
        logs = geogig.command(LogOp.class).call();
        assertEquals(mainCommit, logs.next());
        assertFalse(logs.hasNext());

        // Verify that the transaction has the commit
        logs = transaction2.command(LogOp.class).call();
        assertEquals(transaction2Commit, logs.next());
        assertEquals(mainCommit, logs.next());
        assertFalse(logs.hasNext());

        // Commit the first transaction
        geogig.command(TransactionEnd.class).setTransaction(transaction1).setRebase(true).call();

        // Verify that the base repository has the changes from the transaction
        logs = geogig.command(LogOp.class).call();
        assertEquals(transaction1Commit, logs.next());
        assertEquals(mainCommit, logs.next());
        assertFalse(logs.hasNext());

        // Now try to commit the second transaction
        geogig.command(TransactionEnd.class).setTransaction(transaction2).setRebase(true).call();

        // Verify that the base repository has the changes from the transaction
        logs = geogig.command(LogOp.class).call();
        RevCommit lastCommit = logs.next();
        assertFalse(lastCommit.equals(transaction2Commit));
        assertEquals(lastCommit.getMessage(), transaction2Commit.getMessage());
        assertEquals(lastCommit.getAuthor(), transaction2Commit.getAuthor());
        assertEquals(lastCommit.getCommitter().getName(),
                transaction2Commit.getCommitter().getName());
        assertFalse(lastCommit.getCommitter().getTimestamp() == transaction2Commit.getCommitter()
                .getTimestamp());
        assertEquals(logs.next(), transaction1Commit);
        assertEquals(logs.next(), mainCommit);
        assertFalse(logs.hasNext());

    }

    @Test
    public void testConflictIsolation() throws Exception {
        insertAndAdd(points2);
        geogig.command(CommitOp.class).setMessage("foo").call();
        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points1);
        RevCommit mainCommit = geogig.command(CommitOp.class).setMessage("Commit1").call();
        geogig.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points1_modified);
        RevCommit modifiedCommit = geogig.command(CommitOp.class).setMessage("Commit2").call();
        assertNotNull(modifiedCommit);
        GeogigTransaction tx = geogig.command(TransactionBegin.class).call();
        try {
            tx.command(MergeOp.class).addCommit(mainCommit.getId()).call();
            fail("Expected a merge conflict!");
        } catch (org.locationtech.geogig.porcelain.MergeConflictsException e) {
            // expected.
        }
        long txConflicts = tx.command(ConflictsCountOp.class).call().longValue();
        long baseConflicts = geogig.command(ConflictsCountOp.class).call().longValue();
        assertTrue("There should be no conflicts outside the transaction", baseConflicts == 0);
        assertTrue("There should be conflicts in the transaction", txConflicts > 0);
    }

    @Test
    public void testBranchCreateCollision() throws Exception {

        // make a commit
        insertAndAdd(points1);
        RevCommit mainCommit = geogig.command(CommitOp.class).setMessage("Commit1").call();

        // start the first transaction
        GeogigTransaction transaction1 = geogig.command(TransactionBegin.class).call();

        // make a new branch
        transaction1.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();

        // perform a commit in the transaction
        insertAndAdd(transaction1, points2);
        RevCommit transaction1Commit = transaction1.command(CommitOp.class).setMessage("Commit2")
                .call();

        // Verify that the base repository is unchanged
        Iterator<RevCommit> logs = geogig.command(LogOp.class).call();
        assertEquals(logs.next(), mainCommit);
        assertFalse(logs.hasNext());

        // Verify that the transaction has the commit
        logs = transaction1.command(LogOp.class).call();
        assertEquals(logs.next(), transaction1Commit);
        assertEquals(logs.next(), mainCommit);
        assertFalse(logs.hasNext());

        // start the second transaction
        GeogigTransaction transaction2 = geogig.command(TransactionBegin.class).call();

        // make a new branch
        transaction2.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();

        // perform a commit in the transaction
        insertAndAdd(transaction2, points3);
        RevCommit transaction2Commit = transaction2.command(CommitOp.class).setMessage("Commit3")
                .call();

        // Verify that the base repository is unchanged
        logs = geogig.command(LogOp.class).call();
        assertEquals(logs.next(), mainCommit);
        assertFalse(logs.hasNext());

        // Verify that the transaction has the commit
        logs = transaction2.command(LogOp.class).call();
        assertEquals(logs.next(), transaction2Commit);
        assertEquals(logs.next(), mainCommit);
        assertFalse(logs.hasNext());

        // Commit the first transaction
        geogig.command(TransactionEnd.class).setTransaction(transaction1).setRebase(true).call();

        // Verify that the base repository has the changes from the transaction
        logs = geogig.command(LogOp.class).call();
        assertEquals(logs.next(), mainCommit);
        assertFalse(logs.hasNext());

        geogig.command(CheckoutOp.class).setSource("branch1").call();
        logs = geogig.command(LogOp.class).call();
        assertEquals(logs.next(), transaction1Commit);
        assertEquals(logs.next(), mainCommit);
        assertFalse(logs.hasNext());

        // Now try to commit the second transaction
        geogig.command(TransactionEnd.class).setTransaction(transaction2).setRebase(true).call();

        // Verify that the base repository has the changes from the transaction
        logs = geogig.command(LogOp.class).call();
        RevCommit lastCommit = logs.next();
        assertFalse(lastCommit.equals(transaction2Commit));
        assertEquals(lastCommit.getMessage(), transaction2Commit.getMessage());
        assertEquals(lastCommit.getAuthor(), transaction2Commit.getAuthor());
        assertEquals(lastCommit.getCommitter().getName(),
                transaction2Commit.getCommitter().getName());
        assertFalse(lastCommit.getCommitter().getTimestamp() == transaction2Commit.getCommitter()
                .getTimestamp());
        assertEquals(logs.next(), transaction1Commit);
        assertEquals(logs.next(), mainCommit);
        assertFalse(logs.hasNext());

    }

    @Test
    public void testEndTransactionConflict() throws Exception {

        // make a commit
        insertAndAdd(points1);
        RevCommit mainCommit = geogig.command(CommitOp.class).setMessage("Commit1").call();

        // start the transaction
        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        // perform a commit in the transaction
        insertAndAdd(transaction, points1_modified);
        RevCommit transactionCommit = transaction.command(CommitOp.class).setMessage("Commit2")
                .call();

        // Verify that the base repository is unchanged
        Iterator<RevCommit> logs = geogig.command(LogOp.class).call();
        assertEquals(logs.next(), mainCommit);
        assertFalse(logs.hasNext());

        // Verify that the transaction has the commit
        logs = transaction.command(LogOp.class).call();
        assertEquals(logs.next(), transactionCommit);
        assertEquals(logs.next(), mainCommit);
        assertFalse(logs.hasNext());

        // remove the feature in the base repository
        deleteAndAdd(points1);
        RevCommit mainCommit2 = geogig.command(CommitOp.class).setMessage("Commit3").call();

        // commit the transaction
        try {
            geogig.command(TransactionEnd.class).setTransaction(transaction).call();
            fail("Expected a conflict!");
        } catch (ConflictsException e) {
            // expected
        }

        long txConflicts = transaction.command(ConflictsCountOp.class).call().longValue();
        long baseConflicts = geogig.command(ConflictsCountOp.class).call().longValue();
        assertTrue("There should be no conflicts outside the transaction", baseConflicts == 0);
        assertTrue("There should be conflicts in the transaction", txConflicts == 1);

        // make sure the transaction is still resolvable
        Optional<GeogigTransaction> resolvedTransaction = geogig.command(TransactionResolve.class)
                .setId(transaction.getTransactionId()).call();
        assertTrue(resolvedTransaction.isPresent());

        // resolve the conflict in the transaction
        deleteAndAdd(transaction, points1);
        RevCommit resolvedCommit = transaction.command(CommitOp.class)
                .setMessage("Resolved Conflict").call();

        geogig.command(TransactionEnd.class).setTransaction(transaction).call();

        // Verify that the base repository has the changes from the transaction
        logs = geogig.command(LogOp.class).call();
        RevCommit lastCommit = logs.next();
        assertTrue(lastCommit.equals(resolvedCommit));
        assertEquals(lastCommit.getMessage(), resolvedCommit.getMessage());
        assertEquals(lastCommit.getAuthor(), resolvedCommit.getAuthor());
        assertEquals(lastCommit.getCommitter().getName(), resolvedCommit.getCommitter().getName());
        assertTrue(lastCommit.getCommitter().getTimestamp() == resolvedCommit.getCommitter()
                .getTimestamp());
        assertEquals(mainCommit2, logs.next());
        assertEquals(transactionCommit, logs.next());
        assertEquals(mainCommit, logs.next());

    }

    @Test
    public void testCancelTransaction() throws Exception {
        LinkedList<RevCommit> expectedMain = new LinkedList<RevCommit>();

        // make a commit
        insertAndAdd(points1);
        RevCommit commit = geogig.command(CommitOp.class).call();
        expectedMain.addFirst(commit);

        // start a transaction
        GeogigTransaction t = geogig.command(TransactionBegin.class).call();

        // perform a commit in the transaction
        insertAndAdd(t, points2);
        commit = t.command(CommitOp.class).call();

        // Verify that the base repository is unchanged
        Iterator<RevCommit> logs = geogig.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMain, logged);

        // Cancel the transaction
        geogig.command(TransactionEnd.class).setCancel(true).setTransaction(t).call();

        // Verify that the base repository is unchanged
        logs = geogig.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expectedMain, logged);

    }

    @Test
    public void testEndNoTransaction() throws Exception {
        exception.expect(IllegalArgumentException.class);
        geogig.command(TransactionEnd.class).call();
    }

    @Test
    public void testEndWithinTransaction() throws Exception {
        // make a commit
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();

        // start a transaction
        GeogigTransaction t = geogig.command(TransactionBegin.class).call();

        // perform a commit in the transaction
        insertAndAdd(t, points2);
        t.command(CommitOp.class).call();

        // End the transaction
        exception.expect(IllegalStateException.class);
        t.command(TransactionEnd.class).setTransaction(t).call();

    }

    @Test
    public void testBeginWithinTransaction() throws Exception {
        // make a commit
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();

        // start a transaction
        GeogigTransaction t = geogig.command(TransactionBegin.class).call();

        // start a transaction within the transaction
        exception.expect(IllegalStateException.class);
        t.command(TransactionBegin.class).call();

    }

    @Test
    public void testCommitUpdatesRemoteRefs() throws Exception {
        // make a commit
        insertAndAdd(points1);
        RevCommit headCommit = geogig.command(CommitOp.class).call();

        final String remoteRef = "refs/remotes/upstream/master";
        final String unchangedRemoteRef = "refs/remotes/upstream/testbranch";

        Ref remoteHead = geogig.command(UpdateRef.class).setName(remoteRef)
                .setNewValue(headCommit.getId()).call().get();
        assertEquals(headCommit.getId(), remoteHead.getObjectId());

        geogig.command(UpdateRef.class).setName(unchangedRemoteRef).setNewValue(headCommit.getId())
                .call().get();

        // start a transaction
        GeogigTransaction tx = geogig.command(TransactionBegin.class).call();

        // make a commit
        insertAndAdd(tx, points2);
        RevCommit newcommit = tx.command(CommitOp.class).call();
        // upadte remote
        Ref txRemoteHead = tx.command(UpdateRef.class).setName(remoteRef)
                .setNewValue(newcommit.getId()).call().get();
        assertEquals(newcommit.getId(), txRemoteHead.getObjectId());

        // commit transaction
        tx.commit();
        txRemoteHead = geogig.command(RefParse.class).setName(remoteRef).call().get();
        assertEquals(newcommit.getId(), txRemoteHead.getObjectId());

        txRemoteHead = geogig.command(RefParse.class).setName(unchangedRemoteRef).call().get();
        assertEquals(headCommit.getId(), txRemoteHead.getObjectId());
    }

    @Test
    public void testEndTransactionFromTransactionId() throws Exception {
        GeogigTransaction tx = geogig.command(TransactionBegin.class).call();

        // Use transaction id from the TransactionBegin to create another transaction that
        // can be used to perform other actions such as pull, push, and end. The web api
        // for example needs a way to retrieve/recreate a transaction object using a transactionId
        // without calling GeogigTransaction.create() since the transaction was previously created.
        Optional<GeogigTransaction> txNew = geogig.command(TransactionResolve.class)
                .setId(tx.getTransactionId()).call();

        assertTrue(txNew.isPresent());

        TransactionEnd endTransaction = geogig.command(TransactionEnd.class);
        boolean closed = endTransaction.setCancel(false).setTransaction(txNew.get()).call();
        assertTrue(closed);
    }
}
