/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator.Strategy;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;

@SuppressWarnings("deprecation")
public class CanonicalTreeBuilderTest extends RevTreeBuilderTest {

    @Override
    protected RevTreeBuilder createBuiler() {
        return CanonicalTreeBuilder.create(objectStore);
    }

    @Override
    protected CanonicalTreeBuilder createBuiler(RevTree original) {
        return CanonicalTreeBuilder.create(objectStore, original);
    }

    @Test
    public void testRemove() throws Exception {
        final int numEntries = 100;
        ObjectId treeId = createAndSaveTree(numEntries, true);
        final RevTree tree = objectStore.getTree(treeId);

        // collect some keys to remove
        final Set<Node> removedKeys = new HashSet<>();
        {
            int i = 0;
            DepthTreeIterator it = new DepthTreeIterator("", ObjectId.NULL, tree, objectStore,
                    Strategy.CHILDREN);
            for (; it.hasNext(); i++) {
                NodeRef entry = it.next();
                if (i % 10 == 0) {
                    removedKeys.add(entry.getNode());
                }
            }
            // assertEquals(100, removedKeys.size());
        }

        final LegacyTreeBuilder legacy = createLegacyBuilder(tree);
        final CanonicalTreeBuilder builder = createBuiler(tree);
        for (Node key : removedKeys) {
            builder.remove(key);
            legacy.remove(key);
        }

        final RevTree legacyResult = legacy.build();
        final RevTree result = builder.build();

        assertEquals(legacyResult, result);

        Iterator<NodeRef> it = new DepthTreeIterator("", ObjectId.NULL, result, objectStore,
                Strategy.CHILDREN);
        Function<NodeRef, String> asName = v -> {
            return v.name();
        };
        HashSet<String> names = Sets.newHashSet(Iterators.transform(it, asName));
        assertEquals(numEntries - removedKeys.size(), names.size());
        for (Node key : removedKeys) {
            assertFalse(names.contains(key.getName()));
        }
    }

    @Test
    public void testRemoveSplittedTree() throws Exception {
        final int numEntries = (int) (1.5 * CanonicalNodeNameOrder.normalizedSizeLimit(0));
        final ObjectId treeId = createAndSaveTree(numEntries, true);
        final RevTree tree = objectStore.getTree(treeId);

        // collect some keys to remove
        final Set<Node> removedKeys = new HashSet<>();
        {
            int i = 0;
            DepthTreeIterator it = new DepthTreeIterator("", ObjectId.NULL, tree, objectStore,
                    Strategy.CHILDREN);
            for (; it.hasNext(); i++) {
                NodeRef entry = it.next();
                if (i % 10 == 0) {
                    removedKeys.add(entry.getNode());
                }
            }
            assertTrue(removedKeys.size() > 0);
        }

        final LegacyTreeBuilder legacy = createLegacyBuilder(tree);
        final CanonicalTreeBuilder builder = createBuiler(tree);
        for (Node key : removedKeys) {
            builder.remove(key);
            legacy.remove(key);
        }

        final RevTree legacyResult = legacy.build();
        final RevTree result = builder.build();
        assertEquals(legacyResult, result);

        Iterator<NodeRef> it = new DepthTreeIterator("", ObjectId.NULL, result, objectStore,
                Strategy.CHILDREN);
        Function<NodeRef, String> asName = v -> {
            return v.name();
        };
        HashSet<String> names = Sets.newHashSet(Iterators.transform(it, asName));
        assertEquals(numEntries - removedKeys.size(), names.size());
        for (Node key : removedKeys) {
            assertFalse(names.contains(key.getName()));
        }
    }

