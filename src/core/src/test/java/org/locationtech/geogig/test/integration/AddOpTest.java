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
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.storage.AutoCloseableIterator;

public class AddOpTest extends RepositoryTestCase {

    protected @Override void setUpInternal() throws Exception {
        repo.context().configDatabase().put("user.name", "groldan");
        repo.context().configDatabase().put("user.email", "groldan@test.com");
    }

    @Test
    public void testAddSingleFile() throws Exception {
        insert(points1);
        List<DiffEntry> diffs = toList(repo.context().workingTree().getUnstaged(null));
        assertEquals(2, diffs.size());
        assertEquals(pointsName, diffs.get(0).newPath());
        assertEquals(NodeRef.appendChild(pointsName, idP1), diffs.get(1).newPath());
    }

    @Test
    public void testAddMultipleFeatures() throws Exception {
        insert(points1);
        insert(points2);
        insert(points3);
        repo.command(AddOp.class).call();
        List<DiffEntry> unstaged = toList(repo.context().workingTree().getUnstaged(null));
        assertEquals(Collections.emptyList(), unstaged);
    }

    @Test
    public void testAddMultipleTimes() throws Exception {
        insert(points1);
        insert(points2);
        insert(points3);
        repo.command(AddOp.class).call();
        try (AutoCloseableIterator<DiffEntry> iterator = repo.context().workingTree()
                .getUnstaged(null)) {
            assertFalse(iterator.hasNext());
        }
        insert(lines1);
        insert(lines2);
        repo.command(AddOp.class).call();
        try (AutoCloseableIterator<DiffEntry> iterator = repo.context().workingTree()
                .getUnstaged(null)) {
            assertFalse(iterator.hasNext());
        }
    }

    @Test
    public void testAddNewPathUsingPathFilter() throws Exception {
        insert(points1);
        insert(points2);
        repo.command(AddOp.class).addPattern("Points/Points.1").call();
        List<DiffEntry> unstaged = toList(repo.context().stagingArea().getStaged(null));
        assertEquals(unstaged.toString(), 2, unstaged.size());

        assertEquals(ChangeType.ADDED, unstaged.get(0).changeType());
        assertEquals(RevObject.TYPE.TREE, unstaged.get(0).getNewObject().getType());
        assertEquals("Points", unstaged.get(0).newName());
        RevFeatureType ft = RevFeatureType.builder().type(pointsType).build();
        ObjectId expectedTreeMdId = ft.getId();
        assertEquals(expectedTreeMdId, unstaged.get(0).getNewObject().getMetadataId());

        assertEquals(ChangeType.ADDED, unstaged.get(1).changeType());
        assertEquals(RevObject.TYPE.FEATURE, unstaged.get(1).getNewObject().getType());
        assertEquals("Points.1", unstaged.get(1).newName());
        assertFalse("feature node's metadata id should not be set, as it uses the parent tree one",
                unstaged.get(1).getNewObject().getNode().getMetadataId().isPresent());
    }

    @Test
    public void testAddMultipleFeaturesWithPathFilter() throws Exception {
        insert(points1);
        insert(points2);
        insert(lines1);
        repo.command(AddOp.class).addPattern("Points").call();
        List<DiffEntry> unstaged = toList(repo.context().workingTree().getUnstaged(null));
        assertEquals(2, unstaged.size());
        assertEquals(linesName, unstaged.get(0).newName());
        assertEquals(ChangeType.ADDED, unstaged.get(0).changeType());
        assertEquals(TYPE.TREE, unstaged.get(0).getNewObject().getType());
    }

    @Test
    public void testAddSingleDeletion() throws Exception {
        insert(points1);
        insert(points2);
        repo.command(AddOp.class).call();
        List<DiffEntry> staged = toList(
                repo.context().stagingArea().getStaged(List.of(pointsName)));
        assertEquals(3, staged.size());
        delete(points1);
        repo.command(AddOp.class).call();
        staged = toList(repo.context().stagingArea().getStaged(List.of(pointsName)));
        assertEquals(2, staged.size());
    }

