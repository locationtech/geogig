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

import static org.locationtech.geogig.model.NodeRef.appendChild;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObjectTestUtil;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.FeatureToDelete;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.test.TestPlatform;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Feature;
import org.opengis.feature.type.Name;

import com.google.common.base.Optional;
import com.google.common.collect.Iterators;

/**
 *
 */
public class WorkingTreeTest extends RepositoryTestCase {

    private WorkingTree workTree;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected Platform createPlatform() {
        @SuppressWarnings("serial")
        Platform testPlatform = new TestPlatform(repositoryDirectory) {
            @Override
            public int availableProcessors() {
                return 2;
            }
        };
        return testPlatform;
    }

    @Override
    protected void setUpInternal() throws Exception {
        workTree = repo.workingTree();
    }

    @Test
    public void testInsertSingle() throws Exception {
        FeatureInfo fi = featureInfo(points1);
        workTree.insert(fi);

        assertEquals(fi.getFeature().getId(),
                workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId());
    }

    @Test
    public void testInsertCollection() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        List<FeatureInfo> targetList = insert(featureList);

        assertEquals(3, targetList.size());

        FeatureInfo ref1 = targetList.get(0);
        FeatureInfo ref2 = targetList.get(1);
        FeatureInfo ref3 = targetList.get(2);

        assertEquals(ref1.getFeature().getId(),
                workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(ref2.getFeature().getId(),
                workTree.findUnstaged(appendChild(pointsName, idP2)).get().getObjectId());
        assertEquals(ref3.getFeature().getId(),
                workTree.findUnstaged(appendChild(pointsName, idP3)).get().getObjectId());
    }

