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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.ResetOp;
import org.locationtech.geogig.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.remotes.FetchOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.test.TestData;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;

public class PRHealthCheckOpTest {

    public @Rule TestSupport testSupport = new TestSupport();

    /**
     * <pre>
     * <code>
     * 
     *  master o-----------------------------------------
     *            (initial commit has Point.1, Line.1, Polygon.1)  
     * </code>
     * </pre>
     */
    private TestData origin;

    /**
     * <pre>
     * <code>  issuerBranch  remove Line.1   add Polygon.4   modify Point.1
     *           ---------------o---------------o----------------o
     *          /                                        
     *  master o-----------------------------------------
     *            (initial commit has Point.1, Line.1, Polygon.1)  
     * </code>
     * </pre>
     */
    private TestData clone;

    private RevCommit commonAncestor;

    private PR request;

    public @Before void before() {
        origin = testSupport.newRepo("origin");
        origin.loadDefaultData();
        clone = testSupport.clone(origin, "clone");

        commonAncestor = Iterators.getLast(origin.log("master"));
        origin.resetHard(commonAncestor.getId());

        TestData.point1_modified.setAttribute("sp", "modified by clone");
        clone.branchAndCheckout("issuerBranch")//
                .resetHard(commonAncestor.getId())//
                .remove(TestData.line1).add().commit("remove line1")//
                .insert(TestData.poly4).add().commit("add poly 4")//
                .insert(TestData.point1_modified).add().commit("modify point1");

        PRInitOp prinit = PRInitOp.builder()//
                .id(1)//
                .remoteURI(clone.getRepo().getLocation())//
                .remoteBranch("issuerBranch")//
                .targetBranch("master")//
                .title("first PR")//
                .description(null)//
                .build();
        prinit.setContext(origin.getRepo().context());
        request = prinit.call();
        assertNotNull(request);

    }

    public @Test void healthCheckAfterInitBeforePrepare() {
        PRStatus status = origin.getRepo().command(PRHealthCheckOp.class).setId(request.getId())
                .call();
        assertNotNull(status);
        assertEquals(request, status.getRequest());

        Optional<ObjectId> mergeCommit = status.getMergeCommit();
        assertFalse(mergeCommit.toString(), mergeCommit.isPresent());
        // PR was just inited, not prepared, so it's behind the remote branch
        assertEquals(3, status.getCommitsBehindRemoteBranch());
        assertEquals(0, status.getCommitsBehindTargetBranch());
        assertEquals(0, status.getNumConflicts());
    }

    public @Test void healthUpToDate() {
        fakeUpToDate();// pull from remote

        PRStatus status = origin.getRepo().command(PRHealthCheckOp.class).setId(request.getId())
                .call();
        assertNotNull(status);
        assertEquals(request, status.getRequest());

        assertEquals(Sets.newHashSet("Lines", "Points", "Polygons"),
                Sets.newHashSet(status.getAffectedLayers()));

        Optional<ObjectId> mergeCommit = status.getMergeCommit();
        assertFalse(mergeCommit.toString(), mergeCommit.isPresent());
        assertEquals(0, status.getCommitsBehindRemoteBranch());
        assertEquals(0, status.getCommitsBehindTargetBranch());
        assertEquals(0, status.getNumConflicts());
    }

    public @Test void healthCheckNoChanges() {
        // get target repo up to date with issuer repo
        {
            fakeUpToDate();// pull from remote
            PR pr = this.request;
            Ref targetBranch = pr.resolveTargetBranch(origin.getRepo().context());
            Ref remoteBranch = pr.resolveRemoteBranch(clone.getRepo());
            Context liveContext = origin.getContext();
            origin.checkout(pr.getTargetBranch()).resetHard(remoteBranch.getObjectId());

            Context txContext = origin.resumeTransaction(pr.getTransactionId())
                    .checkout(pr.getTargetBranch()).resetHard(remoteBranch.getObjectId())
                    .getContext();
            origin.exitFromTransaction();

            txContext.command(UpdateRef.class).setName(pr.getHeadRef())
                    .setNewValue(remoteBranch.getObjectId()).call();
            targetBranch = pr.resolveTargetBranch(liveContext);
            assertEquals(targetBranch.getObjectId(), remoteBranch.getObjectId());

            targetBranch = pr.resolveTargetBranch(txContext);
            assertEquals(targetBranch.getObjectId(), remoteBranch.getObjectId());
        }
        PRStatus status = origin.getRepo().command(PRHealthCheckOp.class).setId(request.getId())
                .call();
        assertNotNull(status);
        assertEquals(request, status.getRequest());

        assertEquals(Collections.emptyList(), status.getAffectedLayers());

        Optional<ObjectId> mergeCommit = status.getMergeCommit();
        assertFalse(mergeCommit.toString(), mergeCommit.isPresent());
        assertEquals(0, status.getCommitsBehindRemoteBranch());
        assertEquals(0, status.getCommitsBehindTargetBranch());
        assertEquals(0, status.getNumConflicts());
    }

