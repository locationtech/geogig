/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.plumbing.DiffIndex;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.plumbing.DiffWorkTree;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.Name;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Unit test suite for {@link DiffOp}, must cover {@link DiffIndex}, {@link DiffWorkTree}, and
 * {@link DiffTree}
 * 
 */
public class DiffOpTest extends RepositoryTestCase {

    private DiffOp diffOp;

    @Override
    protected void setUpInternal() throws Exception {
        this.diffOp = geogig.command(DiffOp.class);
    }

    @Test
    public void testDiffPreconditions() throws Exception {
        try (AutoCloseableIterator<DiffEntry> difflist = geogig.command(DiffOp.class).call()) {
            assertNotNull(difflist);
            assertFalse(difflist.hasNext());
        }

        final ObjectId oid1 = insertAndAdd(points1);
        final RevCommit commit1_1 = geogig.command(CommitOp.class).call();
        try {
            diffOp.setOldVersion(oid1.toString()).setNewVersion(Ref.HEAD).call();
            fail("Expected IAE as oldVersion is not a commit");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(oid1.toString()));
            assertTrue(e.getMessage(),
                    e.getMessage().contains("doesn't resolve to a tree-ish object"));
        }
        try {
            diffOp.setOldVersion(commit1_1.getId().toString()).setNewVersion(oid1.toString())
                    .call();
            fail("Expected IAE as newVersion is not a commit");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(oid1.toString()));
            assertTrue(e.getMessage(),
                    e.getMessage().contains("doesn't resolve to a tree-ish object"));
        }
    }

    @Test
    public void testEmptyRepo() throws Exception {
        try (AutoCloseableIterator<DiffEntry> difflist = diffOp
                .setOldVersion(ObjectId.NULL.toString()).call()) {
            assertNotNull(difflist);
            assertFalse(difflist.hasNext());
        }
    }

    @Test
    public void testNoChangeSameCommit() throws Exception {

        insertAndAdd(points1);
        final RevCommit commit = geogig.command(CommitOp.class).setAll(true).call();

        assertFalse(diffOp.setOldVersion(commit.getId().toString())
                .setNewVersion(commit.getId().toString()).call().hasNext());
    }

    @Test
    public void testSingleAddition() throws Exception {

        final ObjectId newOid = insertAndAdd(points1);
        geogig.command(CommitOp.class).setAll(true).call();

        List<DiffEntry> difflist = toList(
                diffOp.setOldVersion(ObjectId.NULL).setNewVersion(Ref.HEAD).call());

        assertNotNull(difflist);
        assertEquals(1, difflist.size());
        DiffEntry de = difflist.get(0);

        assertNull(de.getOldObject());
        assertNotNull(de.getNewObject());

        String expectedPath = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        assertEquals(expectedPath, de.newPath());

        assertEquals(DiffEntry.ChangeType.ADDED, de.changeType());
        assertEquals(ObjectId.NULL, de.oldObjectId());

        assertEquals(newOid, de.newObjectId());
        assertFalse(de.getNewObject().getMetadataId().isNull());
    }

    @Test
    public void testSingleAdditionReverseOrder() throws Exception {

        final ObjectId newOid = insertAndAdd(points1);
        final RevCommit commit = geogig.command(CommitOp.class).setAll(true).call();

        List<DiffEntry> difflist = toList(
                diffOp.setOldVersion(commit.getId()).setNewVersion(ObjectId.NULL).call());

        assertNotNull(difflist);
        assertEquals(1, difflist.size());
        DiffEntry de = difflist.get(0);

        assertNull(de.getNewObject());
        assertNotNull(de.getOldObject());

        assertEquals(DiffEntry.ChangeType.REMOVED, de.changeType());
        assertEquals(ObjectId.NULL, de.newObjectId());

        assertEquals(newOid, de.oldObjectId());
        assertFalse(de.getOldObject().getMetadataId().isNull());
    }

    @Test
    public void testSingleDeletion() throws Exception {
        final ObjectId featureContentId = insertAndAdd(points1);
        final RevCommit addCommit = geogig.command(CommitOp.class).setAll(true).call();

        assertTrue(deleteAndAdd(points1));
        final RevCommit deleteCommit = geogig.command(CommitOp.class).setAll(true).call();

        List<DiffEntry> difflist = toList(
                diffOp.setOldVersion(addCommit.getId()).setNewVersion(deleteCommit.getId()).call());

        final String path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());

        assertNotNull(difflist);
        assertEquals(1, difflist.size());
        DiffEntry de = difflist.get(0);
        assertEquals(path, de.oldPath());

        assertEquals(DiffEntry.ChangeType.REMOVED, de.changeType());

        assertEquals(featureContentId, de.oldObjectId());

        assertEquals(ObjectId.NULL, de.newObjectId());
    }

    @Test
    public void testSingleDeletionReverseOrder() throws Exception {

        final ObjectId featureContentId = insertAndAdd(points1);
        final RevCommit addCommit = geogig.command(CommitOp.class).setAll(true).call();

        assertTrue(deleteAndAdd(points1));
        final RevCommit deleteCommit = geogig.command(CommitOp.class).setAll(true).call();

        // set old/new version in reverse order
        List<DiffEntry> difflist = toList(
                diffOp.setOldVersion(deleteCommit.getId()).setNewVersion(addCommit.getId()).call());

        final String path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());

        // then the diff should report an ADD instead of a DELETE
        assertNotNull(difflist);
        assertEquals(1, difflist.size());
        DiffEntry de = difflist.get(0);
        assertNull(de.oldPath());
        assertEquals(path, de.newPath());

        assertEquals(DiffEntry.ChangeType.ADDED, de.changeType());

        assertEquals(ObjectId.NULL, de.oldObjectId());

        assertEquals(featureContentId, de.newObjectId());
    }

    @Test
    public void testSingleModification() throws Exception {

        final ObjectId oldOid = insertAndAdd(points1);
        final RevCommit insertCommit = geogig.command(CommitOp.class).setAll(true).call();

        final ObjectId newOid = insertAndAdd(points1_modified);

        final RevCommit changeCommit = geogig.command(CommitOp.class).setAll(true).call();

        List<DiffEntry> difflist = toList(diffOp.setOldVersion(insertCommit.getId())
                .setNewVersion(changeCommit.getId()).call());

        assertNotNull(difflist);
        assertEquals(1, difflist.size());
        DiffEntry de = difflist.get(0);
        String expectedPath = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        assertEquals(expectedPath, de.newPath());

        assertEquals(DiffEntry.ChangeType.MODIFIED, de.changeType());
        assertEquals(oldOid, de.oldObjectId());

        assertEquals(newOid, de.newObjectId());
    }

    @Test
    public void testFilterNamespaceNoChanges() throws Exception {

        // two commits on different trees
        insertAndAdd(points1);
        final RevCommit commit1 = geogig.command(CommitOp.class).setAll(true).call();

        insertAndAdd(lines1);
        final RevCommit commit2 = geogig.command(CommitOp.class).setAll(true).call();

        diffOp.setOldVersion(commit1.getId()).setNewVersion(commit2.getId());
        diffOp.setFilter(pointsName);

        try (AutoCloseableIterator<DiffEntry> diffs = diffOp.call()) {
            assertFalse(diffs.hasNext());
        }
    }

    @Test
    public void testFilterTypeNameNoChanges() throws Exception {

        // two commits on different trees
        insertAndAdd(points1);
        final RevCommit commit1 = geogig.command(CommitOp.class).setAll(true).call();

        insertAndAdd(lines1);
        final RevCommit commit2 = geogig.command(CommitOp.class).setAll(true).call();

        diffOp.setOldVersion(commit1.getId()).setNewVersion(commit2.getId());
        diffOp.setFilter(pointsName);

        try (AutoCloseableIterator<DiffEntry> diffs = diffOp.call()) {
            assertFalse(diffs.hasNext());
        }
    }

    @Test
    public void testFilterDidntMatchAnything() throws Exception {

        // two commits on different trees
        insertAndAdd(points1);
        final RevCommit commit1 = geogig.command(CommitOp.class).setAll(true).call();

        insertAndAdd(lines1);
        final RevCommit commit2 = geogig.command(CommitOp.class).setAll(true).call();

        // set a filter that doesn't produce any match

        diffOp.setOldVersion(commit1.getId()).setNewVersion(commit2.getId());
        diffOp.setFilter(NodeRef.appendChild(pointsName, "nonExistentId"));

        try (AutoCloseableIterator<DiffEntry> diffs = diffOp.call()) {
            assertNotNull(diffs);
            assertFalse(diffs.hasNext());
        }
    }

    @Test
    public void testFilterFeatureIdNoChanges() throws Exception {

        // two commits on different trees
        insertAndAdd(points1);
        final RevCommit commit1 = geogig.command(CommitOp.class).setAll(true).call();

        insertAndAdd(lines1);
        final RevCommit commit2 = geogig.command(CommitOp.class).setAll(true).call();

        // filter on feature1_1, it didn't change between commit2 and commit1

        diffOp.setOldVersion(commit1.getId()).setNewVersion(commit2.getId());
        diffOp.setFilter(NodeRef.appendChild(pointsName, points1.getIdentifier().getID()));

        try (AutoCloseableIterator<DiffEntry> diffs = diffOp.call()) {
            assertFalse(diffs.hasNext());
        }
    }

    @Test
    public void testFilterMatchesSingleBlobChange() throws Exception {
        final ObjectId initialOid = insertAndAdd(points1);
        final RevCommit commit1 = geogig.command(CommitOp.class).setAll(true).call();

        insertAndAdd(lines1);
        final RevCommit commit2 = geogig.command(CommitOp.class).setAll(true).call();

        ((SimpleFeature) points1).setAttribute("sp", "modified");
        final ObjectId modifiedOid = insertAndAdd(points1);
        final RevCommit commit3 = geogig.command(CommitOp.class).setAll(true).call();

        diffOp.setOldVersion(commit1.getId()).setNewVersion(commit3.getId());
        diffOp.setFilter(NodeRef.appendChild(pointsName, points1.getIdentifier().getID()));

        List<DiffEntry> diffs;
        DiffEntry diff;

        diffs = toList(diffOp.call());
        assertEquals(1, diffs.size());
        diff = diffs.get(0);
        assertEquals(ChangeType.MODIFIED, diff.changeType());
        assertEquals(initialOid, diff.oldObjectId());
        assertEquals(modifiedOid, diff.newObjectId());

        assertTrue(deleteAndAdd(points1));
        final RevCommit commit4 = geogig.command(CommitOp.class).setAll(true).call();
        diffOp.setOldVersion(commit2.getId()).setNewVersion(commit4.getId());
        diffOp.setFilter(NodeRef.appendChild(pointsName, points1.getIdentifier().getID()));
        diffs = toList(diffOp.call());
        assertEquals(1, diffs.size());
        diff = diffs.get(0);
        assertEquals(ChangeType.REMOVED, diff.changeType());
        assertEquals(initialOid, diff.oldObjectId());
        assertEquals(ObjectId.NULL, diff.newObjectId());

        // invert the order of old and new commit
        diffOp.setOldVersion(commit4.getId()).setNewVersion(commit1.getId());
        diffOp.setFilter(NodeRef.appendChild(pointsName, points1.getIdentifier().getID()));
        diffs = toList(diffOp.call());
        assertEquals(1, diffs.size());
        diff = diffs.get(0);
        assertEquals(ChangeType.ADDED, diff.changeType());
        assertEquals(ObjectId.NULL, diff.oldObjectId());
        assertEquals(initialOid, diff.newObjectId());

        // different commit range
        diffOp.setOldVersion(commit4.getId()).setNewVersion(commit3.getId());
        diffOp.setFilter(NodeRef.appendChild(pointsName, points1.getIdentifier().getID()));
        diffs = toList(diffOp.call());
        assertEquals(1, diffs.size());
        diff = diffs.get(0);
        assertEquals(ChangeType.ADDED, diff.changeType());
        assertEquals(ObjectId.NULL, diff.oldObjectId());
        assertEquals(modifiedOid, diff.newObjectId());
    }

    // @Test
    // public void testFilterAddressesNamespaceTree() throws Exception {
    //
    // // two commits on different trees
    // final ObjectId oid11 = insertAndAdd(points1);
    // final ObjectId oid12 = insertAndAdd(points2);
    // final RevCommit commit1 = geogig.command(CommitOp.class).setAll(true).call();
    //
    // final ObjectId oid21 = insertAndAdd(lines1);
    // final ObjectId oid22 = insertAndAdd(lines2);
    // final RevCommit commit2 = geogig.command(CommitOp.class).setAll(true).call();
    //
    // List<DiffEntry> diffs;
    //
    // // filter on namespace1, no changes between commit1 and commit2
    // diffOp.setOldVersion(commit1.getId());
    // diffOp.setFilter(pointsNs);
    //
    // diffs = toList(diffOp.call());
    // assertEquals(0, diffs.size());
    //
    // // filter on namespace2, all additions between commit1 and commit2
    // diffOp.setOldVersion(commit1.getId());
    // diffOp.setFilter(linesNs);
    //
    // diffs = toList(diffOp.call());
    // assertEquals(2, diffs.size());
    // assertEquals(ChangeType.ADD, diffs.get(0).getType());
    // assertEquals(ChangeType.ADD, diffs.get(1).getType());
    //
    // assertEquals(ObjectId.NULL, diffs.get(0).getOldObjectId());
    // assertEquals(ObjectId.NULL, diffs.get(1).getOldObjectId());
    //
    // // don't care about order
    // Set<ObjectId> expected = new HashSet<ObjectId>();
    // expected.add(oid21);
    // expected.add(oid22);
    // Set<ObjectId> actual = new HashSet<ObjectId>();
    // actual.add(diffs.get(0).getNewObjectId());
    // actual.add(diffs.get(1).getNewObjectId());
    // assertEquals(expected, actual);
    // }

    @SuppressWarnings("unused")
    @Test
    public void testMultipleDeletes() throws Exception {

        // two commits on different trees
        final ObjectId oid11 = insertAndAdd(points1);
        final ObjectId oid12 = insertAndAdd(points2);
        final ObjectId oid13 = insertAndAdd(points3);
        final RevCommit commit1 = geogig.command(CommitOp.class).setAll(true).call();

        final ObjectId oid21 = insertAndAdd(lines1);
        final RevCommit commit2 = geogig.command(CommitOp.class).setAll(true).call();

        deleteAndAdd(points1);
        deleteAndAdd(points3);
        final RevCommit commit3 = geogig.command(CommitOp.class).setAll(true).call();

        List<DiffEntry> diffs;

        // filter on namespace1, no changes between commit1 and commit2
        diffOp.setOldVersion(commit1.getId()).setNewVersion(commit3.getId());
        diffOp.setFilter(pointsName);

        diffs = toList(diffOp.call());

        assertEquals(2, diffs.size());
        assertEquals(ChangeType.REMOVED, diffs.get(0).changeType());
        assertEquals(ChangeType.REMOVED, diffs.get(1).changeType());

        Set<ObjectId> ids = Sets.newHashSet(diffs.get(0).oldObjectId(), diffs.get(1).oldObjectId());

        assertEquals(Sets.newHashSet(oid11, oid13), ids);
    }

    @SuppressWarnings("unused")
    @Test
    public void testTreeDeletes() throws Exception {

        // two commits on different trees
        final ObjectId oid11 = insertAndAdd(points1);
        final ObjectId oid12 = insertAndAdd(points2);
        final ObjectId oid13 = insertAndAdd(points3);
        final RevCommit commit1 = geogig.command(CommitOp.class).setAll(true).call();

        final ObjectId oid21 = insertAndAdd(lines1);
        final ObjectId oid22 = insertAndAdd(lines2);
        final RevCommit commit2 = geogig.command(CommitOp.class).setAll(true).call();

        deleteAndAdd(points1);
        deleteAndAdd(points2);
        deleteAndAdd(points3);
        final RevCommit commit3 = geogig.command(CommitOp.class).setAll(true).call();

        List<DiffEntry> diffs;

        // filter on namespace1, no changes between commit1 and commit2
        diffOp.setOldVersion(commit1.getId());
        diffOp.setNewVersion(Ref.HEAD);
        diffOp.setFilter(pointsName);

        diffs = toList(diffOp.call());
        assertEquals(3, diffs.size());
    }

    @Test
    public void testReportTreesEmptyTree() throws Exception {

        WorkingTree workingTree = geogig.getRepository().workingTree();
        workingTree.createTypeTree(linesName, linesType);

        List<DiffEntry> difflist = toList(diffOp.setReportTrees(true).setOldVersion(ObjectId.NULL)
                .setNewVersion(Ref.WORK_HEAD).call());

        assertNotNull(difflist);
        assertEquals(1, difflist.size());
        DiffEntry de = difflist.get(0);

        assertNull(de.getOldObject());
        assertNotNull(de.getNewObject());

        assertEquals(linesName, de.newPath());

        assertEquals(DiffEntry.ChangeType.ADDED, de.changeType());
        assertEquals(ObjectId.NULL, de.oldObjectId());
        assertFalse(de.getNewObject().getMetadataId().isNull());
    }

    @Test
    public void testReportRename() throws Exception {

        insertAndAdd(lines1);
        final RevCommit commit1 = geogig.command(CommitOp.class).setAll(true).call();

        Feature lines1B = feature(linesType, idL2, "StringProp2_1", new Integer(1000),
                "LINESTRING (1 1, 2 2)");
        delete(lines1);
        // insert(lines2);
        WorkingTree workTree = repo.workingTree();
        Name name = lines1.getType().getName();
        String parentPath = name.getLocalPart();
        workTree.insert(featureInfo(parentPath, lines1B));
        geogig.command(AddOp.class).call();
        RevCommit commit2 = geogig.command(CommitOp.class).setAll(true).call();

        List<DiffEntry> diffs;
        diffOp.setOldVersion(commit1.getId());
        diffOp.setNewVersion(commit2.getId());
        diffs = toList(diffOp.call());
        assertEquals(2, diffs.size()); // this is reported as an addition and a removal, with both
                                       // nodes pointing to same ObjectId
        assertEquals(diffs.get(0).newObjectId(), diffs.get(1).oldObjectId());
        assertEquals(diffs.get(1).newObjectId(), diffs.get(0).oldObjectId());

    }

    @Test
    public void testReportTreesEmptyTreeFromFeatureDeletion() throws Exception {
        insert(lines1);
        delete(lines1);

        List<DiffEntry> difflist = toList(diffOp.setReportTrees(true).setOldVersion(ObjectId.NULL)
                .setNewVersion(Ref.WORK_HEAD).call());

        assertNotNull(difflist);
        assertEquals(1, difflist.size());
        assertEquals(linesName, difflist.get(0).newName());

        DiffEntry de = difflist.get(0);

        assertNull(de.getOldObject());
        assertNotNull(de.getNewObject());

        assertEquals(linesName, de.newPath());

        assertEquals(DiffEntry.ChangeType.ADDED, de.changeType());
        assertEquals(ObjectId.NULL, de.oldObjectId());
        assertFalse(de.getNewObject().getMetadataId().isNull());
    }

    @Test
    public void testReportTrees() throws Exception {

        insert(points1);
        insert(lines1);

        List<DiffEntry> difflist = toList(diffOp.setReportTrees(true).setOldVersion(ObjectId.NULL)
                .setNewVersion(Ref.WORK_HEAD).call());

        assertNotNull(difflist);
        assertEquals(4, difflist.size());
        Set<String> expected = ImmutableSet.of(linesName, pointsName,
                NodeRef.appendChild(linesName, idL1), NodeRef.appendChild(pointsName, idP1));
        Set<String> actual = Sets.newHashSet(Collections2.transform(difflist, (e) -> e.newPath()));
        assertEquals(expected, actual);
    }

    @Test
    public void testChangedFeatureType() throws Exception {

        insertAndAdd(points1, points2);
        geogig.getRepository().workingTree().updateTypeTree(pointsName, modifiedPointsType);
        List<DiffEntry> difflist = toList(diffOp.setReportTrees(true).call());

        assertNotNull(difflist);
        assertEquals(1, difflist.size());

    }

    @Test
    public void testTreeModifiedByAddingExtraFeature() throws Exception {

        insertAndAdd(points1, points2);
        insert(points3);
        List<DiffEntry> difflist = toList(diffOp.setReportTrees(true).call());
        assertNotNull(difflist);
        assertEquals(2, difflist.size());
        assertEquals(ChangeType.MODIFIED, difflist.get(0).changeType());
        assertEquals(TYPE.TREE, difflist.get(0).getOldObject().getType());
        assertEquals(ChangeType.ADDED, difflist.get(1).changeType());
        assertEquals(TYPE.FEATURE, difflist.get(1).getNewObject().getType());
    }

}