    @Test
    public void testAddTreeDeletion() throws Exception {
        insert(points1);
        insert(points2);
        repo.command(AddOp.class).call();
        repo.context().workingTree().delete(pointsName);
        repo.command(AddOp.class).call();
        List<DiffEntry> staged = toList(
                repo.context().stagingArea().getStaged(List.of(pointsName)));
        assertEquals(0, staged.size());
        assertEquals(0, repo.context().stagingArea().countStaged(null).featureCount());
        assertEquals(0, repo.context().stagingArea().countStaged(null).treeCount());
    }

    @Test
    public void testAddUpdate() throws Exception {
        insert(points1);
        repo.command(AddOp.class).call();
        repo.command(CommitOp.class).call();

        insert(points1_modified);
        insert(lines1);
        repo.command(AddOp.class).setUpdateOnly(true).call();
        List<DiffEntry> unstaged = toList(repo.context().workingTree().getUnstaged(null));
        assertEquals(2, unstaged.size());
        assertEquals(linesName, unstaged.get(0).newName());
        assertEquals(lines1.getId(), unstaged.get(1).newName());
    }

    @Test
    public void testAddUpdateWithPathFilter() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        insert(points1_modified);
        insert(lines1);

        // stage only Lines changed
        repo.command(AddOp.class).setUpdateOnly(true).addPattern(pointsName).call();
        List<DiffEntry> staged = toList(repo.context().stagingArea().getStaged(null));
        assertEquals(2, staged.size());
        assertEquals(pointsName, staged.get(0).newName());
        assertEquals(idP1, staged.get(1).newName());

        List<DiffEntry> unstaged = toList(repo.context().workingTree().getUnstaged(null));

        assertEquals(2, unstaged.size());
        assertEquals(linesName, unstaged.get(0).newName());
        assertEquals(idL1, unstaged.get(1).newName());

        repo.command(AddOp.class).setUpdateOnly(true).addPattern("Points").call();
        unstaged = toList(repo.context().workingTree().getUnstaged(null));

        assertEquals(2, unstaged.size());
        assertEquals(linesName, unstaged.get(0).newName());
        assertEquals(idL1, unstaged.get(1).newName());
    }

    @Test
    public void testInsertionAndAdditionFixesConflict() throws Exception {
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", Integer.valueOf(1000),
                "POINT(1 1)");
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", Integer.valueOf(2000),
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
        insert(points1);
        repo.command(AddOp.class).call();
        assertFalse(repo.context().conflictsDatabase().hasConflicts(null));
        repo.command(CommitOp.class).call();
        Optional<Ref> ref = repo.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertFalse(ref.isPresent());
    }

    @Test
    public void testAdditionFixesConflict() throws Exception {
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", Integer.valueOf(1000),
                "POINT(1 1)");
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", Integer.valueOf(2000),
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
            assertTrue(true);
        }
        repo.command(AddOp.class).call();
        assertFalse(repo.context().conflictsDatabase().hasConflicts(null));
        repo.command(CommitOp.class).call();
        Optional<Ref> ref = repo.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertFalse(ref.isPresent());
    }

    @Test
    public void testAddModifiedFeatureType() throws Exception {
        insertAndAdd(points2, points1B);
        repo.command(CommitOp.class).call();
        repo.context().workingTree().updateTypeTree(pointsName, modifiedPointsType);
        repo.command(AddOp.class).call();
        List<DiffEntry> list = toList(repo.context().stagingArea().getStaged(null));
        assertFalse(list.isEmpty());
        String path = NodeRef.appendChild(pointsName, idP1);
        Optional<NodeRef> ref = repo.command(FindTreeChild.class).setChildPath(path)
                .setParent(repo.context().stagingArea().getTree()).call();
        assertTrue(ref.isPresent());
        assertFalse(ref.get().getNode().getMetadataId().isPresent());
        path = NodeRef.appendChild(pointsName, idP2);
        ref = repo.command(FindTreeChild.class).setChildPath(path)
                .setParent(repo.context().stagingArea().getTree()).call();
        assertTrue(ref.isPresent());
        assertTrue(ref.get().getNode().getMetadataId().isPresent());

    }
}
