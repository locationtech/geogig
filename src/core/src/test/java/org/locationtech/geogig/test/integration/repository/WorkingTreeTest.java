/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration.repository;

import static org.locationtech.geogig.api.NodeRef.appendChild;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.QueryCapabilities;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.feature.FeatureCollection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.DefaultProgressListener;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevFeatureTypeImpl;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.api.data.ForwardingFeatureSource;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.repository.FeatureToDelete;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.Name;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 *
 */
public class WorkingTreeTest extends RepositoryTestCase {

    private static final ProgressListener LISTENER = new DefaultProgressListener();

    private WorkingTree workTree;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected Platform createPlatform() {
        Platform testPlatform = new TestPlatform(envHome) {
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
        Name name = points1.getType().getName();
        String parentPath = name.getLocalPart();
        Node ref = workTree.insert(parentPath, points1);
        ObjectId objectId = ref.getObjectId();

        assertEquals(objectId, workTree.findUnstaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
    }

    @Test
    public void testInsertCollection() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        List<Node> targetList = new LinkedList<Node>();
        workTree.insert(pointsName, featureList.iterator(), LISTENER, targetList, 3);

        assertEquals(3, targetList.size());

        Node ref1 = targetList.get(0);
        Node ref2 = targetList.get(1);
        Node ref3 = targetList.get(2);

        assertEquals(ref1.getObjectId(), workTree.findUnstaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertEquals(ref2.getObjectId(), workTree.findUnstaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(ref3.getObjectId(), workTree.findUnstaged(appendChild(pointsName, idP3)).get()
                .getObjectId());
    }

    @Test
    public void testInsertIncludingFeatureToDelete() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);

        List<Node> targetList = new LinkedList<Node>();
        workTree.insert(pointsName, featureList.iterator(), LISTENER, targetList, 1);

        assertEquals(1, targetList.size());

        Node ref1 = targetList.get(0);
        assertEquals(ref1.getObjectId(), workTree.findUnstaged(appendChild(pointsName, idP1)).get()
                .getObjectId());

        featureList.clear();
        featureList.add(new FeatureToDelete(pointsType, idP1));
        featureList.add(points2);
        featureList.add(points3);

        targetList.clear();

        workTree.insert(pointsName, featureList.iterator(), LISTENER, targetList, 3);

        assertEquals(2, targetList.size());

        Node ref2 = targetList.get(0);
        Node ref3 = targetList.get(1);

        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertEquals(ref2.getObjectId(), workTree.findUnstaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(ref3.getObjectId(), workTree.findUnstaged(appendChild(pointsName, idP3)).get()
                .getObjectId());
    }

    @Test
    public void testInsertCollectionNullCollectionSize() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        List<Node> targetList = new LinkedList<Node>();
        workTree.insert(pointsName, featureList.iterator(), LISTENER, targetList, null);

        assertEquals(3, targetList.size());

        Node ref1 = targetList.get(0);
        Node ref2 = targetList.get(1);
        Node ref3 = targetList.get(2);

        assertEquals(ref1.getObjectId(), workTree.findUnstaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertEquals(ref2.getObjectId(), workTree.findUnstaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(ref3.getObjectId(), workTree.findUnstaged(appendChild(pointsName, idP3)).get()
                .getObjectId());
    }

    @Test
    public void testInsertCollectionZeroCollectionSize() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        List<Node> targetList = new LinkedList<Node>();
        workTree.insert(pointsName, featureList.iterator(), LISTENER, targetList, 0);

        assertEquals(3, targetList.size());

        Node ref1 = targetList.get(0);
        Node ref2 = targetList.get(1);
        Node ref3 = targetList.get(2);

        assertEquals(ref1.getObjectId(), workTree.findUnstaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertEquals(ref2.getObjectId(), workTree.findUnstaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(ref3.getObjectId(), workTree.findUnstaged(appendChild(pointsName, idP3)).get()
                .getObjectId());
    }

    @Test
    public void testInsertCollectionNegativeCollectionSize() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        List<Node> targetList = new LinkedList<Node>();

        exception.expect(IllegalArgumentException.class);
        workTree.insert(pointsName, featureList.iterator(), LISTENER, targetList, -5);
    }

    @Test
    public void testInsertNonPagingFeatureSource() throws Exception {
        assertEquals(2, super.getGeogig().getPlatform().availableProcessors());

        final List<SimpleFeature> features = ImmutableList.of((SimpleFeature) points1,
                (SimpleFeature) points2, (SimpleFeature) points3);
        MemoryDataStore store = new MemoryDataStore();
        store.addFeatures(features);

        final QueryCapabilities caps = mock(QueryCapabilities.class);
        when(caps.isOffsetSupported()).thenReturn(true);

        @SuppressWarnings("rawtypes")
        FeatureSource source = store.getFeatureSource(pointsName);
        assertFalse(source.getQueryCapabilities().isOffsetSupported());

        String treePath = "target_typename";
        workTree.insert(treePath, source, Query.ALL, LISTENER);

        assertEquals(3, workTree.countUnstaged(treePath).featureCount());

    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testInsertPagingFeatureSource() throws Exception {
        assertEquals(2, super.getGeogig().getPlatform().availableProcessors());

        final List<SimpleFeature> features = ImmutableList.of((SimpleFeature) points1,
                (SimpleFeature) points2, (SimpleFeature) points3);
        MemoryDataStore store = new MemoryDataStore();
        store.addFeatures(features);

        final QueryCapabilities caps = mock(QueryCapabilities.class);
        when(caps.isOffsetSupported()).thenReturn(true);

        FeatureSource source = new ForwardingFeatureSource(store.getFeatureSource(pointsName)) {
            @Override
            public QueryCapabilities getQueryCapabilities() {
                return caps;
            }

            @Override
            public FeatureCollection getFeatures(Query query) throws IOException {
                Integer startIndex = query.getStartIndex();
                if (startIndex == null) {
                    return super.getFeatures();
                }
                int toIndex = (int) Math.min((long) startIndex + query.getMaxFeatures(),
                        features.size());
                List<SimpleFeature> result = features.subList(startIndex, toIndex);
                return DataUtilities.collection(result);
            }
        };

        assertTrue(source.getQueryCapabilities().isOffsetSupported());

        String treePath = "target_typename";
        workTree.insert(treePath, source, Query.ALL, LISTENER);

        assertEquals(3, workTree.countUnstaged(treePath).featureCount());
    }

    @Test
    public void testInsertCollectionNoTarget() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, null);

        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP3)).isPresent());
    }

    @Test
    public void testInsertDuplicateFeatures() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, 3);

        ObjectId oID1 = workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId();

        List<Feature> modifiedFeatures = new LinkedList<Feature>();
        modifiedFeatures.add(points1_modified);

        workTree.insert(pointsName, modifiedFeatures.iterator(), LISTENER, null, 1);
        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId()
                .equals(oID1));

    }

    @Test
    public void testUpdateFeatures() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, 3);

        ObjectId oID1 = workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId();

        List<Feature> modifiedFeatures = new LinkedList<Feature>();
        modifiedFeatures.add(points1_modified);

        workTree.update(pointsName, modifiedFeatures.iterator(), LISTENER, 1);
        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId()
                .equals(oID1));
    }

    @Test
    public void testUpdateFeaturesNullCollectionSize() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, 3);

        ObjectId oID1 = workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId();

        List<Feature> modifiedFeatures = new LinkedList<Feature>();
        modifiedFeatures.add(points1_modified);

        workTree.update(pointsName, modifiedFeatures.iterator(), LISTENER, null);
        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId()
                .equals(oID1));
    }

    @Test
    public void testUpdateFeaturesZeroCollectionSize() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, 3);

        ObjectId oID1 = workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId();

        List<Feature> modifiedFeatures = new LinkedList<Feature>();
        modifiedFeatures.add(points1_modified);

        workTree.update(pointsName, modifiedFeatures.iterator(), LISTENER, 0);
        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP1)).get().getObjectId()
                .equals(oID1));
    }

    @Test
    public void testUpdateFeaturesNegativeCollectionSize() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, 3);

        List<Feature> modifiedFeatures = new LinkedList<Feature>();
        modifiedFeatures.add(points1_modified);

        exception.expect(IllegalArgumentException.class);
        workTree.update(pointsName, modifiedFeatures.iterator(), LISTENER, -5);
    }

    @Test
    public void testDeleteSingle() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, 3);

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

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, 2);

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
    public void testDeleteCollection() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, 3);

        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP3)).isPresent());

        List<Feature> deleteFeatures = new LinkedList<Feature>();
        deleteFeatures.add(points1);
        deleteFeatures.add(points3);

        Name typeName = points1.getName();

        workTree.delete(typeName, null, deleteFeatures.iterator());

        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP3)).isPresent());
    }

    @Test
    public void testDeleteCollectionOfFeaturesNotPresent() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, 3);

        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP3)).isPresent());

        List<Feature> deleteFeatures = new LinkedList<Feature>();
        deleteFeatures.add(points3);

        Name typeName = points1.getName();

        workTree.delete(typeName, null, deleteFeatures.iterator());

        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP1)).isPresent());
        assertTrue(workTree.findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertFalse(workTree.findUnstaged(appendChild(pointsName, idP3)).isPresent());
    }

    @Test
    public void testDeleteFeatureType() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, 3);

        featureList = new LinkedList<Feature>();
        featureList.add(lines1);
        featureList.add(lines2);
        featureList.add(lines3);

        workTree.insert(linesName, featureList.iterator(), LISTENER, null, 3);

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
        assertFalse(workTree.hasRoot(typeName));
    }

    @Test
    public void testGetUnstaged() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, 3);

        assertEquals(3, workTree.countUnstaged(null).featureCount());
        assertEquals(1, workTree.countUnstaged(null).treeCount());

        Iterator<DiffEntry> changes = workTree.getUnstaged(null);

        assertNotNull(changes);
    }

    @Test
    public void testInsertMultipleFeatureTypes() throws Exception {
        List<Feature> featureList = new LinkedList<Feature>();
        featureList.add(points1);
        featureList.add(points2);
        featureList.add(points3);

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, 3);

        featureList = new LinkedList<Feature>();
        featureList.add(lines1);
        featureList.add(lines2);
        featureList.add(lines3);

        workTree.insert(linesName, featureList.iterator(), LISTENER, null, 3);

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

        workTree.insert(pointsName, featureList.iterator(), LISTENER, null, 3);

        featureList = new LinkedList<Feature>();
        featureList.add(lines1);
        featureList.add(lines2);
        featureList.add(lines3);

        workTree.insert(linesName, featureList.iterator(), LISTENER, null, 3);

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
        assertSame(treeRef.getMetadataId(), treeRef.getNode().getMetadataId().get());

        RevFeatureType featureType = repo.stagingDatabase().getFeatureType(treeRef.getMetadataId());
        assertEquals(pointsType, featureType.type());
    }

    @Test
    public void testCreateTypeNestedNonExistingParent() throws Exception {
        NodeRef treeRef = workTree.createTypeTree("path/to/nested/type", pointsType);
        assertNotNull(treeRef);
        assertEquals("path/to/nested/type", treeRef.path());
        assertEquals("path/to/nested", treeRef.getParentPath());
        assertTrue(treeRef.getNode().getMetadataId().isPresent());
        assertSame(treeRef.getMetadataId(), treeRef.getNode().getMetadataId().get());

        RevFeatureType featureType = repo.stagingDatabase().getFeatureType(treeRef.getMetadataId());
        assertEquals(pointsType, featureType.type());
    }

    @Test
    public void testCreateTypeTreeAutomaticallyWhenInsertingWitNoExistingTypeTree()
            throws Exception {

        insert(points1, points2);
        Optional<NodeRef> treeRef = repo.command(FindTreeChild.class).setChildPath(pointsName)
                .setIndex(true).setParent(workTree.getTree()).call();
        assertTrue(treeRef.isPresent());
        assertTrue(treeRef.get().getNode().getMetadataId().isPresent());
        assertFalse(treeRef.get().getNode().getMetadataId().get().isNull());

        RevFeatureType featureType = repo.stagingDatabase().getFeatureType(
                treeRef.get().getMetadataId());
        assertEquals(pointsType, featureType.type());

    }

    @Test
    public void testInsertFeatureWithNonDefaultFeatureType() throws Exception {
        insert(points2, points3);
        insert(points1B);
        RevTree root = repo.workingTree().getTree();
        assertNotNull(root);
        Optional<Node> typeTreeId = findTreeChild(root, pointsName);
        assertEquals(typeTreeId.get().getMetadataId().get(), RevFeatureTypeImpl.build(pointsType)
                .getId());
        RevTree typeTree = repo.getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);
        String path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        Optional<NodeRef> featureBlobId = geogig.command(FindTreeChild.class).setParent(root)
                .setChildPath(path).setIndex(true).call();
        assertTrue(featureBlobId.isPresent());
        assertEquals(RevFeatureTypeImpl.build(modifiedPointsType).getId(), featureBlobId.get()
                .getMetadataId());
        path = NodeRef.appendChild(pointsName, points3.getIdentifier().getID());
    }

    @Test
    public void testUpdateTypeTree() throws Exception {
        insert(points2, points3);
        insert(points1B);
        RevTree root = repo.workingTree().getTree();
        assertNotNull(root);
        Optional<Node> typeTreeId = findTreeChild(root, pointsName);
        assertEquals(typeTreeId.get().getMetadataId().get(), RevFeatureTypeImpl.build(pointsType)
                .getId());
        RevTree typeTree = repo.getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);
        String path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        Optional<Node> featureBlobId = findTreeChild(root, path);
        assertTrue(featureBlobId.isPresent());
        assertEquals(RevFeatureTypeImpl.build(modifiedPointsType).getId(), featureBlobId.get()
                .getMetadataId().orNull());
        path = NodeRef.appendChild(pointsName, points3.getIdentifier().getID());
        featureBlobId = findTreeChild(root, path);
        assertEquals(null, featureBlobId.get().getMetadataId().orNull());

        workTree.updateTypeTree(pointsName, modifiedPointsType);
        root = repo.workingTree().getTree();
        typeTreeId = findTreeChild(root, pointsName);
        assertEquals(typeTreeId.get().getMetadataId().get(),
                RevFeatureTypeImpl.build(modifiedPointsType).getId());
        typeTree = repo.getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);
        path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        featureBlobId = findTreeChild(root, path);
        assertTrue(featureBlobId.isPresent());
        assertEquals(null, featureBlobId.get().getMetadataId().orNull());
        path = NodeRef.appendChild(pointsName, points3.getIdentifier().getID());
        featureBlobId = findTreeChild(root, path);
        assertEquals(RevFeatureTypeImpl.build(pointsType).getId(), featureBlobId.get()
                .getMetadataId().orNull());

    }

    private Optional<Node> findTreeChild(RevTree root, String pathRemove) {
        Optional<NodeRef> nodeRef = geogig.command(FindTreeChild.class).setParent(root)
                .setChildPath(pathRemove).setIndex(true).call();
        Optional<Node> node = Optional.absent();
        if (nodeRef.isPresent()) {
            node = Optional.of(nodeRef.get().getNode());
        }
        return node;
    }
}
