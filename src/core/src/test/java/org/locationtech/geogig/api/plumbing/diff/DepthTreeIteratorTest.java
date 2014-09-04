/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.diff;

import static org.locationtech.geogig.api.plumbing.diff.TreeTestSupport.createFeaturesTree;
import static org.locationtech.geogig.api.plumbing.diff.TreeTestSupport.createTreesTree;
import static org.locationtech.geogig.api.plumbing.diff.TreeTestSupport.featureNode;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.api.plumbing.diff.DepthTreeIterator.Strategy;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectDatabse;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

/**
 *
 */
public class DepthTreeIteratorTest extends Assert {

    private ObjectDatabase source;

    private String treePath;

    private ObjectId metadataId;

    private RevTree emptyTree, featuresLeafTree, treesLeafTree, mixedLeafTree;

    private RevTree featuresBucketsTree;

    @Before
    public void setUp() {
        source = new HeapObjectDatabse();
        source.open();

        metadataId = ObjectId.forString("fake id");
        treePath = "";
        emptyTree = RevTree.EMPTY;
        featuresLeafTree = createFeaturesTree(source, "featuresLeafTree", 100).build();
        assertTrue(featuresLeafTree.features().isPresent());

        treesLeafTree = createTreesTree(source, 100, 10, metadataId).build();
        assertTrue(treesLeafTree.trees().isPresent());

        RevTreeBuilder builder = createTreesTree(source, 10, 10, metadataId);
        for (int i = 0; i < 100; i++) {
            builder.put(featureNode("feature.", i));
        }
        mixedLeafTree = builder.build();

        featuresBucketsTree = createFeaturesTree(source, "feature.", 25000).build();
    }

    @Test
    public void testFeaturesLeafTree() {
        assertEquals(0, list(emptyTree, Strategy.FEATURES_ONLY).size());
        assertEquals(100, list(featuresLeafTree, Strategy.FEATURES_ONLY).size());
        assertEquals(0, list(treesLeafTree, Strategy.FEATURES_ONLY).size());
        assertEquals(100, list(mixedLeafTree, Strategy.FEATURES_ONLY).size());
    }

    @Test
    public void testFeaturesBucketsTree() {
        int numEntries = 2 * RevTree.NORMALIZED_SIZE_LIMIT;
        RevTree tree = createFeaturesTree(source, "feature.", numEntries).build();
        assertEquals(numEntries, list(tree, Strategy.FEATURES_ONLY).size());

        assertEquals(featuresBucketsTree.size(), list(featuresBucketsTree, Strategy.FEATURES_ONLY)
                .size());
    }

    @Test
    public void testChildren() {
        assertEquals(0, list(emptyTree, Strategy.CHILDREN).size());
        assertEquals(100, list(featuresLeafTree, Strategy.CHILDREN).size());
        assertEquals(100, list(treesLeafTree, Strategy.CHILDREN).size());
        assertEquals(110, list(mixedLeafTree, Strategy.CHILDREN).size());
        assertEquals(25000, list(featuresBucketsTree, Strategy.CHILDREN).size());
    }

    @Test
    public void testTrees() {
        assertEquals(0, list(emptyTree, Strategy.TREES_ONLY).size());
        assertEquals(0, list(featuresLeafTree, Strategy.TREES_ONLY).size());
        assertEquals(100, list(treesLeafTree, Strategy.TREES_ONLY).size());
        assertEquals(10, list(mixedLeafTree, Strategy.TREES_ONLY).size());
        assertEquals(0, list(featuresBucketsTree, Strategy.TREES_ONLY).size());

        int numSubTrees = RevTree.NORMALIZED_SIZE_LIMIT + 1;
        int featuresPerTree = RevTree.NORMALIZED_SIZE_LIMIT + 1;
        RevTreeBuilder builder = createTreesTree(source, numSubTrees, featuresPerTree, metadataId);
        for (int i = 0; i < 25000; i++) {
            builder.put(featureNode("f", i));
        }
        RevTree mixedBucketsTree = builder.build();
        assertEquals(numSubTrees, list(mixedBucketsTree, Strategy.TREES_ONLY).size());
    }