    public @Test void healthCheckBehindTargetBranch() {
        // fake PRPrepareOp
        fakeUpToDate();

        origin.checkout("master").insert(TestData.line2).add().commit("l2").insert(TestData.line3)
                .add().commit("l3");

        PRStatus status = origin.getRepo().command(PRHealthCheckOp.class).setId(request.getId())
                .call();
        assertNotNull(status);
        assertEquals(request, status.getRequest());
        assertEquals(Sets.newHashSet("Lines", "Points", "Polygons"),
                Sets.newHashSet(status.getAffectedLayers()));

        Optional<ObjectId> mergeCommit = status.getMergeCommit();
        assertFalse(mergeCommit.toString(), mergeCommit.isPresent());
        assertEquals(0, status.getCommitsBehindRemoteBranch());
        assertEquals(2, status.getCommitsBehindTargetBranch());
        assertEquals(0, status.getNumConflicts());
    }

    public @Test void healthCheckWithConflicts() {
        // fake PRPrepareOp
        fakeUpToDate();

        TestData.point1_modified.setAttribute("sp", "changed on pr target repo");
        origin.checkout("master").insert(TestData.point1_modified).add().commit("ci for conflict");

        GeogigTransaction tx = request.getTransaction(origin.getRepo().context());
        tx.command(UpdateRef.class).setName("refs/heads/" + request.getTargetBranch())
                .setNewValue(origin.getRef(request.getTargetBranch()).getObjectId());

        tx.command(CheckoutOp.class).setSource(request.getTargetBranch()).call();
        tx.command(ResetOp.class).setCommit(origin.getRef(request.getTargetBranch()).getObjectId())
                .setMode(ResetMode.HARD).call();
        ObjectId localRemoteCommit = clone.getRef(request.getRemoteBranch()).getObjectId();
        try {
            tx.command(MergeOp.class).addCommit(localRemoteCommit).call();
            fail("Expected MergeConflictsException");
        } catch (MergeConflictsException expected) {
            //
        }
        PRStatus status = origin.getRepo().command(PRHealthCheckOp.class).setId(request.getId())
                .call();
        assertNotNull(status);
        assertEquals(request, status.getRequest());
        assertEquals(Sets.newHashSet("Lines", "Points", "Polygons"),
                Sets.newHashSet(status.getAffectedLayers()));

        Optional<ObjectId> mergeCommit = status.getMergeCommit();
        assertFalse(mergeCommit.toString(), mergeCommit.isPresent());
        assertEquals(0, status.getCommitsBehindRemoteBranch());
        assertEquals(1, status.getCommitsBehindTargetBranch());
        assertEquals(1, status.getNumConflicts());
    }

    public @Test void healthCheckTestMergeDoneButOutDated() {
        // fake PRPrepareOp
        fakeUpToDate();

        GeogigTransaction tx = request.getTransaction(origin.getRepo().context());
        tx.command(UpdateRef.class).setName("refs/heads/" + request.getTargetBranch())
                .setNewValue(origin.getRef(request.getTargetBranch()).getObjectId());

        tx.command(CheckoutOp.class).setSource(request.getTargetBranch()).call();
        tx.command(ResetOp.class).setCommit(origin.getRef(request.getTargetBranch()).getObjectId())
                .setMode(ResetMode.HARD).call();
        ObjectId localRemoteCommit = clone.getRef(request.getRemoteBranch()).getObjectId();

        RevCommit merge = tx.command(MergeOp.class).addCommit(localRemoteCommit).call()
                .getMergeCommit();

        tx.command(UpdateRef.class).setName(request.getMergeRef()).setNewValue(merge.getId())
                .call();

        origin.checkout("master").insert(TestData.line2).add().commit("ahead commit");

        PRStatus status = origin.getRepo().command(PRHealthCheckOp.class).setId(request.getId())
                .call();
        assertNotNull(status);
        assertEquals(request, status.getRequest());
        assertEquals(Sets.newHashSet("Lines", "Points", "Polygons"),
                Sets.newHashSet(status.getAffectedLayers()));
        Optional<ObjectId> mergeCommit = status.getMergeCommit();
        assertTrue(mergeCommit.toString(), mergeCommit.isPresent());

        assertEquals(0, status.getCommitsBehindRemoteBranch());
        assertEquals(1, status.getCommitsBehindTargetBranch());
        assertEquals(0, status.getNumConflicts());
    }

    private void fakeUpToDate() {
        Remote remote = request.buildRemote();
        GeogigTransaction tx = request.getTransaction(origin.getRepo().context());
        tx.command(FetchOp.class).setFetchIndexes(true).addRemote(remote).call();

        Ref remoteBranchState = clone.getRef(request.getRemoteBranch());
        Ref localRemoteBranchState = tx.command(RefParse.class).setName(request.getOriginRef())
                .call().get();
        assertEquals(remoteBranchState.getObjectId(), localRemoteBranchState.getObjectId());
    }
}
