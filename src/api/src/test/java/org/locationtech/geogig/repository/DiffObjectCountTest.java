/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.repository;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DiffObjectCountTest {

    @Test
    public void testConstructorAndAccessors() {
        DiffObjectCount count = new DiffObjectCount();
        assertEquals(0, count.getFeaturesAdded());
        assertEquals(0, count.getFeaturesChanged());
        assertEquals(0, count.getFeaturesRemoved());
        assertEquals(0, count.getTreesAdded());
        assertEquals(0, count.getTreesChanged());
        assertEquals(0, count.getTreesRemoved());
        assertEquals(0, count.featureCount());
        assertEquals(0, count.treeCount());
        assertEquals(0, count.count());
    }

    @Test
    public void testIncrementing() {
        DiffObjectCount count = new DiffObjectCount();
        // added features
        assertEquals(20, count.addedFeatures(20));
        assertEquals(20, count.getFeaturesAdded());
        assertEquals(20, count.featureCount());
        assertEquals(0, count.treeCount());
        assertEquals(20, count.count());

        // added features again
        assertEquals(30, count.addedFeatures(10));
        assertEquals(30, count.getFeaturesAdded());
        assertEquals(30, count.featureCount());
        assertEquals(0, count.treeCount());
        assertEquals(30, count.count());

        // changed features
        assertEquals(15, count.changedFeatures(15));
        assertEquals(15, count.getFeaturesChanged());
        assertEquals(45, count.featureCount());
        assertEquals(0, count.treeCount());
        assertEquals(45, count.count());

        // changed features again
        assertEquals(30, count.changedFeatures(15));
        assertEquals(30, count.getFeaturesChanged());
        assertEquals(60, count.featureCount());
        assertEquals(0, count.treeCount());
        assertEquals(60, count.count());

        // removed features
        assertEquals(5, count.removedFeatures(5));
        assertEquals(5, count.getFeaturesRemoved());
        assertEquals(65, count.featureCount());
        assertEquals(0, count.treeCount());
        assertEquals(65, count.count());

        // removed features again
        assertEquals(10, count.removedFeatures(5));
        assertEquals(10, count.getFeaturesRemoved());
        assertEquals(70, count.featureCount());
        assertEquals(0, count.treeCount());
        assertEquals(70, count.count());

        // added trees
        assertEquals(10, count.addedTrees(10));
        assertEquals(10, count.getTreesAdded());
        assertEquals(70, count.featureCount());
        assertEquals(10, count.treeCount());
        assertEquals(80, count.count());

        // added trees again
        assertEquals(20, count.addedTrees(10));
        assertEquals(20, count.getTreesAdded());
        assertEquals(70, count.featureCount());
        assertEquals(20, count.treeCount());
        assertEquals(90, count.count());

        // changed trees
        assertEquals(15, count.changedTrees(15));
        assertEquals(15, count.getTreesChanged());
        assertEquals(70, count.featureCount());
        assertEquals(35, count.treeCount());
        assertEquals(105, count.count());

        // changed trees again
        assertEquals(30, count.changedTrees(15));
        assertEquals(30, count.getTreesChanged());
        assertEquals(70, count.featureCount());
        assertEquals(50, count.treeCount());
        assertEquals(120, count.count());

        // removed trees
        assertEquals(5, count.removedTrees(5));
        assertEquals(5, count.getTreesRemoved());
        assertEquals(70, count.featureCount());
        assertEquals(55, count.treeCount());
        assertEquals(125, count.count());

        // removed trees again
        assertEquals(10, count.removedTrees(5));
        assertEquals(10, count.getTreesRemoved());
        assertEquals(70, count.featureCount());
        assertEquals(60, count.treeCount());
        assertEquals(130, count.count());
    }

    @Test
    public void testToString() {
        DiffObjectCount count = new DiffObjectCount();
        count.addedFeatures(1);
        count.changedFeatures(2);
        count.removedFeatures(3);
        count.addedTrees(4);
        count.changedTrees(5);
        count.removedTrees(6);

        String expected = "trees   [ added: 4, changed: 5, removed: 6]\n"
                + "features[ added: 1, changed: 2, removed: 3]";

        assertEquals(expected, count.toString());
    }
}
