/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.geogig.commands.pr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.TransactionResolve;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.test.TestData;

import com.google.common.base.Optional;
import com.google.common.collect.Iterators;

public class PRInitOpTest {

    public @Rule ExpectedException ex = ExpectedException.none();

    public @Rule TestSupport testSupport = new TestSupport();

    /**
     * <pre>
     * <code>
     *             (adds Points/Point.1, Lines/Line.2, Polygons/Polygon.2)
     *    branch1 o-------------------------------------
     *           /                                      \
     *          /                                        \  no ff merge
     *  master o------------------------------------------o-----------------o
     *          \  (initial commit has                                     / no ff merge
     *           \     Points/Point.1, Lines/Line.1, Polygons/Polygon.1)  /
     *            \                                                      /
     *             \                                                    /
     *     branch2  o--------------------------------------------------
     *             (adds Points/Point.3, Lines/Line.3, Polygons/Polygon.3)
     *
     * </code>
     * </pre>
     */
    private TestData origin;

    private TestData clone;

    public @Before void before() {
        origin = testSupport.newRepo("origin");
        origin.loadDefaultData();
        clone = testSupport.clone(origin, "clone");
    }

    public @Test void testCreatePullRequest() {
        final RevCommit commonAncestor = Iterators.getLast(origin.log("master"));

        clone.branchAndCheckout("issuerBranch")//
                .resetHard(commonAncestor.getId())//
                .remove(TestData.line1).add().commit("remove line1")//
                .insert(TestData.poly4).add().commit("add poly 4")//
                .insert(TestData.point1_modified).add().commit("modify point1");

        // System.err.println("Common ancestor: " + commonAncestor);
        // System.err.println(Lists.newArrayList(clone.log("issuerBranch")));
        PRInitOp prinit = PRInitOp.builder()//
                .id(1)//
                .remoteURI(clone.getRepo().getLocation())//
                .remoteBranch("issuerBranch")//
                .targetBranch("master")//
                .title("first PR")//
                .description(null)//
                .build();
        prinit.setContext(origin.getRepo().context());
        PR request = prinit.call();
        assertNotNull(request);

        assertEquals("issuerBranch", request.getRemoteBranch());
        assertEquals("master", request.getTargetBranch());
        assertNotNull(request.buildRemote());
        assertEquals(clone.getRepo().getLocation(), request.getRemote());
        assertEquals("first PR", request.getTitle());
        assertNull(request.getDescription());
        assertNotNull(request.getTransactionId());

        Optional<GeogigTransaction> transaction = origin.getRepo().command(TransactionResolve.class)
                .setId(request.getTransactionId()).call();
        assertTrue(transaction.isPresent());
        GeogigTransaction tx = transaction.get();

        Ref headRef = tx.command(RefParse.class).setName(request.getHeadRef()).call().orNull();
        Ref originRef = tx.command(RefParse.class).setName(request.getOriginRef()).call().orNull();
        Ref targetRef = tx.command(RefParse.class).setName(request.getTargetBranch()).call()
                .orNull();
        assertNotNull(headRef);
        assertNotNull(originRef);
        assertNotNull(targetRef);

        assertEquals(origin.getRef("master").getObjectId(), targetRef.getObjectId());
        assertEquals(commonAncestor.getId(), originRef.getObjectId());
        assertEquals(targetRef.getObjectId(), headRef.getObjectId());
        assertFalse(request.resolveMergeRef(tx).isPresent());
    }

