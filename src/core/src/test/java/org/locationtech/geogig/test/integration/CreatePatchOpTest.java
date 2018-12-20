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

import org.junit.Test;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.diff.Patch;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.CreatePatchOp;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;

public class CreatePatchOpTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Test
    public void testCreatePatch() throws Exception {
        insertAndAdd(points1, points2);
        geogig.command(CommitOp.class).setAll(true).call();

        final String featureId = points1.getIdentifier().getID();
        final Feature modifiedFeature = feature((SimpleFeatureType) points1.getType(), featureId,
                "changedProp", new Integer(1500), "POINT (2 2)");
        insert(modifiedFeature);
        insert(points3);
        delete(points2);

        try (AutoCloseableIterator<DiffEntry> diffs = geogig.command(DiffOp.class).call()) {
            Patch patch = geogig.command(CreatePatchOp.class).setDiffs(diffs).call();

            assertEquals(3, patch.count());
            assertEquals(1, patch.getAddedFeatures().size());
            assertEquals(1, patch.getRemovedFeatures().size());
            assertEquals(1, patch.getModifiedFeatures().size());
            assertEquals(RevFeatureType.builder().type(pointsType).build(),
                    patch.getFeatureTypes().get(0));
            assertEquals(NodeRef.appendChild(pointsName, idP2),
                    patch.getRemovedFeatures().get(0).getPath());
            assertEquals(NodeRef.appendChild(pointsName, idP3),
                    patch.getAddedFeatures().get(0).getPath());
        }

    }

    @Test
    public void testCreatePatchUsingIndex() throws Exception {
        insertAndAdd(points1, points2);
        geogig.command(CommitOp.class).setAll(true).call();

        final String featureId = points1.getIdentifier().getID();
        final Feature modifiedFeature = feature((SimpleFeatureType) points1.getType(), featureId,
                "changedProp", new Integer(1500), null);

        insertAndAdd(modifiedFeature);
        insertAndAdd(points3);
        deleteAndAdd(points2);
        delete(points3);
        DiffOp op = geogig.command(DiffOp.class);
        op.setCompareIndex(true);
        try (AutoCloseableIterator<DiffEntry> diffs = op.call()) {
            Patch patch = geogig.command(CreatePatchOp.class).setDiffs(diffs).call();

            assertEquals(3, patch.count());
            assertEquals(1, patch.getAddedFeatures().size());
            assertEquals(1, patch.getRemovedFeatures().size());
            assertEquals(1, patch.getModifiedFeatures().size());
            assertEquals(RevFeatureType.builder().type(pointsType).build(),
                    patch.getFeatureTypes().get(0));
            assertEquals(NodeRef.appendChild(pointsName, idP2),
                    patch.getRemovedFeatures().get(0).getPath());
            assertEquals(NodeRef.appendChild(pointsName, idP3),
                    patch.getAddedFeatures().get(0).getPath());
        }
    }

    @Test
    public void testCreatePatchWithNoChanges() throws Exception {
        insertAndAdd(points1, points2);
        geogig.command(CommitOp.class).setAll(true).call();
        try (AutoCloseableIterator<DiffEntry> diffs = geogig.command(DiffOp.class).call()) {
            Patch patch = geogig.command(CreatePatchOp.class).setDiffs(diffs).call();
            assertEquals(0, patch.count());
        }
    }

    @Test
    public void testCreatePatchAddNewFeatureToEmptyRepo() throws Exception {
        insert(points1);
        DiffOp op = geogig.command(DiffOp.class);
        try (AutoCloseableIterator<DiffEntry> diffs = op.call()) {
            Patch patch = geogig.command(CreatePatchOp.class).setDiffs(diffs).call();
            assertEquals(1, patch.getAddedFeatures().size());
        }
    }

    @Test
    public void testCreatePatchAddNewEmptyFeatureTypeToEmptyRepo() throws Exception {
        WorkingTree workingTree = geogig.getRepository().workingTree();
        workingTree.createTypeTree(linesName, linesType);
        DiffOp op = geogig.command(DiffOp.class).setReportTrees(true);
        try (AutoCloseableIterator<DiffEntry> diffs = op.call()) {
            Patch patch = geogig.command(CreatePatchOp.class).setDiffs(diffs).call();
            assertEquals(1, patch.getAlteredTrees().size());
            assertEquals(ObjectId.NULL, patch.getAlteredTrees().get(0).getOldFeatureType());
            assertEquals(RevFeatureType.builder().type(linesType).build().getId(),
                    patch.getAlteredTrees().get(0).getNewFeatureType());
            assertEquals(1, patch.getFeatureTypes().size());
        }
    }

    @Test
    public void testCreatePatchRemoveEmptyFeatureType() throws Exception {
        WorkingTree workingTree = geogig.getRepository().workingTree();
        workingTree.createTypeTree(linesName, linesType);
        geogig.command(AddOp.class).setUpdateOnly(false).call();
        workingTree.delete(linesName);
        DiffOp op = geogig.command(DiffOp.class).setReportTrees(true);
        try (AutoCloseableIterator<DiffEntry> diffs = op.call()) {
            Patch patch = geogig.command(CreatePatchOp.class).setDiffs(diffs).call();
            assertEquals(1, patch.getAlteredTrees().size());
            assertEquals(RevFeatureType.builder().type(linesType).build().getId(),
                    patch.getAlteredTrees().get(0).getOldFeatureType());
            assertEquals(ObjectId.NULL, patch.getAlteredTrees().get(0).getNewFeatureType());
            assertEquals(1, patch.getFeatureTypes().size());
        }
    }

    @Test
    public void testCreatePatchModifyFeatureType() throws Exception {
        DiffOp op = geogig.command(DiffOp.class).setReportTrees(true);

        insertAndAdd(points1, points2);
        geogig.getRepository().workingTree().updateTypeTree(pointsName, modifiedPointsType);

        try (AutoCloseableIterator<DiffEntry> diffs = op.call()) {
            Patch patch = geogig.command(CreatePatchOp.class).setDiffs(diffs).call();
            assertEquals(1, patch.getAlteredTrees().size());
            assertEquals(RevFeatureType.builder().type(pointsType).build().getId(),
                    patch.getAlteredTrees().get(0).getOldFeatureType());
            assertEquals(RevFeatureType.builder().type(modifiedPointsType).build().getId(),
                    patch.getAlteredTrees().get(0).getNewFeatureType());
            assertEquals(2, patch.getFeatureTypes().size());
        }
    }

    @Test
    public void testCreatePatchAddNewEmptyPath() throws Exception {
        insert(points1);
        delete(points1);
        DiffOp op = geogig.command(DiffOp.class).setReportTrees(true);
        try (AutoCloseableIterator<DiffEntry> diffs = op.call()) {
            // ArrayList<DiffEntry> list = Lists.newArrayList(diffs);
            Patch patch = geogig.command(CreatePatchOp.class).setDiffs(diffs).call();
            assertEquals(1, patch.getAlteredTrees().size());
        }
    }

}
