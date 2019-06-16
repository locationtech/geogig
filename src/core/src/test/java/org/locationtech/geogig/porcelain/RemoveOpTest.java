/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

public class RemoveOpTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected @Override void setUpInternal() throws Exception {
    }

    @Test
    public void testSingleFeatureRemoval() throws Exception {
        populate(false, points1, points2, points3);

        String featureId = points1.getId();
        String path = NodeRef.appendChild(pointsName, featureId);
        DiffObjectCount result = repo.command(RemoveOp.class).addPathToRemove(path).call();
        assertEquals(1, result.getFeaturesRemoved());
        assertEquals(0, result.getTreesRemoved());

        Optional<ObjectId> id = repo.command(RevParse.class).setRefSpec(Ref.WORK_HEAD + ":" + path)
                .call();
        assertFalse(id.isPresent());
        id = repo.command(RevParse.class).setRefSpec(Ref.STAGE_HEAD + ":" + path).call();
        assertFalse(id.isPresent());
    }

    @Test
    public void testMultipleRemoval() throws Exception {
        populate(false, points1, points2, points3);

        String featureId = points1.getId();
        String path = NodeRef.appendChild(pointsName, featureId);
        String featureId2 = points2.getId();
        String path2 = NodeRef.appendChild(pointsName, featureId2);

        DiffObjectCount result = repo.command(RemoveOp.class).addPathToRemove(path)
                .addPathToRemove(path2).call();
        assertEquals(2, result.getFeaturesRemoved());
        assertEquals(0, result.getTreesRemoved());

        Optional<ObjectId> id = repo.command(RevParse.class).setRefSpec(Ref.WORK_HEAD + ":" + path)
                .call();
        assertFalse(id.isPresent());
        id = repo.command(RevParse.class).setRefSpec(Ref.STAGE_HEAD + ":" + path).call();
        assertFalse(id.isPresent());
        id = repo.command(RevParse.class).setRefSpec(Ref.WORK_HEAD + ":" + path2).call();
        assertFalse(id.isPresent());
        id = repo.command(RevParse.class).setRefSpec(Ref.STAGE_HEAD + ":" + path2).call();
        assertFalse(id.isPresent());
    }

    @Test
    public void testTreeRemoval() throws Exception {
        populate(false, points1, points2, points3, lines1, lines2);

        DiffObjectCount result = repo.command(RemoveOp.class).addPathToRemove(pointsName)
                .setRecursive(true).call();
        assertEquals(3, result.getFeaturesRemoved());
        assertEquals(1, result.getTreesRemoved());

        Optional<ObjectId> id = repo.command(RevParse.class)
                .setRefSpec(Ref.WORK_HEAD + ":" + pointsName).call();
        assertFalse(id.isPresent());
        id = repo.command(RevParse.class).setRefSpec(Ref.STAGE_HEAD + ":" + pointsName).call();
        List<DiffEntry> list = toList(repo.index().getStaged(null));
        assertEquals(4, list.size());
        assertFalse(id.isPresent());
        id = repo.command(RevParse.class).setRefSpec(Ref.STAGE_HEAD + ":" + linesName).call();
        assertTrue(id.isPresent());
    }

    @Ignore
    @Test
    public void testUnexistentPathRemoval() throws Exception {
        populate(false, points1, points2, points3);

        try {
            repo.command(RemoveOp.class).addPathToRemove("wrong/wrong.1").call();
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
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points1Modified);
        repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        repo.command(CommitOp.class).call();

        repo.command(CheckoutOp.class).setSource("master").call();
        Ref branch = repo.command(RefParse.class).setName("TestBranch").call().get();
        try {
            repo.command(MergeOp.class).addCommit(branch.getObjectId()).call();
            fail();
        } catch (MergeConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }
        String path = NodeRef.appendChild(pointsName, idP1);
        DiffObjectCount result = repo.command(RemoveOp.class).addPathToRemove(path).call();
        assertEquals(1, result.getFeaturesRemoved());
        assertEquals(0, result.getTreesRemoved());

        Repository repository = repo;
        ConflictsDatabase conflicts = repository.conflictsDatabase();
        assertEquals(0, conflicts.getCountByPrefix(null, null));
        repo.command(CommitOp.class).call();
        Optional<Ref> ref = repo.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertFalse(ref.isPresent());
    }

    // TODO: Remove this test
    @SuppressWarnings(value = { "unused" })
    @Test
    public void testRemovalOfAllFeaturesOfAGivenType() throws Exception {
        List<RevCommit> commits = populate(false, points1, points2, points3, lines1, lines2);

        String featureId = lines1.getId();
        String path = NodeRef.appendChild(linesName, featureId);
        String featureId2 = lines2.getId();
        String path2 = NodeRef.appendChild(linesName, featureId2);

        DiffObjectCount result = repo.command(RemoveOp.class).addPathToRemove(path)
                .addPathToRemove(path2).call();
        assertEquals(2, result.getFeaturesRemoved());
        assertEquals(0, result.getTreesRemoved());

        RevCommit commit = repo.command(CommitOp.class).setMessage("Removed lines").call();
        Iterator<NodeRef> nodes = repo.command(LsTreeOp.class).call();

        while (nodes.hasNext()) {
            NodeRef node = nodes.next();
            assertNotNull(node);
        }

        repo.command(ResetOp.class).setMode(ResetMode.HARD).call();

        nodes = repo.command(LsTreeOp.class).call();
        while (nodes.hasNext()) {
            NodeRef node = nodes.next();
            assertNotNull(node);
        }
    }

    @Test
    public void testPathsPrecondition() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("No paths to remove were indicated");
        repo.command(RemoveOp.class).call();
    }

    @Test
    public void testTruncateAndRecursivePrecondition() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("recursive and truncate arguments are mutually exclusive");
        repo.command(RemoveOp.class).addPathToRemove("tree").setRecursive(true).setTruncate(true)
                .call();
    }

    @Test
    public void testTruncateOrRecursivePrecondition() throws Exception {
        insertAndAdd(points1);

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(
                "Cannot remove tree " + pointsName + " if recursive or truncate is not specified");
        repo.command(RemoveOp.class).addPathToRemove(pointsName).setRecursive(false)
                .setTruncate(false).call();
    }

    @Test
    public void testTruncate() throws Exception {
        insert(points1, points2, points3);
        insert(lines1, lines2, lines3);

        DiffObjectCount result = repo.command(RemoveOp.class).addPathToRemove(linesName)
                .setTruncate(true).call();
        assertEquals(0, result.getTreesRemoved());
        assertEquals(1, result.getTreesChanged());
        assertEquals(3, result.getFeaturesRemoved());
    }
}
