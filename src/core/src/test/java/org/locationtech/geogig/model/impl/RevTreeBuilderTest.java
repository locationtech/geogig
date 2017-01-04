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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.geometry.jts.JTS;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.impl.LegacyTreeBuilder;
import org.locationtech.geogig.model.impl.RevTreeBuilder;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator.Strategy;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectStore;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.geom.impl.PackedCoordinateSequenceFactory;

public abstract class RevTreeBuilderTest {

    protected ObjectStore objectStore;

    protected abstract RevTreeBuilder createBuiler();

    protected abstract RevTreeBuilder createBuiler(RevTree original);

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
        final int numEntries = 513;//100 * 1000;
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
                Node ref = Node.create(name, newid, ObjectId.NULL, TYPE.FEATURE, null);
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
                    ObjectId oid = RevObjectTestSupport.hashString("updated" + i);
                    Node update = Node.create(entry.name(), oid, entry.getMetadataId(),
                            TYPE.FEATURE, entry.bounds().orNull());
                    oldValues.put(entry.name(), entry.getNode());
                    newValues.put(update.getName(), update);
                }
            }
            assertTrue(newValues.size() > 0);
        }

        RevTreeBuilder builder = createBuiler(tree);
        for (Node change : newValues.values()) {
            builder.put(change);
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
        treeId1 = createAndSaveTree(numEntries, true);
        treeId2 = createAndSaveTree(numEntries, false);

        assertEquals(treeId1, treeId2);
    }

    protected ObjectId createAndSaveTree(final int numEntries,
            final boolean insertInAscendingKeyOrder) throws Exception {

        RevTreeBuilder treeBuilder = createTree(numEntries, insertInAscendingKeyOrder);
        RevTree tree = treeBuilder.build();
        objectStore.put(tree);

        assertEquals(numEntries, tree.size());

        return tree.getId();
    }

    protected RevTreeBuilder createTree(final int numEntries,
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
        String key = "Feature." + i;
        // ObjectId oid = ObjectId.forString(key);
        // ObjectId metadataId = ObjectId.forString("FeatureType");
        // Node ref = new Node(key, oid, metadataId, TYPE.FEATURE);

        Node ref = Node.create(key, FAKE_ID, FAKE_ID, TYPE.FEATURE,
                new Envelope(i, i + 1, i, i + 1));
        tree.put(ref);
    }

    @Test
    public void testResultingTreeBounds() throws Exception {
        checkTreeBounds(10);
        checkTreeBounds(100);
        checkTreeBounds(1000);
        checkTreeBounds(10 * 1000);
        checkTreeBounds(100 * 1000);
    }

    @Test
    public void testBuildWithChildTreesUnsplittedRoot() {
        // create a somewhat uncommon tree in that it contains both trees and features. Despite
        // not commonly used, it's a perfectly valid tree wrt the object model.
        final int rootFeatureCount = 10;
        final int numSubTrees = 4;

        RevTreeBuilder builder = createTree(rootFeatureCount, false);
        long totalSize = rootFeatureCount;

        for (int i = 0; i < numSubTrees; i++) {
            int size = 2 * i;
            totalSize += size;
            RevTree subtree = createTree(size, false).build();
            assertEquals(size, subtree.size());
            String name = "tree-" + i;
            ObjectId metadataId = RevObjectTestSupport.hashString(name);
            Node node = Node.create(name, subtree.getId(), metadataId, TYPE.TREE,
                    SpatialOps.boundsOf(subtree));
            builder.put(node);
        }
        RevTree root = builder.build();
        assertEquals(totalSize, root.size());
        assertEquals(numSubTrees, root.numTrees());
        // This root tree won't be split into buckets since the sum of direct features and trees
        // does not exceed the normalization limit
        assertFalse(root.trees().isEmpty());
        assertFalse(root.features().isEmpty());
        assertTrue(root.buckets().isEmpty());
    }

    @Test
    public void testBuildWithChildTreesSplittedRoot() {
        // create a somewhat uncommon tree in that it contains both trees and features. Despite
        // not commonly used, it's a perfectly valid tree wrt the object model.
        final int rootFeatureCount = 1024;
        final int numSubTrees = 64;

        RevTreeBuilder builder = createTree(rootFeatureCount, false);
        long totalSize = rootFeatureCount;

        for (int i = 0; i < numSubTrees; i++) {
            int size = i * 1024;
            totalSize += size;
            RevTree subtree = createTree(size, false).build();
            assertEquals(size, subtree.size());
            String name = "tree-" + i;
            ObjectId metadataId = RevObjectTestSupport.hashString(name);
            Node node = Node.create(name, subtree.getId(), metadataId, TYPE.TREE,
                    SpatialOps.boundsOf(subtree));
            builder.put(node);
        }
        RevTree root = builder.build();
        assertEquals(totalSize, root.size());
        assertEquals(numSubTrees, root.numTrees());

        // This root tree shall be split into buckets since the sum of direct features and trees
        // does exceed the normalization limit
        assertTrue(root.trees().isEmpty());
        assertTrue(root.features().isEmpty());
        assertFalse(root.buckets().isEmpty());
    }

    private void checkTreeBounds(int size) {
        RevTree tree;
        Envelope bounds;
        tree = tree(size).build();
        assertEquals(size, tree.size());
        bounds = SpatialOps.boundsOf(tree);
        Envelope expected = new Envelope(0, size, 0, size);
        assertEquals(expected, bounds);
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

    private RevTreeBuilder tree(int nfeatures) {
        RevTreeBuilder b = createBuiler();
        for (Node n : nodes(nfeatures)) {
            b.put(n);
        }
        return b;
    }

    protected List<Node> nodes(int size) {
        List<Node> nodes = Lists.newArrayListWithCapacity(size);
        for (int i = 0; i < size; i++) {
            nodes.add(node(i));
        }
        return nodes;
    }

    /**
     * @return a feature node named {@code i}, with
     *         {@code id = ObjectId.forString(String.valueOf(i))}, null metadata id, and
     *         {@code bounds = [i, i+1, i, i+1]}
     */
    protected static Node node(int i) {
        String key = "a" + String.valueOf(i);
        ObjectId oid = RevObjectTestSupport.hashString(key);
        Envelope bounds = new Envelope(i, i + 1, i, i + 1);
        Node node = Node.create(key, oid, ObjectId.NULL, TYPE.FEATURE, bounds);
        return node;
    }

    protected void printTreeBounds(RevTree root) {

        final GeometryFactory gf = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING),
                4326, new PackedCoordinateSequenceFactory());

        final List<Geometry> geoms = new ArrayList<>();

        geoms.add(JTS.toGeometry(SpatialOps.boundsOf(root), gf));

        PreOrderDiffWalk walk = new PreOrderDiffWalk(RevTree.EMPTY, root, objectStore, objectStore);
        walk.walk(new PreOrderDiffWalk.AbstractConsumer() {
            @Override
            public synchronized boolean bucket(NodeRef leftParent, NodeRef rightParent,
                    BucketIndex bucketIndex, @Nullable Bucket left, @Nullable Bucket right) {

                Optional<Envelope> bounds = right.bounds();
                if (bounds.isPresent()) {
                    Envelope env = bounds.get();
                    Polygon geometry = JTS.toGeometry(env, gf);
                    geoms.add(geometry);
                }
                return true;
            }
        });

        Geometry buildGeometry = gf.buildGeometry(geoms);
        System.err.println(buildGeometry);
    }
}
