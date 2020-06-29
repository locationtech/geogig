/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.model.NodeRef.appendChild;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.WriteTree2;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Collections2;

public class IndexTest extends RepositoryTestCase {

    private StagingArea index;

    private WorkingTree workingTree;

    private Geogig repo;

    protected @Override void setUpInternal() throws Exception {
        index = super.repo.context().stagingArea();
        workingTree = super.repo.context().workingTree();
        repo = Geogig.of(super.repo.context());
    }

    // two features with the same content and different fid should point to the same object
    @Test
    public void testInsertIdenticalObjects() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        Feature equalContentFeature = feature(pointsType, "DifferentId",
                points1.getAttributes().toArray());

        ObjectId oId2 = insertAndAdd(equalContentFeature);

        // BLOBS.print(repo.getRawObject(insertedId1), System.err);
        // BLOBS.print(repo.getRawObject(insertedId2), System.err);
        assertNotNull(oId1);
        assertNotNull(oId2);
        assertEquals(oId1, oId2);
    }

    // two features with different content should point to different objects
    @Test
    public void testInsertNonEqualObjects() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);

        ObjectId oId2 = insertAndAdd(points2);
        assertNotNull(oId1);
        assertNotNull(oId2);
        assertFalse(oId1.equals(oId2));
    }

    private static class TreeNameFilter implements Predicate<NodeRef> {

        private String treePath;

        public TreeNameFilter(String treePath) {
            this.treePath = treePath;
        }

        public @Override boolean apply(NodeRef ref) {
            TYPE type = ref.getType();
            String path = ref.path();
            return TYPE.TREE.equals(type) && treePath.equals(path);
        }
    }

    @Test
    public void testWriteEmptyPathAddAll() throws Exception {
        insert(lines1);
        workingTree.createTypeTree(pointsName, pointsType);

        List<NodeRef> workHead = toList(repo.commands().command(LsTreeOp.class)
                .setReference(Ref.WORK_HEAD).setStrategy(Strategy.DEPTHFIRST).call());

        assertEquals(3, workHead.size());
        Collection<NodeRef> filtered = Collections2.filter(workHead,
                new TreeNameFilter(pointsName));
        assertEquals(1, filtered.size());

        repo.commands().command(AddOp.class).call();

        List<NodeRef> indexHead = toList(repo.commands().command(LsTreeOp.class)
                .setReference(Ref.STAGE_HEAD).setStrategy(Strategy.DEPTHFIRST).call());

        assertEquals(3, indexHead.size());
        filtered = Collections2.filter(indexHead, new TreeNameFilter(pointsName));
        assertEquals(1, filtered.size());
    }

    @Test
    public void testWriteEmptyPath() throws Exception {
        workingTree.createTypeTree(pointsName, pointsType);
        workingTree.createTypeTree(linesName, linesType);

        List<NodeRef> workHead = toList(repo.commands().command(LsTreeOp.class)
                .setReference(Ref.WORK_HEAD).setStrategy(Strategy.DEPTHFIRST).call());

        assertEquals(2, workHead.size());

        Collection<NodeRef> filtered;
        filtered = Collections2.filter(workHead, new TreeNameFilter(pointsName));
        assertEquals(1, filtered.size());

        filtered = Collections2.filter(workHead, new TreeNameFilter(linesName));
        assertEquals(1, filtered.size());

        repo.commands().command(AddOp.class).addPattern(pointsName).call();

        List<NodeRef> indexHead;
        indexHead = toList(repo.commands().command(LsTreeOp.class).setReference(Ref.STAGE_HEAD)
                .setStrategy(Strategy.DEPTHFIRST).call());

        assertEquals(1, indexHead.size());
        filtered = Collections2.filter(indexHead, new TreeNameFilter(pointsName));
        assertEquals(1, filtered.size());

        repo.commands().command(AddOp.class).addPattern(linesName).call();
        indexHead = toList(repo.commands().command(LsTreeOp.class).setReference(Ref.STAGE_HEAD)
                .setStrategy(Strategy.DEPTHFIRST).call());

        assertEquals(2, indexHead.size());// Points and Lines
        filtered = Collections2.filter(indexHead, new TreeNameFilter(linesName));
        assertEquals(1, filtered.size());
    }

    @Test
    public void testModify() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        assertNotNull(oId1);

        assertEquals(oId1, index.findStaged(appendChild(pointsName, idP1)).get().getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        assertNotNull(oId1_modified);
        assertFalse(oId1.equals(oId1_modified));

        assertFalse(
                index.findStaged(appendChild(pointsName, idP1)).get().getObjectId().equals(oId1));
        assertEquals(oId1_modified,
                index.findStaged(appendChild(pointsName, idP1)).get().getObjectId());

    }

    @Test
    public void testAddMultiple() throws Exception {
        ObjectId oId1 = insert(points1);
        ObjectId oId2 = insert(points2);
        assertNotNull(oId1);
        assertNotNull(oId2);

        assertFalse(index.findStaged(appendChild(pointsName, idP1)).isPresent());
        assertFalse(index.findStaged(appendChild(pointsName, idP2)).isPresent());

        assertEquals(oId1,
                workingTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2,
                workingTree.findUnstaged(appendChild(pointsName, idP2)).get().getObjectId());

        repo.commands().command(AddOp.class).call();

        assertEquals(oId1, index.findStaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, index.findStaged(appendChild(pointsName, idP2)).get().getObjectId());

    }

    private Supplier<RevTree> tree(final ObjectId treeId) {
        Supplier<RevTree> delegate = new Supplier<RevTree>() {

            public @Override RevTree get() {
                if (treeId.isNull()) {
                    return RevTree.EMPTY;
                }
                return repo.commands().command(RevObjectParse.class).setObjectId(treeId)
                        .call(RevTree.class).get();
            }
        };
        return Suppliers.memoize(delegate);
    }

    @Test
    public void testMultipleStaging() throws Exception {

        // insert and commit feature1_1
        final ObjectId oId1_1 = insertAndAdd(points1);

        // check feature1_1 is there
        assertEquals(oId1_1, index.findStaged(appendChild(pointsName, idP1)).get().getObjectId());

        // insert and commit feature1_2, feature1_2 and feature2_1
        final ObjectId oId1_2 = insertAndAdd(points2);
        final ObjectId oId1_3 = insertAndAdd(points3);
        final ObjectId oId2_1 = insertAndAdd(lines1);

        // check feature1_2, feature1_3 and feature2_1
        Optional<Node> treeChild;

        assertNotNull(treeChild = index.findStaged(appendChild(pointsName, idP2)));
        assertTrue(treeChild.isPresent());
        assertEquals(oId1_2, treeChild.get().getObjectId());

        assertNotNull(treeChild = index.findStaged(appendChild(pointsName, idP3)));
        assertTrue(treeChild.isPresent());
        assertEquals(oId1_3, treeChild.get().getObjectId());

        assertNotNull(treeChild = index.findStaged(appendChild(linesName, idL1)));
        assertTrue(treeChild.isPresent());
        assertEquals(oId2_1, treeChild.get().getObjectId());

        // as well as feature1_1 from the previous commit
        assertNotNull(treeChild = index.findStaged(appendChild(pointsName, idP1)));
        assertTrue(treeChild.isPresent());
        assertEquals(oId1_1, treeChild.get().getObjectId());

        // delete feature1_1, feature1_3, and feature2_1
        assertTrue(deleteAndAdd(points1));
        assertTrue(deleteAndAdd(points3));
        assertTrue(deleteAndAdd(lines1));
        // and insert feature2_2
        final ObjectId oId2_2 = insertAndAdd(lines2);

        // and check only points2 and lines2 remain (i.e. its oids are set to NULL)
        assertFalse(index.findStaged(appendChild(pointsName, idP1)).isPresent());
        assertFalse(index.findStaged(appendChild(pointsName, idP3)).isPresent());
        assertFalse(index.findStaged(appendChild(linesName, idL1)).isPresent());

        assertEquals(oId1_2, index.findStaged(appendChild(pointsName, idP2)).get().getObjectId());
        assertEquals(oId2_2, index.findStaged(appendChild(linesName, idL2)).get().getObjectId());

    }

    @Test
    public void testWriteTree2() throws Exception {

        // insert and commit feature1_1
        final ObjectId oId1_1 = insertAndAdd(points1);

        final ObjectId newRepoTreeId1;
        {
            Ref head = repo.commands().command(RefParse.class).setName(Ref.HEAD).call().get();
            newRepoTreeId1 = repo.commands().command(WriteTree2.class)
                    .setOldRoot(tree(head.getObjectId())).call();

            RevTree newRepoTree = repo.objects().getTree(newRepoTreeId1);

            // check feature1_1 is there
            assertEquals(oId1_1, repo.objects().tree(newRepoTree)
                    .child(appendChild(pointsName, idP1)).get().getObjectId());

        }

        // insert and add (stage) points2, points3, and lines1
        final ObjectId oId1_2 = insertAndAdd(points2);
        final ObjectId oId1_3 = insertAndAdd(points3);
        final ObjectId oId2_1 = insertAndAdd(lines1);

        {// simulate a commit so the repo head points to this new tree
            List<ObjectId> parents = Collections.emptyList();

            RevCommit commit = RevCommit.builder().treeId(newRepoTreeId1).parentIds(parents)
                    .build();
            ObjectId commitId = commit.getId();
            repo.objects().put(commit);
            Optional<Ref> newHead = repo.commands().command(UpdateRef.class)
                    .setName("refs/heads/master").setReason("test init").setNewValue(commitId)
                    .call();
            assertTrue(newHead.isPresent());
        }

        final ObjectId newRepoTreeId2;
        {
            // write comparing the the previously generated tree instead of the repository HEAD, as
            // it was not updated (no commit op was performed)
            newRepoTreeId2 = repo.commands().command(WriteTree2.class)
                    .setOldRoot(tree(newRepoTreeId1)).call();

            RevTree newRepoTree = repo.objects().getTree(newRepoTreeId2);

            // check feature1_2, feature1_2 and feature2_1
            Optional<Node> treeChild;
            assertNotNull(treeChild = repo.objects().tree(newRepoTree)
                    .child(appendChild(pointsName, idP2)));
            assertEquals(oId1_2, treeChild.get().getObjectId());

            assertNotNull(treeChild = repo.objects().tree(newRepoTree)
                    .child(appendChild(pointsName, idP3)));
            assertEquals(oId1_3, treeChild.get().getObjectId());

            assertNotNull(treeChild = repo.objects().tree(newRepoTree)
                    .child(appendChild(linesName, idL1)));
            assertEquals(oId2_1, treeChild.get().getObjectId());

            // as well as feature1_1 from the previous commit
            assertNotNull(treeChild = repo.objects().tree(newRepoTree)
                    .child(appendChild(pointsName, idP1)));
            assertEquals(oId1_1, treeChild.get().getObjectId());
        }

        {// simulate a commit so the repo head points to this new tree
            List<ObjectId> parents = Collections.emptyList();
            RevCommit commit = RevCommit.builder().treeId(newRepoTreeId2).parentIds(parents)
                    .build();
            ObjectId commitId = commit.getId();

            repo.objects().put(commit);
            Optional<Ref> newHead = repo.commands().command(UpdateRef.class)
                    .setName("refs/heads/master").setReason("test setup").setNewValue(commitId)
                    .call();
            assertTrue(newHead.isPresent());
        }

        // delete feature1_1, feature1_3, and feature2_1
        assertTrue(deleteAndAdd(points1));
        assertTrue(deleteAndAdd(points3));
        assertTrue(deleteAndAdd(lines1));
        // and insert feature2_2
        final ObjectId oId2_2 = insertAndAdd(lines2);

        final ObjectId newRepoTreeId3;
        {
            // write comparing the the previously generated tree instead of the repository HEAD, as
            // it was not updated (no commit op was performed)
            newRepoTreeId3 = repo.commands().command(WriteTree2.class)
                    .setOldRoot(tree(newRepoTreeId2)).call();

            RevTree newRepoTree = repo.objects().getTree(newRepoTreeId3);

            // and check only feature1_2 and feature2_2 remain
            assertFalse(repo.objects().tree(newRepoTree).child(appendChild(pointsName, idP1))
                    .isPresent());
            assertFalse(repo.objects().tree(newRepoTree).child(appendChild(pointsName, idP3))
                    .isPresent());
            assertFalse(repo.objects().tree(newRepoTree).child(appendChild(linesName, idL3))
                    .isPresent());

            assertEquals(oId1_2, repo.objects().tree(newRepoTree)
                    .child(appendChild(pointsName, idP2)).get().getObjectId());
            assertEquals(oId2_2, repo.objects().tree(newRepoTree)
                    .child(appendChild(linesName, idL2)).get().getObjectId());
        }

    }

    @Test
    public void testAddEmptyTree() throws Exception {
        workingTree.createTypeTree(pointsName, pointsType);
        repo.commands().command(AddOp.class).setUpdateOnly(false).call();
        assertTrue(index.findStaged(pointsName).isPresent());
    }
}