    public @Test void testUpdatePullRequest() {
        final RevCommit commonAncestor = Iterators.getLast(origin.log("master"));

        clone.branchAndCheckout("issuerBranch")//
                .resetHard(commonAncestor.getId())//
                .remove(TestData.line1).add().commit("remove line1")//
                .insert(TestData.poly4).add().commit("add poly 4")//
                .insert(TestData.point1_modified).add().commit("modify point1");

        // System.err.println("Common ancestor: " + commonAncestor);
        // System.err.println(Lists.newArrayList(clone.log("issuerBranch")));
        PRInitOp prinit = PRInitOp.builder()//
                .id(1)//
                .remoteURI(clone.getRepo().getLocation())//
                .remoteBranch("issuerBranch")//
                .targetBranch("master")//
                .title("first PR")//
                .description(null)//
                .build();
        prinit.setContext(origin.getRepo().context());
        PR request = prinit.call();
        assertNotNull(request);

        assertEquals("issuerBranch", request.getRemoteBranch());
        assertEquals("master", request.getTargetBranch());
        assertNotNull(request.buildRemote());
        assertEquals(clone.getRepo().getLocation(), request.getRemote());
        assertEquals("first PR", request.getTitle());
        assertNull(request.getDescription());
        assertNotNull(request.getTransactionId());

        origin.branch("targetbranch");

        clone.checkout("master").branchAndCheckout("newbranch")
                .insert(TestData.point1_modified, TestData.poly1_modified1).add()
                .commit("on branch2");

        PRInitOp initModify = PRInitOp.builder()//
                .id(1)//
                .remoteURI(clone.getRepo().getLocation())//
                .remoteBranch("newbranch")//
                .targetBranch("targetbranch")//
                .title("modified first PR")//
                .description("modified description")//
                .build();
        initModify.setContext(origin.getRepo().context());

        // fake a test-merge
        GeogigTransaction tx = request.getTransaction(origin.getContext());
        tx.command(UpdateRef.class).setName(request.getMergeRef())
                .setNewValue(origin.getRef("master").getObjectId()).call();
        assertTrue(request.resolveMergeRef(tx).isPresent());

        PR modifiedReq = initModify.call();
        assertEquals("newbranch", modifiedReq.getRemoteBranch());
        assertEquals("targetbranch", modifiedReq.getTargetBranch());
        assertNotNull(request.buildRemote());
        assertEquals(clone.getRepo().getLocation(), request.getRemote());
        assertEquals("modified first PR", modifiedReq.getTitle());
        assertEquals("modified description", modifiedReq.getDescription());
        assertEquals(request.getTransactionId(), modifiedReq.getTransactionId());

        assertFalse(modifiedReq.resolveMergeRef(tx).isPresent());
    }

    public @Test void testReOpenPullRequest() {
        final RevCommit commonAncestor = Iterators.getLast(origin.log("master"));

        clone.branchAndCheckout("issuerBranch")//
                .resetHard(commonAncestor.getId())//
                .remove(TestData.line1).add().commit("remove line1")//
                .insert(TestData.poly4).add().commit("add poly 4")//
                .insert(TestData.point1_modified).add().commit("modify point1");

        // System.err.println("Common ancestor: " + commonAncestor);
        // System.err.println(Lists.newArrayList(clone.log("issuerBranch")));
        PRInitOp prinit = PRInitOp.builder()//
                .id(1)//
                .remoteURI(clone.getRepo().getLocation())//
                .remoteBranch("issuerBranch")//
                .targetBranch("master")//
                .title("first PR")//
                .description(null)//
                .build();
        Repository repo = origin.getRepo();
        prinit.setContext(repo.context());
        PR request = prinit.call();
        assertNotNull(request);

        assertNotNull(request.getTransactionId());
        final UUID txId = request.getTransactionId();

        PRStatus closeStatus = repo.command(PRCloseOp.class).setId(request.getId()).call();
        assertTrue(closeStatus.isClosed());
        assertFalse(repo.command(TransactionResolve.class).setId(txId).call().isPresent());
        java.util.Optional<PR> pr = repo.command(PRFindOp.class).setId(request.getId()).call();
        assertTrue(pr.isPresent());

        // re-open
        PR reopen = repo.command(PRInitOp.class).setId(request.getId()).call();
        UUID newtx = reopen.getTransactionId();
        assertNotNull(newtx);
        assertNotEquals(txId, newtx);
    }

    public @Test void tryReOpenMergedPullRequest() {
        final RevCommit commonAncestor = Iterators.getLast(origin.log("master"));

        clone.branchAndCheckout("issuerBranch")//
                .resetHard(commonAncestor.getId())//
                .remove(TestData.line1).add().commit("remove line1")//
                .insert(TestData.poly4).add().commit("add poly 4")//
                .insert(TestData.point1_modified).add().commit("modify point1");

        // System.err.println("Common ancestor: " + commonAncestor);
        // System.err.println(Lists.newArrayList(clone.log("issuerBranch")));
        PRInitOp prinit = PRInitOp.builder()//
                .id(1)//
                .remoteURI(clone.getRepo().getLocation())//
                .remoteBranch("issuerBranch")//
                .targetBranch("master")//
                .title("first PR")//
                .description(null)//
                .build();
        Repository repo = origin.getRepo();
        prinit.setContext(repo.context());
        PR request = prinit.call();

        PRStatus status = repo.command(PRMergeOp.class).setId(request.getId()).call();
        assertTrue(status.isMerged());

        ex.expect(IllegalStateException.class);
        ex.expectMessage("already merged");
        repo.command(PRInitOp.class).setId(request.getId()).call();
    }
}
