/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation as RevTreeBuilderTest, refactored as TreeBuilderTest
 */
package org.locationtech.geogig.model.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator.Strategy;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectStore;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public abstract class RevTreeBuilderTest {

    protected ObjectStore objectStore;

    protected abstract RevTreeBuilder createBuiler();

    protected abstract RevTreeBuilder createBuiler(RevTree original);

    @SuppressWarnings("deprecation")
    protected LegacyTreeBuilder createLegacyBuilder(RevTree original) {
        return new LegacyTreeBuilder(objectStore, original);
    }

    protected ObjectStore createObjectStore() {
        return new HeapObjectStore();
    }

    @Before
    public void setUp() throws Exception {
        objectStore = createObjectStore();
        objectStore.open();
    }

    @After
    public void after() {
        if (objectStore != null) {
            objectStore.close();
            objectStore = null;
        }
    }

    @Test
    public void testResultingTreeSize() {
        testResultingTreeSize(0);
        testResultingTreeSize(1);
        testResultingTreeSize(7);
        testResultingTreeSize(11);
        testResultingTreeSize(100);
        testResultingTreeSize(987);
        testResultingTreeSize(56789);
        // testResultingTreeSize(1234567);
    }

    private void testResultingTreeSize(int numEntries) {
        objectStore.close();
        objectStore = new HeapObjectDatabase();
        objectStore.open();
        RevTreeBuilder builder = createTree(numEntries, true);
        RevTree tree = builder.build();
        final long declaredSize = tree.size();

        Iterator<NodeRef> it = new DepthTreeIterator("", ObjectId.NULL, tree, objectStore,
                Strategy.RECURSIVE_FEATURES_ONLY);
        long itSize = 0;
        while (it.hasNext()) {
            it.next();
            itSize++;
        }

        assertEquals(numEntries, declaredSize);
        assertEquals(numEntries, itSize);
    }

    @Test
    public void testPutIterate() throws Exception {
        final int numEntries = 513;// 100 * 1000;
        ObjectId treeId;

        treeId = createAndSaveTree(numEntries, true);

        final RevTree tree = objectStore.getTree(treeId);

        DepthTreeIterator it = new DepthTreeIterator("", ObjectId.NULL, tree, objectStore,
                Strategy.CHILDREN);

        int counted = Iterators.size(it);
        assertEquals(numEntries, counted);
    }

    @Test
    public void testPutRandomGet() throws Exception {
        final int numEntries = 20 * CanonicalNodeNameOrder.normalizedSizeLimit(0) + 1500;
        final ObjectId treeId;

        treeId = createAndSaveTree(numEntries, true);

        final RevTree tree = objectStore.getTree(treeId);

        {
            Map<Integer, Node> randomEdits = Maps.newHashMap();
            Random randGen = new Random();
            for (int i = 0; i < tree.size() / 2; i++) {
                int random;
                while (randomEdits.containsKey(random = randGen.nextInt(numEntries))) {
                    ; // $codepro.audit.disable extraSemicolon
                }
                String name = "Feature." + random;
                ObjectId newid = RevObjectTestSupport.hashString(name + "changed");
                Node ref = RevObjectFactory.defaultInstance().createNode(name, newid, ObjectId.NULL,
                        TYPE.FEATURE, null, null);
                randomEdits.put(random, ref);
            }
            RevTreeBuilder mutable = createBuiler(tree);
            for (Node ref : randomEdits.values()) {
                mutable.put(ref);
            }
            mutable.build();
        }
    }

    @Test
    public void testUpdateSplittedTree() throws Exception {
        final int numEntries = 600;// (int) (1.5 * NodePathStorageOrder.normalizedSizeLimit(0));
        final ObjectId treeId = createAndSaveTree(numEntries, true);
        final RevTree tree = objectStore.getTree(treeId);

        // collect some nodes to update
        Map<String, Node> oldValues = new TreeMap<>();
        Map<String, Node> newValues = new TreeMap<>();
        {
            int i = 0;
            DepthTreeIterator it = new DepthTreeIterator("", ObjectId.NULL, tree, objectStore,
                    Strategy.CHILDREN);
            for (; it.hasNext(); i++) {
                NodeRef entry = it.next();
                if (i % 10 == 0) {
                    Node oldNode = entry.getNode();
                    Envelope oldBounds = oldNode.bounds().orNull();
                    Envelope newBounds = null;
                    if (oldBounds != null) {
                        newBounds = new Envelope(oldBounds);
                        newBounds.translate(1, 1);
                    }
                    ObjectId newId = RevObjectTestSupport.hashString("updated" + i);
                    Node update = oldNode.update(newId, newBounds);

                    oldValues.put(oldNode.getName(), oldNode);
                    newValues.put(update.getName(), update);
                }
            }
            assertTrue(newValues.size() > 0);
        }

        RevTreeBuilder builder = createBuiler(tree);
        for (Node change : newValues.values()) {
            Node oldNode = oldValues.get(change.getName());
            assertNotNull(oldNode);
            builder.update(oldNode, change);
        }

        final RevTree result = builder.build();

        assertEquals(tree.size(), result.size());

        Map<String, Node> resultNodes = new TreeMap<>();

        Iterator<NodeRef> it = new DepthTreeIterator("", ObjectId.NULL, result, objectStore,
                Strategy.CHILDREN);
        while (it.hasNext()) {
            NodeRef node = it.next();
            resultNodes.put(node.name(), node.getNode());
        }

        for (String changedNodeName : newValues.keySet()) {
            Node oldNode = oldValues.get(changedNodeName);
            Node newNode = resultNodes.get(changedNodeName);
            assertNotNull(newNode);
            assertFalse(oldNode.equals(newNode));
            assertEquals(newValues.get(changedNodeName), newNode);
        }

    }

    /**
     * Assert two trees that have the same contents resolve to the same id regardless of the order
     * the contents were added
     * 
     * @throws Exception
     */
    @Test
    public void testEquality() throws Exception {
        testEquality(100);
        testEquality(100 + CanonicalNodeNameOrder.normalizedSizeLimit(0));
    }

    private void testEquality(final int numEntries) throws Exception {
        final ObjectId treeId1;
        final ObjectId treeId2;
        List<Node> nodes = createNodes(numEntries);
        treeId1 = createAndSaveTree(nodes, true);
        treeId2 = createAndSaveTree(nodes, false);

        assertEquals(treeId1, treeId2);
    }

    protected List<Node> createNodes(int numEntries) {
        List<Node> nodes = new ArrayList<>(numEntries);
        for (int i = 0; i < numEntries; i++) {
            nodes.add(createNode(i));
        }
        return nodes;
    }

    protected ObjectId createAndSaveTree(final int numEntries,
            final boolean insertInAscendingKeyOrder) throws Exception {
        List<Node> nodes = createNodes(numEntries);
        ObjectId treeId = createAndSaveTree(nodes, insertInAscendingKeyOrder);
        return treeId;
    }

    protected ObjectId createAndSaveTree(final List<Node> nodes, final boolean insertInListOrder)
            throws Exception {

        List<Node> insert = nodes;
        if (!insertInListOrder) {
            insert = new ArrayList<>(nodes);
            Collections.shuffle(insert);
        }
        RevTreeBuilder treeBuilder = createBuiler();
        nodes.forEach((n) -> treeBuilder.put(n));
        RevTree tree = treeBuilder.build();
        assertTrue(objectStore.exists(tree.getId()));

        assertEquals(nodes.size(), tree.size());

        return tree.getId();
    }

    private RevTreeBuilder createTree(final int numEntries,
            final boolean insertInAscendingKeyOrder) {
        RevTreeBuilder tree = createBuiler();

        final int increment = insertInAscendingKeyOrder ? 1 : -1;
        final int from = insertInAscendingKeyOrder ? 0 : numEntries - 1;
        final int breakAt = insertInAscendingKeyOrder ? numEntries : -1;

        for (int i = from; i != breakAt; i += increment) {
            addNode(tree, i);
        }
        return tree;
    }

    private static final ObjectId FAKE_ID = RevObjectTestSupport.hashString("fake");

    private void addNode(RevTreeBuilder tree, int i) {
        Node ref = createNode(i);
        tree.put(ref);
    }

    protected Node createNode(int i) {
        String key = "Feature." + i;
        // ObjectId oid = ObjectId.forString(key);
        // ObjectId metadataId = ObjectId.forString("FeatureType");
        // Node ref = new Node(key, oid, metadataId, TYPE.FEATURE);

        Node ref = RevObjectFactory.defaultInstance().createNode(key, FAKE_ID, FAKE_ID,
                TYPE.FEATURE, new Envelope(i, i + 1, i, i + 1), null);
        return ref;
    }

    @Test
    public void testResultingTreeBounds() throws Exception {
        checkTreeBounds(10);
        checkTreeBounds(100);
        checkTreeBounds(1_000);
        checkTreeBounds(10_000);
        checkTreeBounds(100_000);
    }

    private void checkTreeBounds(int size) {
        RevTreeBuilder b = createBuiler();
        List<Node> nodes = createNodes(size);
        Envelope expectedBounds = new Envelope();

        for (Node n : nodes) {
            b.put(n);
            n.expand(expectedBounds);
        }
        expectedBounds = RevObjects.makePrecise(expectedBounds);

        RevTree tree = b.build();
        assertEquals(size, tree.size());
        Envelope bounds = SpatialOps.boundsOf(tree);
        bounds = RevObjects.makePrecise(bounds);
        assertEquals(expectedBounds, bounds);
    }

    protected List<Node> lstree(RevTree tree) {

        Iterator<NodeRef> it = new DepthTreeIterator("", ObjectId.NULL, tree, objectStore,
                Strategy.CHILDREN);
        Function<NodeRef, Node> asNode = v -> {
            return v.getNode();
        };
        List<Node> nodes = Lists.newArrayList(Iterators.transform(it, asNode));
        return nodes;
    }

    protected void print(RevTree root) {

        PreOrderDiffWalk walk = new PreOrderDiffWalk(RevTree.EMPTY, root, objectStore, objectStore,
                true);

        PrintStream out = System.err;

        walk.walk(new PreOrderDiffWalk.AbstractConsumer() {
            String indent = "";

            @Override
            public boolean bucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                    @Nullable Bucket left, @Nullable Bucket right) {
                out.printf("%sBUCKET: [%s] %s\n", indent, bucketIndex, right);
                int indentLength = 2 * (1 + bucketIndex.depthIndex());
                indent = Strings.padStart("", indentLength, ' ');
                return true;
            }

            @Override
            public void endBucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                    @Nullable Bucket left, @Nullable Bucket right) {
                int indentLength = 2 * (bucketIndex.depthIndex());
                indent = Strings.padStart("", indentLength, ' ');
                out.printf("%sEND BUCKET: [%s] %s\n", indent, bucketIndex, right);
            }

            @Override
            public boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
                out.printf("%s%s\n", indent, right.name());
                return true;
            }
        });

    }
}
