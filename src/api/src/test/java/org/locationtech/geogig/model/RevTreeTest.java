/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.jts.geom.Envelope;

public class RevTreeTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testEmptyTree() {
        RevTree emptyTree = RevTree.EMPTY;
        assertEquals(TYPE.TREE, emptyTree.getType());
        assertEquals(RevTree.EMPTY_TREE_ID, emptyTree.getId());
        assertEquals(0, emptyTree.trees().size());
        assertEquals(0, emptyTree.features().size());
        assertEquals(0, emptyTree.bucketsSize());
        assertEquals(0, emptyTree.size());
        assertEquals(0, emptyTree.numTrees());
        assertTrue(emptyTree.isEmpty());
        assertTrue(emptyTree.toString().contains("EMPTY TREE"));
        assertEquals(emptyTree, emptyTree);
    }

    @Test
    public void testIsEmpty() {
        SortedMap<Integer, Bucket> buckets = new TreeMap<Integer, Bucket>();
        List<Node> trees = new LinkedList<Node>();
        List<Node> features = new LinkedList<Node>();

        final Node testNode = RevObjectFactory.defaultInstance().createNode("Points",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.TREE, null, null);
        final Bucket testBucket = RevObjectFactory.defaultInstance().createBucket(
                ObjectId.valueOf("abc123000000000000001234567890abcdef0002"), 0,
                new Envelope(0, 0, 1, 1));

        RevTree testTree = new RevTree() {
            public @Override ObjectId getId() {
                return RevTree.EMPTY_TREE_ID;
            }

            public @Override long size() {
                return 0;
            }

            public @Override int numTrees() {
                return 0;
            }

            public @Override List<Node> trees() {
                return new ArrayList<>(trees);
            }

            public @Override List<Node> features() {
                return new ArrayList<>(features);
            }

            public @Override Iterable<Bucket> getBuckets() {
                return buckets.values();
            }
        };

        assertTrue(testTree.isEmpty());
        trees.add(testNode);
        assertFalse(testTree.isEmpty());
        trees.clear();
        features.add(testNode);
        assertFalse(testTree.isEmpty());
        features.clear();
        buckets.put(0, testBucket);
        assertFalse(testTree.isEmpty());
        buckets.clear();

    }

    @Test
    public void testEmptyTreeWithDifferentObjectId() {
        RevTree testTree = new RevTree() {
            public @Override ObjectId getId() {
                return ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
            }

            public @Override long size() {
                return 0;
            }

            public @Override int numTrees() {
                return 0;
            }

            public @Override List<Node> trees() {
                return Collections.emptyList();
            }

            public @Override List<Node> features() {
                return Collections.emptyList();
            }

            public @Override Iterable<Bucket> getBuckets() {
                return Collections.emptySet();
            }
        };

        // Empty tree, but not the empty tree object id.
        exception.expect(IllegalStateException.class);
        testTree.isEmpty();
    }

    @Test
    public void testEmptyTreeWithNonZeroSize() {
        RevTree testTree = new RevTree() {
            public @Override ObjectId getId() {
                return RevTree.EMPTY_TREE_ID;
            }

            public @Override long size() {
                return 1;
            }

            public @Override int numTrees() {
                return 0;
            }

            public @Override List<Node> trees() {
                return Collections.emptyList();
            }

            public @Override List<Node> features() {
                return Collections.emptyList();
            }

            public @Override Iterable<Bucket> getBuckets() {
                return Collections.emptySet();
            }
        };

        // Empty tree, but not the empty tree object id.
        exception.expect(IllegalStateException.class);
        testTree.isEmpty();
    }

    @Test
    public void testChildren() {
        List<Node> trees = new LinkedList<Node>();
        List<Node> features = new LinkedList<Node>();

        final Node testNode1 = RevObjectFactory.defaultInstance().createNode("Points",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.TREE, null, null);
        final Node testNode2 = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0002"), ObjectId.NULL,
                TYPE.FEATURE, null, null);

        Comparator<Node> comparator = new Comparator<Node>() {
            public @Override int compare(Node o1, Node o2) {
                return o1.compareTo(o2);
            }
        };

        RevTree testTree = new RevTree() {
            public @Override ObjectId getId() {
                return RevTree.EMPTY_TREE_ID;
            }

            public @Override long size() {
                return 0;
            }

            public @Override int numTrees() {
                return 0;
            }

            public @Override List<Node> trees() {
                return new ArrayList<>(trees);
            }

            public @Override List<Node> features() {
                return new ArrayList<>(features);
            }

            public @Override Iterable<Bucket> getBuckets() {
                return Collections.emptySet();
            }
        };

        trees.add(testNode1);

        Iterator<Node> result = RevObjects.children(testTree, comparator);
        assertTrue(result.hasNext());
        assertEquals(testNode1, result.next());
        assertFalse(result.hasNext());

        features.add(testNode2);

        result = RevObjects.children(testTree, comparator);
        assertTrue(result.hasNext());
        assertEquals(testNode1, result.next());
        assertTrue(result.hasNext());
        assertEquals(testNode2, result.next());

        trees.clear();

        result = RevObjects.children(testTree, comparator);
        assertTrue(result.hasNext());
        assertEquals(testNode2, result.next());
        assertFalse(result.hasNext());

        // Try reversing the comparator
        Comparator<Node> comparator2 = new Comparator<Node>() {
            public @Override int compare(Node o1, Node o2) {
                return -o1.compareTo(o2);
            }
        };

        trees.add(testNode1);

        result = RevObjects.children(testTree, comparator2);
        assertTrue(result.hasNext());
        assertEquals(testNode2, result.next());
        assertTrue(result.hasNext());
        assertEquals(testNode1, result.next());
    }
}
