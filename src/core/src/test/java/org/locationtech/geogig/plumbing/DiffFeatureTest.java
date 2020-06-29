/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.plumbing.diff.FeatureDiff;
import org.locationtech.geogig.porcelain.FeatureNodeRefFromRefspec;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Suppliers;

public class DiffFeatureTest extends RepositoryTestCase {

    protected @Override void setUpInternal() throws Exception {
        populate(true, points1);
        insert(points1_modified);
    }

    @Test
    public void testDiffBetweenEditedFeatures() {
        NodeRef oldRef = repo.command(FeatureNodeRefFromRefspec.class)
                .setRefspec("HEAD:" + NodeRef.appendChild(pointsName, idP1)).call().orElse(null);
        NodeRef newRef = repo.command(FeatureNodeRefFromRefspec.class)
                .setRefspec(NodeRef.appendChild(pointsName, idP1)).call().orElse(null);
        FeatureDiff diff = repo.command(DiffFeature.class)
                .setOldVersion(Suppliers.ofInstance(oldRef))
                .setNewVersion(Suppliers.ofInstance(newRef)).call();
        assertTrue(diff.hasDifferences());
        System.out.println(diff);
    }

    @Test
    public void testDiffBetweenFeatureAndItself() {
        NodeRef oldRef = repo.command(FeatureNodeRefFromRefspec.class)
                .setRefspec(NodeRef.appendChild(pointsName, idP1)).call().orElse(null);
        NodeRef newRef = repo.command(FeatureNodeRefFromRefspec.class)
                .setRefspec(NodeRef.appendChild(pointsName, idP1)).call().orElse(null);
        FeatureDiff diff = repo.command(DiffFeature.class)
                .setOldVersion(Suppliers.ofInstance(oldRef))
                .setNewVersion(Suppliers.ofInstance(newRef)).call();
        assertFalse(diff.hasDifferences());
        System.out.println(diff);
    }

    @Test
    public void testDiffUnexistentFeature() {
        try {
            NodeRef oldRef = repo.command(FeatureNodeRefFromRefspec.class)
                    .setRefspec(NodeRef.appendChild(pointsName, "Points.100")).call().orElse(null);
            NodeRef newRef = repo.command(FeatureNodeRefFromRefspec.class)
                    .setRefspec(NodeRef.appendChild(pointsName, idP1)).call().orElse(null);
            repo.command(DiffFeature.class).setOldVersion(Suppliers.ofInstance(oldRef))
                    .setNewVersion(Suppliers.ofInstance(newRef)).call();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testDiffWrongPath() {
        try {
            NodeRef oldRef = repo.command(FeatureNodeRefFromRefspec.class).setRefspec(pointsName)
                    .call().orElse(null);
            NodeRef newRef = repo.command(FeatureNodeRefFromRefspec.class)
                    .setRefspec(NodeRef.appendChild(pointsName, idP1)).call().orElse(null);
            repo.command(DiffFeature.class).setOldVersion(Suppliers.ofInstance(oldRef))
                    .setNewVersion(Suppliers.ofInstance(newRef)).call();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

}
