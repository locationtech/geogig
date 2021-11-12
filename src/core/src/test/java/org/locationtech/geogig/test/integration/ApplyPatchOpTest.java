/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.diff.AttributeDiff;
import org.locationtech.geogig.plumbing.diff.FeatureDiff;
import org.locationtech.geogig.plumbing.diff.FeatureTypeDiff;
import org.locationtech.geogig.plumbing.diff.GenericAttributeDiffImpl;
import org.locationtech.geogig.plumbing.diff.Patch;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.ApplyPatchOp;
import org.locationtech.geogig.porcelain.CannotApplyPatchException;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import com.google.common.collect.Maps;

public class ApplyPatchOpTest extends RepositoryTestCase {

    protected @Override void setUpInternal() throws Exception {
    }

    private Optional<Node> findTreeChild(RevTree root, String pathRemove) {
        Optional<NodeRef> nodeRef = repo.command(FindTreeChild.class).setParent(root)
                .setChildPath(pathRemove).call();
        Optional<Node> node = Optional.empty();
        if (nodeRef.isPresent()) {
            node = Optional.of(nodeRef.get().getNode());
        }
        return node;
    }

    @Test
    public void testAddFeaturePatch() throws Exception {
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getId());
        patch.addAddedFeature(path, RevFeature.builder().build(points1),
                RevFeatureType.builder().type(pointsType).build());
        repo.command(ApplyPatchOp.class).setPatch(patch).call();
        RevTree root = repo.context().workingTree().getTree();
        assertNotNull(root);
        Optional<Node> typeTreeId = findTreeChild(root, pointsName);
        RevTree typeTree = repo.context().objectDatabase().getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);
        Optional<Node> featureBlobId = findTreeChild(root, path);
        assertTrue(featureBlobId.isPresent());
    }

    @Test
    public void testRemoveFeaturePatch() throws Exception {
        insert(points1);
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getId());
        patch.addRemovedFeature(path, RevFeature.builder().build(points1),
                RevFeatureType.builder().type(pointsType).build());
        repo.command(ApplyPatchOp.class).setPatch(patch).call();
        RevTree root = repo.context().workingTree().getTree();
        assertNotNull(root);
        Optional<Node> featureBlobId = findTreeChild(root, path);
        assertFalse(featureBlobId.isPresent());
    }

    @Test
    public void testModifyFeatureAttributePatch() throws Exception {
        insert(points1);
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getId());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Object oldValue = points1.getAttribute("sp");
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(oldValue, "new");
        map.put(pointsType.getDescriptor("sp"), diff);
        FeatureDiff feaureDiff = new FeatureDiff(path, map,
                RevFeatureType.builder().type(pointsType).build(),
                RevFeatureType.builder().type(pointsType).build());
        patch.addModifiedFeature(feaureDiff);
        repo.command(ApplyPatchOp.class).setPatch(patch).call();
        RevTree root = repo.context().workingTree().getTree();
        Optional<Node> featureBlobId = findTreeChild(root, path);
        assertTrue(featureBlobId.isPresent());
        try (AutoCloseableIterator<DiffEntry> unstaged = repo.context().workingTree()
                .getUnstaged(pointsName)) {
            List<DiffEntry> diffs = unstaged.toList();
            assertEquals(2, diffs.size());
        }
        Optional<RevFeature> feature = repo.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:" + path).call(RevFeature.class);
        assertTrue(feature.isPresent());
        List<Optional<Object>> values = feature.get().getValues();
        assertEquals("new", values.get(0).get());
    }

    @Test
    public void testModifyFeatureAttributeOutdatedPatch() throws Exception {
        insert(points1_modified);
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getId());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Object oldValue = points1.getAttribute("sp");
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(oldValue, "new");
        map.put(pointsType.getDescriptor("sp"), diff);
        FeatureDiff feaureDiff = new FeatureDiff(path, map,
                RevFeatureType.builder().type(pointsType).build(),
                RevFeatureType.builder().type(pointsType).build());
        patch.addModifiedFeature(feaureDiff);
        try {
            repo.command(ApplyPatchOp.class).setPatch(patch).call();
            fail();
        } catch (CannotApplyPatchException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testRemoveFeatureAttributePatch() throws Exception {
        insert(points1B);
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1B.getId());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Object oldValue = points1B.getAttribute("extra");
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(oldValue, null);
        map.put(modifiedPointsType.getDescriptor("extra"), diff);
        FeatureDiff featureDiff = new FeatureDiff(path, map,
                RevFeatureType.builder().type(modifiedPointsType).build(),
                RevFeatureType.builder().type(pointsType).build());
        patch.addModifiedFeature(featureDiff);
        repo.command(ApplyPatchOp.class).setPatch(patch).call();
        Optional<RevFeature> feature = repo.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:" + path).call(RevFeature.class);
        assertTrue(feature.isPresent());
        List<Optional<Object>> values = feature.get().getValues();
        assertEquals(points1.getAttributeCount(), values.size());
        assertFalse(values.contains(Optional.of("ExtraString")));

    }

    @Test
    public void testAddFeatureAttributePatch() throws Exception {
        insert(points1);
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getId());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Object newValue = points1B.getAttribute("extra");
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(null, newValue);
        map.put(modifiedPointsType.getDescriptor("extra"), diff);
        FeatureDiff featureDiff = new FeatureDiff(path, map,
                RevFeatureType.builder().type(pointsType).build(),
                RevFeatureType.builder().type(modifiedPointsType).build());
        patch.addModifiedFeature(featureDiff);
        repo.command(ApplyPatchOp.class).setPatch(patch).call();
        // TODO
    }

    @Test
    public void testRemoveFeatureAttributeOutdatedPatch() throws Exception {
        insert(points1B_modified);
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1B.getId());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Object oldValue = points1B.getAttribute("extra");
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(oldValue, null);
        map.put(modifiedPointsType.getDescriptor("extra"), diff);
        FeatureDiff featureDiff = new FeatureDiff(path, map,
                RevFeatureType.builder().type(modifiedPointsType).build(),
                RevFeatureType.builder().type(pointsType).build());
        patch.addModifiedFeature(featureDiff);
        try {
            repo.command(ApplyPatchOp.class).setPatch(patch).call();
            fail();
        } catch (CannotApplyPatchException e) {
            assertTrue(true);
        }

    }

    @Test
    public void testAddFeatureAttributeOutdatedPatch() throws Exception {
        insert(points1B);
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getId());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Object newValue = points1B.getAttribute("extra");
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(null, newValue);
        map.put(modifiedPointsType.getDescriptor("extra"), diff);
        FeatureDiff featureDiff = new FeatureDiff(path, map,
                RevFeatureType.builder().type(modifiedPointsType).build(),
                RevFeatureType.builder().type(modifiedPointsType).build());
        patch.addModifiedFeature(featureDiff);
        try {
            repo.command(ApplyPatchOp.class).setPatch(patch).call();
            fail();
        } catch (CannotApplyPatchException e) {
            assertTrue(true);
        }

    }

    @Test
    public void testAddedFeatureExists() throws Exception {
        insert(points1);
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getId());
        patch.addAddedFeature(path, RevFeature.builder().build(points1),
                RevFeatureType.builder().type(pointsType).build());
        try {
            repo.command(ApplyPatchOp.class).setPatch(patch).call();
            fail();
        } catch (CannotApplyPatchException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testModifiedFeatureDoesNotExists() throws Exception {
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getId());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Object oldValue = points1.getAttribute("sp");
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(oldValue, "new");
        map.put(pointsType.getDescriptor("sp"), diff);
        FeatureDiff featureDiff = new FeatureDiff(path, map,
                RevFeatureType.builder().type(pointsType).build(),
                RevFeatureType.builder().type(pointsType).build());
        patch.addModifiedFeature(featureDiff);
        try {
            repo.command(ApplyPatchOp.class).setPatch(patch).call();
            fail();
        } catch (CannotApplyPatchException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testRemovedFeatureDoesNotExists() throws Exception {
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getId());
        patch.addRemovedFeature(path, RevFeature.builder().build(points1),
                RevFeatureType.builder().type(pointsType).build());
        try {
            repo.command(ApplyPatchOp.class).setPatch(patch).call();
            fail();
        } catch (CannotApplyPatchException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testPartialApplication() throws Exception {
        insert(points1, points2);
        Patch patch = new Patch();
        String pathRemove = NodeRef.appendChild(pointsName, points2.getId());
        patch.addRemovedFeature(pathRemove, RevFeature.builder().build(points2),
                RevFeatureType.builder().type(pointsType).build());
        String pathModify = NodeRef.appendChild(pointsName, points1B.getId());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Object oldValue = points1B.getAttribute("extra");
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(oldValue, null);
        map.put(modifiedPointsType.getDescriptor("extra"), diff);
        FeatureDiff featureDiff = new FeatureDiff(pathModify, map,
                RevFeatureType.builder().type(modifiedPointsType).build(),
                RevFeatureType.builder().type(pointsType).build());
        patch.addModifiedFeature(featureDiff);
        Patch rejected = repo.command(ApplyPatchOp.class).setPatch(patch).setApplyPartial(true)
                .call();
        assertFalse(rejected.isEmpty());
        RevTree root = repo.context().workingTree().getTree();
        assertNotNull(root);
        Optional<Node> featureBlobId = findTreeChild(root, pathRemove);
        assertFalse(featureBlobId.isPresent());
        // now we take the rejected patch and apply it, and the new rejected should be identical to
        // it
        Patch newRejected = repo.command(ApplyPatchOp.class).setPatch(rejected)
                .setApplyPartial(true).call();
        assertEquals(rejected, newRejected);
    }

    @Test
    public void testApplyEmptyPatch() {
        Patch patch = new Patch();
        repo.command(ApplyPatchOp.class).setPatch(patch).setApplyPartial(true).call();

    }

    @Test
    public void testReversedPatch() throws Exception {
        insert(points1, points2);
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getId());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Object oldValue = points1.getAttribute("sp");
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(oldValue, "new");
        map.put(pointsType.getDescriptor("sp"), diff);
        FeatureDiff feaureDiff = new FeatureDiff(path, map,
                RevFeatureType.builder().type(pointsType).build(),
                RevFeatureType.builder().type(pointsType).build());
        patch.addModifiedFeature(feaureDiff);
        String removedPath = NodeRef.appendChild(pointsName, points2.getId());
        patch.addRemovedFeature(removedPath, RevFeature.builder().build(points2),
                RevFeatureType.builder().type(pointsType).build());
        String addedPath = NodeRef.appendChild(pointsName, points3.getId());
        patch.addAddedFeature(addedPath, RevFeature.builder().build(points3),
                RevFeatureType.builder().type(pointsType).build());
        repo.command(ApplyPatchOp.class).setPatch(patch).call();
        repo.command(ApplyPatchOp.class).setPatch(patch.reversed()).call();
        RevTree root = repo.context().workingTree().getTree();
        Optional<Node> featureBlobId = findTreeChild(root, removedPath);
        assertTrue(featureBlobId.isPresent());
        featureBlobId = findTreeChild(root, addedPath);
        assertFalse(featureBlobId.isPresent());
        Optional<RevFeature> feature = repo.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:" + path).call(RevFeature.class);
        assertTrue(feature.isPresent());
        assertEquals(oldValue, feature.get().getValues().get(0).get());
    }

    @Test
    public void testAddEmptyFeatureTypePatch() throws Exception {
        Patch patch = new Patch();
        RevFeatureType featureType = RevFeatureType.builder().type(pointsType).build();
        patch.addFeatureType(featureType);
        patch.addAlteredTree(new FeatureTypeDiff(pointsName, null, featureType.getId()));
        repo.command(ApplyPatchOp.class).setPatch(patch).call();
        RevTree root = repo.context().workingTree().getTree();
        assertNotNull(root);
        Optional<Node> typeTreeId = findTreeChild(root, pointsName);
        RevTree typeTree = repo.context().objectDatabase().getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);
        assertEquals(featureType.getId(), typeTreeId.get().getMetadataId().get());
    }

    @Test
    public void testRemoveEmptyFeatureTypePatch() throws Exception {
        WorkingTree workingTree = repo.context().workingTree();
        workingTree.createTypeTree(pointsName, pointsType);
        repo.command(AddOp.class).setUpdateOnly(false).call();
        Patch patch = new Patch();
        RevFeatureType featureType = RevFeatureType.builder().type(pointsType).build();
        patch.addFeatureType(featureType);
        patch.addAlteredTree(new FeatureTypeDiff(pointsName, featureType.getId(), null));
        repo.command(ApplyPatchOp.class).setPatch(patch).call();
        RevTree root = repo.context().workingTree().getTree();
        assertNotNull(root);
        Optional<Node> typeTree = findTreeChild(root, pointsName);
        assertFalse(typeTree.isPresent());
    }

    @Test
    public void testModifiedFeatureType() throws Exception {
        insert(points2, points3, points1B);
        Patch patch = new Patch();
        RevFeatureType oldFeatureType = RevFeatureType.builder().type(pointsType).build();
        RevFeatureType featureType = RevFeatureType.builder().type(modifiedPointsType).build();
        patch.addFeatureType(featureType);
        patch.addAlteredTree(
                new FeatureTypeDiff(pointsName, oldFeatureType.getId(), featureType.getId()));
        repo.command(ApplyPatchOp.class).setPatch(patch).call();
        RevTree root = repo.context().workingTree().getTree();
        assertNotNull(root);
        Optional<Node> typeTree = findTreeChild(root, pointsName);
        assertTrue(typeTree.isPresent());
        assertEquals(featureType.getId(), typeTree.get().getMetadataId().get());
        Optional<Node> featureNode = findTreeChild(root, NodeRef.appendChild(pointsName, idP2));
        assertTrue(featureNode.isPresent());
        assertEquals(oldFeatureType.getId(), featureNode.get().getMetadataId().get());
        featureNode = findTreeChild(root, NodeRef.appendChild(pointsName, idP1));
        assertTrue(featureNode.isPresent());
        assertFalse(featureNode.get().getMetadataId().isPresent());
    }

    @Test
    public void testAddFeatureWithNonDefaultFeatureType() throws Exception {
        insert(points2, points3);
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getId());
        patch.addAddedFeature(path, RevFeature.builder().build(points1B),
                RevFeatureType.builder().type(modifiedPointsType).build());
        repo.command(ApplyPatchOp.class).setPatch(patch).call();
        RevTree root = repo.context().workingTree().getTree();
        assertNotNull(root);
        Optional<Node> typeTreeId = findTreeChild(root, pointsName);
        assertEquals(typeTreeId.get().getMetadataId().get(),
                RevFeatureType.builder().type(pointsType).build().getId());
        RevTree typeTree = repo.context().objectDatabase().getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);
        Optional<Node> featureBlobId = findTreeChild(root, path);
        assertEquals(RevFeatureType.builder().type(modifiedPointsType).build().getId(),
                featureBlobId.get().getMetadataId().orElse(null));
        assertTrue(featureBlobId.isPresent());
        path = NodeRef.appendChild(pointsName, points3.getId());
        featureBlobId = findTreeChild(root, path);
        assertEquals(null, featureBlobId.get().getMetadataId().orElse(null));

    }

}