    @Test
    public void testRecursive() {
        assertEquals(0, list(emptyTree, Strategy.RECURSIVE).size());
        assertEquals(100, list(featuresLeafTree, Strategy.RECURSIVE).size());
        assertEquals(treesLeafTree.size() + treesLeafTree.numTrees(),
                list(treesLeafTree, Strategy.RECURSIVE).size());
        assertEquals(mixedLeafTree.size() + mixedLeafTree.numTrees(),
                list(mixedLeafTree, Strategy.RECURSIVE).size());
        assertEquals(featuresBucketsTree.size(), list(featuresBucketsTree, Strategy.RECURSIVE)
                .size());

        int numSubTrees = RevTree.NORMALIZED_SIZE_LIMIT + 1;
        int featuresPerTree = RevTree.NORMALIZED_SIZE_LIMIT + 1;
        RevTreeBuilder builder = createTreesTree(source, numSubTrees, featuresPerTree, metadataId);
        for (int i = 0; i < 25000; i++) {
            builder.put(featureNode("f", i));
        }
        RevTree mixedBucketsTree = builder.build();
        assertEquals(mixedBucketsTree.size() + mixedBucketsTree.numTrees(),
                list(mixedBucketsTree, Strategy.RECURSIVE).size());
    }

    @Test
    public void testRecursiveFeaturesOnly() {
        assertEquals(0, list(emptyTree, Strategy.RECURSIVE_FEATURES_ONLY).size());
        assertEquals(100, list(featuresLeafTree, Strategy.RECURSIVE_FEATURES_ONLY).size());
        assertEquals(treesLeafTree.size(), list(treesLeafTree, Strategy.RECURSIVE_FEATURES_ONLY)
                .size());
        assertEquals(mixedLeafTree.size(), list(mixedLeafTree, Strategy.RECURSIVE_FEATURES_ONLY)
                .size());
        assertEquals(featuresBucketsTree.size(),
                list(featuresBucketsTree, Strategy.RECURSIVE_FEATURES_ONLY).size());

        int numSubTrees = RevTree.NORMALIZED_SIZE_LIMIT + 1;
        int featuresPerTree = RevTree.NORMALIZED_SIZE_LIMIT + 1;
        RevTreeBuilder builder = createTreesTree(source, numSubTrees, featuresPerTree, metadataId);
        for (int i = 0; i < 25000; i++) {
            builder.put(featureNode("f", i));
        }
        RevTree mixedBucketsTree = builder.build();
        assertEquals(mixedBucketsTree.size(),
                list(mixedBucketsTree, Strategy.RECURSIVE_FEATURES_ONLY).size());
    }

    @Test
    public void testRecursiveTreesOnly() {
        assertEquals(0, list(emptyTree, Strategy.RECURSIVE_TREES_ONLY).size());
        assertEquals(0, list(featuresLeafTree, Strategy.RECURSIVE_TREES_ONLY).size());
        assertEquals(treesLeafTree.numTrees(), list(treesLeafTree, Strategy.RECURSIVE_TREES_ONLY)
                .size());
        assertEquals(mixedLeafTree.numTrees(), list(mixedLeafTree, Strategy.RECURSIVE_TREES_ONLY)
                .size());
        assertEquals(0, list(featuresBucketsTree, Strategy.RECURSIVE_TREES_ONLY).size());

        int numSubTrees = RevTree.NORMALIZED_SIZE_LIMIT + 1;
        int featuresPerTree = RevTree.NORMALIZED_SIZE_LIMIT + 1;
        RevTreeBuilder builder = createTreesTree(source, numSubTrees, featuresPerTree, metadataId);
        for (int i = 0; i < 25000; i++) {
            builder.put(featureNode("f", i));
        }
        RevTree mixedBucketsTree = builder.build();
        Stopwatch sw = Stopwatch.createStarted();
        assertEquals(numSubTrees, list(mixedBucketsTree, Strategy.RECURSIVE_TREES_ONLY).size());
        sw.stop();
        System.err.println(sw);
    }

    private List<NodeRef> list(RevTree tree, Strategy strategy) {
        List<NodeRef> refs = Lists.newArrayList(iterator(tree, strategy));
        return refs;
    }

    private DepthTreeIterator iterator(RevTree tree, Strategy strategy) {
        DepthTreeIterator iterator = new DepthTreeIterator(treePath, metadataId, tree, source,
                strategy);
        return iterator;
    }

}