    @Test
    public void testInsertIncludingFeatureToDelete() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);

        List<FeatureInfo> targetList = insert(featureList);

        assertEquals(1, targetList.size());

        FeatureInfo ref1 = targetList.get(0);
        assertEquals(ref1.getFeature().getId(),
                workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId());

        featureList.clear();
        featureList.add(new FeatureToDelete(pointsType, idP1));
        featureList.add(points2);
        featureList.add(points3);

        targetList = insert(featureList);

        assertEquals(2, targetList.size());

        FeatureInfo ref2 = targetList.get(0);
        FeatureInfo ref3 = targetList.get(1);

        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertEquals(ref2.getFeature().getId(),
                workTree.findUnstaged(appendChild(pointsName, idP2)).get().getObjectId());
        assertEquals(ref3.getFeature().getId(),
                workTree.findUnstaged(appendChild(pointsName, idP3)).get().getObjectId());
    }

    @Test
    public void testInsertCollectionNullCollectionSize() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        List<FeatureInfo> targetList = insert(featureList);

        assertEquals(3, targetList.size());

        FeatureInfo ref1 = targetList.get(0);
        FeatureInfo ref2 = targetList.get(1);
        FeatureInfo ref3 = targetList.get(2);

        assertEquals(ref1.getFeature().getId(),
                workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(ref2.getFeature().getId(),
                workTree.findUnstaged(appendChild(pointsName, idP2)).get().getObjectId());
        assertEquals(ref3.getFeature().getId(),
                workTree.findUnstaged(appendChild(pointsName, idP3)).get().getObjectId());
    }

    @Test
    public void testInsertDuplicateFeatures() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        insert(featureList);

        ObjectId oID1 = workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId();

        List<Feature> modifiedFeatures = new LinkedList<Feature>();
        modifiedFeatures.add(points1_modified);

        insert(modifiedFeatures);
        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId()
                .equals(oID1));

    }

    @Test
    public void testDeleteSingle() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        insert(featureList);

        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP3)).isPresent());

        ObjectId oldTreeId = workTree.getTree().getId();

        workTree.delete(pointsName, idP2);

        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP3)).isPresent());

        ObjectId newTreeId = workTree.getTree().getId();

        assertFalse(oldTreeId.equals(newTreeId));
    }

    @Test
    public void testDeleteNonexistentFeature() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);

        insert(featureList);

        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP3)).isPresent());

        ObjectId oldTreeId = workTree.getTree().getId();

        workTree.delete(pointsName, idP3);

        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP3)).isPresent());

        ObjectId newTreeId = workTree.getTree().getId();

        assertTrue(oldTreeId.equals(newTreeId));

    }

    @Test
    public void testDeleteCollectionAffectingMultipleTrees() throws Exception {
        insert(points1, points2, points3, lines1, lines2, lines3);

        String path1 = appendChild(pointsName, idP1);
        String path2 = appendChild(pointsName, idP2);
        String path3 = appendChild(pointsName, idP3);
        String path4 = appendChild(linesName, idL1);
        String path5 = appendChild(linesName, idL2);
        String path6 = appendChild(linesName, idL3);

        assertTrue(workTree.findUnstaged(path1).isPresent());
        assertTrue(workTree.findUnstaged(path2).isPresent());
        assertTrue(workTree.findUnstaged(path3).isPresent());
        assertTrue(workTree.findUnstaged(path4).isPresent());
        assertTrue(workTree.findUnstaged(path5).isPresent());
        assertTrue(workTree.findUnstaged(path6).isPresent());

        Iterator<String> featurePaths = Iterators.forArray(path1, path3, path4, path6);

        workTree.delete(featurePaths, DefaultProgressListener.NULL);

        assertFalse(workTree.findUnstaged(path1).isPresent());
        assertTrue(workTree.findUnstaged(path2).isPresent());
        assertFalse(workTree.findUnstaged(path3).isPresent());
        assertFalse(workTree.findUnstaged(path4).isPresent());
        assertTrue(workTree.findUnstaged(path5).isPresent());
        assertFalse(workTree.findUnstaged(path6).isPresent());
    }

    @Test
    public void testDeleteFeatureType() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        insert(featureList);

        featureList = new LinkedList<Feature>();
        featureList.add(lines1);
        featureList.add(lines2);
        featureList.add(lines3);

        insert(featureList);

        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP3)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(linesName, idL1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(linesName, idL2)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(linesName, idL3)).isPresent());

        workTree.delete(pointsName);

        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP3)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(linesName, idL1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(linesName, idL2)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(linesName, idL3)).isPresent());
    }

    @Test
    public void testHasRoot() throws Exception {
        insert(points1);
        Name typeName = points1.getName();
        assertFalse(workTree.hasRoot(typeName.getLocalPart()));
    }

    @Test
    public void testGetUnstaged() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        insert(featureList);

        assertEquals(3, workTree.countUnstaged(null).featureCount());
        assertEquals(1, workTree.countUnstaged(null).treeCount());

        try (AutoCloseableIterator<DiffEntry> changes = workTree.getUnstaged(null)) {
            assertNotNull(changes);
            assertEquals(4, Iterators.size(changes));
        }
    }

    @Test
    public void testInsertMultipleFeatureTypes() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        insert(featureList);

        featureList = new LinkedList<Feature>();
        featureList.add(lines1);
        featureList.add(lines2);
        featureList.add(lines3);

        insert(featureList);

        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP3)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(linesName, idL1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(linesName, idL2)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(linesName, idL3)).isPresent());

    }

    @Test
    public void testGetFeatureTypeNames() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        insert(featureList);

        featureList = new LinkedList<Feature>();
        featureList.add(lines1);
        featureList.add(lines2);
        featureList.add(lines3);

        insert(featureList);

        List<NodeRef> featureTypes = workTree.getFeatureTypeTrees();

        assertEquals(2, featureTypes.size());

        List<String> featureTypeNames = new LinkedList<String>();
        for (NodeRef name : featureTypes) {
            featureTypeNames.add(name.name());
        }

        assertTrue(featureTypeNames.contains(pointsName));
        assertTrue(featureTypeNames.contains(linesName));
    }

    @Test
    public void testCreateTypeTreeExisting() throws Exception {
        insert(points1);
        try {
            workTree.createTypeTree(pointsName, pointsType);
            fail("expected IAE on existing type tree");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("already exists"));
        }
    }

    @Test
    public void testCreateTypeTree() throws Exception {
        NodeRef treeRef = workTree.createTypeTree("points2", pointsType);
        assertNotNull(treeRef);
        assertEquals("points2", treeRef.path());
        assertEquals("", treeRef.getParentPath());
        assertTrue(treeRef.getNode().getMetadataId().isPresent());
        assertEquals(treeRef.getMetadataId(), treeRef.getNode().getMetadataId().get());

        RevFeatureType featureType = repo.objectDatabase().getFeatureType(treeRef.getMetadataId());
        RevObjectTestUtil.deepEquals(RevFeatureType.builder().type(pointsType).build(),
                featureType);
    }

    @Test
    public void testCreateTypeNestedNonExistingParent() throws Exception {
        NodeRef treeRef = workTree.createTypeTree("path/to/nested/type", pointsType);
        assertNotNull(treeRef);
        assertEquals("path/to/nested/type", treeRef.path());
        assertEquals("path/to/nested", treeRef.getParentPath());
        assertTrue(treeRef.getNode().getMetadataId().isPresent());
        assertEquals(treeRef.getMetadataId(), treeRef.getNode().getMetadataId().get());

        RevFeatureType featureType = repo.objectDatabase().getFeatureType(treeRef.getMetadataId());
        RevObjectTestUtil.deepEquals(RevFeatureType.builder().type(pointsType).build(),
                featureType);
    }

    @Test
    public void testCreateTypeTreeAutomaticallyWhenInsertingWitNoExistingTypeTree()
            throws Exception {

        insert(points1, points2);
        Optional<NodeRef> treeRef = repo.command(FindTreeChild.class).setChildPath(pointsName)
                .setParent(workTree.getTree()).call();
        assertTrue(treeRef.isPresent());
        assertTrue(treeRef.get().getNode().getMetadataId().isPresent());
        assertFalse(treeRef.get().getNode().getMetadataId().get().isNull());

        RevFeatureType featureType = repo.objectDatabase()
                .getFeatureType(treeRef.get().getMetadataId());
        RevObjectTestUtil.deepEquals(RevFeatureType.builder().type(pointsType).build(),
                featureType);

    }

    @Test
    public void testInsertFeatureWithNonDefaultFeatureType() throws Exception {
        insert(points2, points3);
        insert(points1B);
        RevTree root = repo.workingTree().getTree();
        assertNotNull(root);
        Optional<Node> typeTreeId = findTreeChild(root, pointsName);
        assertEquals(typeTreeId.get().getMetadataId().get(),
                RevFeatureType.builder().type(pointsType).build().getId());
        RevTree typeTree = repo.getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);
        String path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        Optional<NodeRef> featureBlobId = geogig.command(FindTreeChild.class).setParent(root)
                .setChildPath(path).call();
        assertTrue(featureBlobId.isPresent());
        assertEquals(RevFeatureType.builder().type(modifiedPointsType).build().getId(),
                featureBlobId.get().getMetadataId());
        path = NodeRef.appendChild(pointsName, points3.getIdentifier().getID());
    }

    @Test
    public void testUpdateTypeTree() throws Exception {
        insert(points2, points3);
        insert(points1B);
        RevTree root = repo.workingTree().getTree();
        assertNotNull(root);
        Optional<Node> typeTreeId = findTreeChild(root, pointsName);
        assertEquals(typeTreeId.get().getMetadataId().get(),
                RevFeatureType.builder().type(pointsType).build().getId());
        RevTree typeTree = repo.getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);
        String path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        Optional<Node> featureBlobId = findTreeChild(root, path);
        assertTrue(featureBlobId.isPresent());
        assertEquals(RevFeatureType.builder().type(modifiedPointsType).build().getId(),
                featureBlobId.get().getMetadataId().orNull());
        path = NodeRef.appendChild(pointsName, points3.getIdentifier().getID());
        featureBlobId = findTreeChild(root, path);
        assertEquals(null, featureBlobId.get().getMetadataId().orNull());

        workTree.updateTypeTree(pointsName, modifiedPointsType);
        root = repo.workingTree().getTree();
        typeTreeId = findTreeChild(root, pointsName);
        assertEquals(typeTreeId.get().getMetadataId().get(),
                RevFeatureType.builder().type(modifiedPointsType).build().getId());
        typeTree = repo.getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);
        path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        featureBlobId = findTreeChild(root, path);
        assertTrue(featureBlobId.isPresent());
        assertEquals(null, featureBlobId.get().getMetadataId().orNull());
        path = NodeRef.appendChild(pointsName, points3.getIdentifier().getID());
        featureBlobId = findTreeChild(root, path);
        assertEquals(RevFeatureType.builder().type(pointsType).build().getId(),
                featureBlobId.get().getMetadataId().orNull());

    }

    @Test
    public void testTruncate() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        insert(featureList);

        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP3)).isPresent());

        final Node oldTypeTree = findTreeChild(workTree.getTree(), pointsName).get();

        ObjectId newRoot = workTree.truncate(pointsName);

        assertEquals(newRoot, workTree.getTree().getId());

        Optional<Node> newTypeTree = findTreeChild(workTree.getTree(), pointsName);
        assertTrue(newTypeTree.isPresent());
        assertEquals(RevTree.EMPTY_TREE_ID, newTypeTree.get().getObjectId());
        assertEquals(oldTypeTree.getMetadataId(), newTypeTree.get().getMetadataId());
    }

    private Optional<Node> findTreeChild(RevTree root, String pathRemove) {
        Optional<NodeRef> nodeRef = geogig.command(FindTreeChild.class).setParent(root)
                .setChildPath(pathRemove).call();
        Optional<Node> node = Optional.absent();
        if (nodeRef.isPresent()) {
            node = Optional.of(nodeRef.get().getNode());
        }
        return node;
    }
}
