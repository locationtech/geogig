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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.NothingToCommitException;
import org.locationtech.geogig.remotes.PullOp;
import org.locationtech.geogig.remotes.PullResult;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.test.TestData;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;

public class PRMergeOpTest {

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
        prinit.setContext(origin.getContext());
        request = prinit.call();
        assertNotNull(request);

    }

    private PRStatus merge() {
        return origin.getRepo().command(PRMergeOp.class).setId(request.getId()).call();
    }

    private GeogigTransaction getTransaction() {
        GeogigTransaction prtx = request.getTransaction(origin.getRepo().context());
        assertNotNull(prtx);
        return prtx;
    }

    private void createConflicts(SimpleFeature... features) {
        for (SimpleFeature base : features) {
            SimpleFeature toOrigin = TestData.clone(base);
            toOrigin.setAttribute("sp", "modified on origin");

            origin.checkout("master").insert(toOrigin).add()
                    .commit(String.format("change %s on origin", base.getID()));

            SimpleFeature toClone = TestData.clone(base);
            toClone.setAttribute("sp", "modified on fork");

            clone.checkout("issuerBranch").insert(toClone).add()
                    .commit(String.format("change %s on clone", base.getID()));
        }
    }

    public @Test void mergeNothingToCommit() {
        clone.checkout("issuerBranch").resetHard(origin.getRef("master").getObjectId());

        try {
            merge();
            fail("Expected NothingToCommitException");
        } catch (NothingToCommitException expected) {
            assertTrue(true);
        }
    }

    public @Test void mergeUpToDateNoConflicts() {
        PRStatus result = merge();
        assertTrue(result.getReport().isPresent());
        MergeScenarioReport mergeReport = result.getReport().get();
        assertNotNull(mergeReport);
        assertEquals(6, mergeReport.getUnconflicted());
        assertTrue(result.getMergeCommit().isPresent());
        assertTrue(result.isClosed());
        assertTrue(result.isMerged());
        assertFalse(result.isConflicted());
        assertFalse(result.isTargetBranchBehind());
        assertFalse(result.isRemoteBranchBehind());

        PRStatus status = getPrStatus();
        assertEquals(result.getRequest(), status.getRequest());
        assertTrue(status.isClosed());
        assertTrue(status.isMerged());
    }

    public @Test void mergeConflictingOutdatedBranches() {
        createConflicts(TestData.point1, TestData.line1, TestData.poly1);
        {
            PRStatus status = getPrStatus();
            assertTrue(status.isRemoteBranchBehind());
            assertTrue(status.isTargetBranchBehind());
            assertEquals(0, status.getNumConflicts());
            assertFalse(status.getMergeCommit().isPresent());
            assertFalse(status.getReport().isPresent());
        }
        mergeExpectConflicts(3);

        final PRStatus result = getPrStatus();
        assertFalse(result.isMerged());
        assertFalse(result.isClosed());

        assertFalse(result.isTargetBranchBehind());
        assertFalse(result.isRemoteBranchBehind());
        assertEquals(3, result.getNumConflicts());

        GeogigTransaction prtx = getTransaction();
        Optional<Ref> mergeRef = request.resolveMergeRef(prtx);
        assertFalse(mergeRef.isPresent());
    }

    public @Test void mergeFailsOnConflictsSucceedsAfterResolvingWhileBranchesGotOutdated() {
        createConflicts(TestData.point1, TestData.line1, TestData.poly1);
        mergeExpectConflicts(3);

        final UUID transactionId = request.getTransactionId();
        origin.resumeTransaction(transactionId);
        assertEquals(request.getTargetBranch(), origin.getRef("HEAD").peel().localName());

        //@formatter:off
        SimpleFeature c1 = TestData.clone(TestData.point1); c1.setAttribute("sp", "manually set");
        SimpleFeature c2 = TestData.clone(TestData.line1);  c2.setAttribute("sp", "manually set");
        SimpleFeature c3 = TestData.clone(TestData.poly1);  c3.setAttribute("sp", "manually set");
        //@formatter:on

        Context context = clone.checkout("issuerBranch").getContext();
        try {
            PullResult pres = context.command(PullOp.class).setIncludeIndexes(true)
                    .addRefSpec("master").call();
            fail("Expected MergeConflictsException , got " + pres);
        } catch (MergeConflictsException e) {
            assertEquals(3, e.getReport().getConflicts());
            clone.insert(c1, c2, c3).add().commit("manual conflict fix");
        }

        // conflicts resolved, but get the PR issuer and target branches run behind the live
        // branches
        origin.exitFromTransaction().checkout("master").insert(TestData.point2, TestData.point3)
                .add().commit("run ahead from target branch");
        clone.checkout("issuerBranch").insert(TestData.line2, TestData.line3).add()
                .commit("run ahead from issuer branch");

        PRStatus result = merge();
        assertTrue(result.isMerged());
        assertTrue(result.isClosed());
        assertTrue(result.getMergeCommit().isPresent());
        assertTrue(result.getReport().isPresent());

        Map<String, NodeRef> features = origin.getFeatureNodes("HEAD");
        Set<String> expected = Sets.newHashSet("Points/Point.1", "Points/Point.2", "Points/Point.3",
                "Polygons/Polygon.1", "Polygons/Polygon.4", "Lines/Line.3", "Lines/Line.1",
                "Lines/Line.2");
        assertEquals(expected, features.keySet());
    }

    /**
     * A test merge first failed, then the target and issuer branches got outdated. The conflicts of
     * the previous test run are not being resolved (nothing changed on the working tree). So a
     * second test merge discards the previous conflicts and runs fresh from the live branches.
     * Since the changes in both branches add more conflicts, the end result has all conflicts.
     */
    public @Test void mergeDiscardsPreviousConflictsNotBeingActivelyResolvedAndAddsConflicts() {
        createConflicts(TestData.point1, TestData.line1, TestData.poly1);
        mergeExpectConflicts(3);
        // got the conflicts from the first test merge run, now get the target and issuer branches
        // updated so the PR runs behind on both ends

        createConflicts(TestData.point2, TestData.line2, TestData.poly2);
        assertTrue(getPrStatus().isTargetBranchBehind());
        assertTrue(getPrStatus().isRemoteBranchBehind());
        assertEquals(3, getPrStatus().getNumConflicts());// prepare didn't run again, so no updated
                                                         // list of conflicts

        // now is should discard previous commits and proceed as if it was new
        mergeExpectConflicts(6);
        PRStatus result = getPrStatus();
        assertFalse(result.isClosed());
        assertFalse(result.isMerged());
        assertFalse(result.isTargetBranchBehind());
        assertFalse(result.isRemoteBranchBehind());
        assertEquals(6, result.getNumConflicts());
        assertFalse(getPrStatus().isTargetBranchBehind());
        assertFalse(getPrStatus().isRemoteBranchBehind());
        assertEquals(6, getPrStatus().getNumConflicts());
    }

    /**
     * A test merge first failed, then the target and issuer branches got outdated. The conflicts of
     * the previous test run are not being resolved (nothing changed on the working tree). So a
     * second test merge discards the previous conflicts and runs fresh from the live branches.
     * Since the changes in both branches cancel out the conflicts, the test merge succeeds.
     */
    public @Test void mergeDiscardsPreviousConflictsNotBeingActivelyResolvedAndSucceeds() {
        createConflicts(TestData.point1, TestData.line1, TestData.poly1);
        mergeExpectConflicts(3);
        // got the conflicts from the first test merge run, now get the target and issuer branches
        // updated so the PR runs behind on both ends

        SimpleFeature r1 = TestData.clone(TestData.point1);
        SimpleFeature r2 = TestData.clone(TestData.line1);
        SimpleFeature r3 = TestData.clone(TestData.poly1);
        r1.setAttribute("sp", "same value at both ends cancel out the conflict");
        r2.setAttribute("sp", "same value at both ends cancel out the conflict");
        r3.setAttribute("sp", "same value at both ends cancel out the conflict");

        origin.checkout("master").insert(r1, r2, r3).add().commit("commit ahead on origin");
        clone.checkout("issuerBranch").insert(r1, r2, r3).add().commit("commit ahead on clone");

        assertTrue(getPrStatus().isTargetBranchBehind());
        assertTrue(getPrStatus().isRemoteBranchBehind());
        assertEquals(3, getPrStatus().getNumConflicts());// prepare didn't run again, so no updated
                                                         // list of conflicts

        PRStatus result = merge();// now is should discard previous commits and proceed as if it was
                                  // new
        assertTrue(result.isClosed());
        assertTrue(result.isMerged());
        assertFalse(result.isTargetBranchBehind());
        assertFalse(result.isRemoteBranchBehind());
        assertEquals(0, result.getNumConflicts());
        assertTrue(result.getMergeCommit().isPresent());
        assertTrue(result.getReport().isPresent());
    }

    private PRStatus getPrStatus() {
        PRStatus status = origin.getRepo().command(PRHealthCheckOp.class).setId(request.getId())
                .call();
        return status;
    }

    private void mergeExpectConflicts(int count) {
        try {
            PRStatus status = merge();
            fail("Expected MergeConflictsException, got " + status);
        } catch (MergeConflictsException expected) {
            assertTrue(expected.getMessage().contains(String.format("%,d conflict(s)", count)));
        }
    }
}