    @Test
    public void testShrinksOnRemoveBellowThreshold() throws Exception {
        final int numEntries = (int) (1.5 * CanonicalNodeNameOrder.normalizedSizeLimit(0));
        final ObjectId treeId = createAndSaveTree(numEntries, true);
        final RevTree tree = objectStore.getTree(treeId);

        assertEquals(numEntries, tree.size());

        // remove all but enough to have an unsplitted tree
        final long resultSize = 100;
        final Set<Node> removedKeys = new HashSet<>();
        {
            DepthTreeIterator it = new DepthTreeIterator("", ObjectId.NULL, tree, objectStore,
                    Strategy.CHILDREN);
            for (int i = 0; i < numEntries - resultSize; i++) {
                NodeRef entry = it.next();
                removedKeys.add(entry.getNode());
            }
        }

        final LegacyTreeBuilder legacy = createLegacyBuilder(tree);
        final CanonicalTreeBuilder builder = createBuiler(tree);
        for (Node key : removedKeys) {
            builder.remove(key);
            legacy.remove(key);
        }

        final RevTree legacyResult = legacy.build();
        final RevTree result = builder.build();
        assertEquals(resultSize, result.size());
        assertEquals(0, result.bucketsSize());
        assertEquals(legacyResult, result);
    }

    @Test
    public void testSplitsOnAdd() throws Exception {
        final RevTree legacyFull;
        final RevTree leafFull;
        {
            LegacyTreeBuilder legacy = createLegacyBuilder(RevTree.EMPTY);
            RevTreeBuilder builder = createBuiler();
            for (int i = 0; i < CanonicalNodeNameOrder.normalizedSizeLimit(0); i++) {
                Node node = createNode(i);
                builder.put(node);
                legacy.put(node);
            }
            leafFull = builder.build();
            legacyFull = legacy.build();
        }
        assertEquals(CanonicalNodeNameOrder.normalizedSizeLimit(0), leafFull.size());
        assertEquals(0, leafFull.bucketsSize());
        assertEquals(legacyFull, leafFull);

        final RevTree legacyExpanded;
        final RevTree expanded;
        {
            LegacyTreeBuilder legacy = createLegacyBuilder(leafFull);
            RevTreeBuilder builder = createBuiler(leafFull);
            for (int i = CanonicalNodeNameOrder.normalizedSizeLimit(0); i < 2
                    * CanonicalNodeNameOrder.normalizedSizeLimit(0); i++) {
                Node node = createNode(i);
                builder.put(node);
                legacy.put(node);
            }
            expanded = builder.build();
            legacyExpanded = legacy.build();
        }

        assertEquals(2 * CanonicalNodeNameOrder.normalizedSizeLimit(0), expanded.size());
        assertTrue(expanded.features().isEmpty());
        assertNotEquals(0, expanded.bucketsSize());
        List<Node> lstree = lstree(expanded);
        assertEquals(2 * CanonicalNodeNameOrder.normalizedSizeLimit(0), lstree.size());
        assertEquals(legacyExpanded, expanded);
    }

    @Test
    public void testNodeOrderPassSplitThreshold() {
        final int splitThreshold = CanonicalNodeNameOrder.normalizedSizeLimit(0);
        List<Node> expectedOrder = createNodes(splitThreshold + 1);
        Collections.sort(expectedOrder, CanonicalNodeOrder.INSTANCE);

        final List<Node> flat = expectedOrder.subList(0, splitThreshold);
        RevTreeBuilder flatTreeBuilder = createBuiler();
        RevTreeBuilder bucketTreeBuilder = createBuiler();

        for (Node n : flat) {
            flatTreeBuilder.put(n);
            bucketTreeBuilder.put(n);
        }
        bucketTreeBuilder.put(expectedOrder.get(expectedOrder.size() - 1));
        RevTree flatTree = flatTreeBuilder.build();
        RevTree bucketTree = bucketTreeBuilder.build();
        assertEquals(0, flatTree.bucketsSize());
        assertNotEquals(0, bucketTree.bucketsSize());
        objectStore.put(flatTree);
        objectStore.put(bucketTree);

        List<Node> flatNodes = lstree(flatTree);
        assertEquals(flat, flatNodes);

        List<Node> splitNodes = lstree(bucketTree);
        assertEquals(expectedOrder, splitNodes);
    }

}
