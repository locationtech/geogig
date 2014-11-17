/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.test.integration;

import java.util.Iterator;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.plumbing.LsTreeOp;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.MergeConflictsException;
import org.locationtech.geogig.api.porcelain.MergeOp;
import org.locationtech.geogig.api.porcelain.RemoveOp;
import org.locationtech.geogig.api.porcelain.ResetOp;
import org.locationtech.geogig.api.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.repository.WorkingTree;
import org.opengis.feature.Feature;

import com.google.common.base.Optional;
import com.google.common.base.Suppliers;

public class RemoveOpTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Test
    public void testSingleFeatureRemoval() throws Exception {
        populate(false, points1, points2, points3);

        String featureId = points1.getIdentifier().getID();
        String path = NodeRef.appendChild(pointsName, featureId);
        geogig.command(RemoveOp.class).addPathToRemove(path).call();

        Optional<ObjectId> id = geogig.command(RevParse.class)
                .setRefSpec(Ref.WORK_HEAD + ":" + path).call();
        assertFalse(id.isPresent());
        id = geogig.command(RevParse.class).setRefSpec(Ref.STAGE_HEAD + ":" + path).call();
        assertFalse(id.isPresent());
    }

    @Test
    public void testMultipleRemoval() throws Exception {
        populate(false, points1, points2, points3);

        String featureId = points1.getIdentifier().getID();
        String path = NodeRef.appendChild(pointsName, featureId);
        String featureId2 = points2.getIdentifier().getID();
        String path2 = NodeRef.appendChild(pointsName, featureId2);

        geogig.command(RemoveOp.class).addPathToRemove(path).addPathToRemove(path2).call();

        Optional<ObjectId> id = geogig.command(RevParse.class)
                .setRefSpec(Ref.WORK_HEAD + ":" + path).call();
        assertFalse(id.isPresent());
        id = geogig.command(RevParse.class).setRefSpec(Ref.STAGE_HEAD + ":" + path).call();
        assertFalse(id.isPresent());
        id = geogig.command(RevParse.class).setRefSpec(Ref.WORK_HEAD + ":" + path2).call();
        assertFalse(id.isPresent());
        id = geogig.command(RevParse.class).setRefSpec(Ref.STAGE_HEAD + ":" + path2).call();
        assertFalse(id.isPresent());
    }

    @Test
    public void testTreeRemoval() throws Exception {
        populate(false, points1, points2, points3, lines1, lines2);

        geogig.command(RemoveOp.class).addPathToRemove(pointsName).call();
        Optional<ObjectId> id = geogig.command(RevParse.class)
                .setRefSpec(Ref.WORK_HEAD + ":" + pointsName).call();
        assertFalse(id.isPresent());
        id = geogig.command(RevParse.class).setRefSpec(Ref.STAGE_HEAD + ":" + pointsName).call();
        List<DiffEntry> list = toList(repo.index().getStaged(null));
        assertFalse(id.isPresent());
        id = geogig.command(RevParse.class).setRefSpec(Ref.STAGE_HEAD + ":" + linesName).call();
        assertTrue(id.isPresent());
    }

    @Test
    public void testUnexistentPathRemoval() throws Exception {
        populate(false, points1, points2, points3);

        try {
            geogig.command(RemoveOp.class).addPathToRemove("wrong/wrong.1").call();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testRemovalFixesConflict() throws Exception {
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        geogig.command(CommitOp.class).call();

        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        try {
            geogig.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch.getObjectId()))
                    .call();
            fail();
        } catch (MergeConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }
        String path = NodeRef.appendChild(pointsName, idP1);
        geogig.command(RemoveOp.class).addPathToRemove(path).call();
        List<Conflict> conflicts = geogig.getRepository().stagingDatabase()
                .getConflicts(null, null);
        assertTrue(conflicts.isEmpty());
        geogig.command(CommitOp.class).call();
        Optional<Ref> ref = geogig.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertFalse(ref.isPresent());
    }

    // TODO: Remove this test
    @SuppressWarnings(value = { "unused" })
    @Test
    public void testRemovalOfAllFeaturesOfAGivenType() throws Exception {
        List<RevCommit> commits = populate(false, points1, points2, points3, lines1, lines2);

        String featureId = lines1.getIdentifier().getID();
        String path = NodeRef.appendChild(linesName, featureId);
        String featureId2 = lines2.getIdentifier().getID();
        String path2 = NodeRef.appendChild(linesName, featureId2);

        WorkingTree tree = geogig.command(RemoveOp.class).addPathToRemove(path)
                .addPathToRemove(path2).call();

        geogig.command(AddOp.class).call();

        RevCommit commit = geogig.command(CommitOp.class).setMessage("Removed lines").call();
        Iterator<NodeRef> nodes = geogig.command(LsTreeOp.class).call();

        while (nodes.hasNext()) {
            NodeRef node = nodes.next();
            assertNotNull(node);
        }

        geogig.command(ResetOp.class).setMode(ResetMode.HARD).call();

        nodes = geogig.command(LsTreeOp.class).call();
        while (nodes.hasNext()) {
            NodeRef node = nodes.next();
            assertNotNull(node);
        }
    }

}
