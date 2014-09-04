/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import static org.locationtech.geogig.api.plumbing.LsTreeOp.Strategy.FEATURES_ONLY;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.LsTreeOp;
import org.locationtech.geogig.api.plumbing.diff.DepthTreeIterator;
import org.locationtech.geogig.api.plumbing.diff.DepthTreeIterator.Strategy;
import org.locationtech.geogig.repository.SpatialOps;
import org.locationtech.geogig.storage.NodeStorageOrder;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Envelope;

public class RevTreeBuilderTest extends RepositoryTestCase {

    private ObjectDatabase odb;

    @Override
    protected void setUpInternal() throws Exception {
        odb = repo.objectDatabase();
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
        RevTreeBuilder builder = createTree(numEntries, true);
        RevTree tree = builder.build();
        final long declaredSize = tree.size();

        Iterator<NodeRef> it = new DepthTreeIterator("", ObjectId.NULL, tree, odb,
                Strategy.RECURSIVE_FEATURES_ONLY);
        long itSize = 0;
        while (it.hasNext()) {
            it.next();
            itSize++;
        }
        assertEquals(numEntries, itSize);
        assertEquals(numEntries, declaredSize);
    }

    @Test
    public void testPutIterate() throws Exception {
        final int numEntries = 1000 * 100;
        ObjectId treeId;

        Stopwatch sw;
        sw = Stopwatch.createStarted();
        treeId = createAndSaveTree(numEntries, true);
        sw.stop();
        System.err.println("Stored " + numEntries + " tree entries in " + sw + " ("
                + Math.round(numEntries / (sw.elapsed(TimeUnit.MILLISECONDS) / 1000D)) + "/s)");

        sw = Stopwatch.createStarted();
        treeId = createAndSaveTree(numEntries, true);
        sw.stop();
        System.err.println("Stored " + numEntries + " tree entries in " + sw + " ("
                + Math.round(numEntries / (sw.elapsed(TimeUnit.MILLISECONDS) / 1000D)) + "/s)");

        sw.reset().start();
        final RevTree tree = odb.getTree(treeId);
        sw.stop();
        System.err.println("Retrieved tree in " + sw);

        System.err.println("traversing with DepthTreeIterator...");
        sw.reset().start();
        int counted = 0;
        for (DepthTreeIterator it = new DepthTreeIterator("", ObjectId.NULL, tree, odb,
                Strategy.CHILDREN); it.hasNext(); counted++) {
            NodeRef ref = it.next();
            if ((counted + 1) % (numEntries / 10) == 0) {
                System.err.print("#" + (counted + 1));
            } else if ((counted + 1) % (numEntries / 100) == 0) {
                System.err.print('.');
            }
        }
        sw.stop();
        System.err.println("\nTraversed " + counted + " in " + sw + " ("
                + Math.round(counted / (sw.elapsed(TimeUnit.MILLISECONDS) / 1000D)) + "/s)\n");

        System.err.println("traversing with DepthTreeIterator...");
        sw.reset().start();
        counted = 0;
        for (DepthTreeIterator it = new DepthTreeIterator("", ObjectId.NULL, tree, odb,
                Strategy.CHILDREN); it.hasNext(); counted++) {
            NodeRef ref = it.next();
            if ((counted + 1) % (numEntries / 10) == 0) {
                System.err.print("#" + (counted + 1));
            } else if ((counted + 1) % (numEntries / 100) == 0) {
                System.err.print('.');
            }
        }
        sw.stop();
        System.err.println("\nTraversed " + counted + " in " + sw + " ("
                + Math.round(counted / (sw.elapsed(TimeUnit.MILLISECONDS) / 1000D)) + "/s)\n");
        assertEquals(numEntries, counted);
    }

    @Test
    public void testPutRandomGet() throws Exception {
        final int numEntries = 2 * RevTree.NORMALIZED_SIZE_LIMIT + 1500;
        final ObjectId treeId;

        Stopwatch sw;
        sw = Stopwatch.createStarted();
        treeId = createAndSaveTree(numEntries, true);
        sw.stop();
        System.err.println("Stored " + numEntries + " tree entries in " + sw + " ("
                + Math.round(numEntries / (sw.elapsed(TimeUnit.MILLISECONDS) / 1000D)) + "/s)");

        sw.reset().start();
        final RevTree tree = odb.getTree(treeId);
        sw.stop();
        System.err.println("Retrieved tree in " + sw);

        {
            Map<Integer, Node> randomEdits = Maps.newHashMap();
            Random randGen = new Random();
            for (int i = 0; i < tree.size() / 2; i++) {
                int random;
                while (randomEdits.containsKey(random = randGen.nextInt(numEntries))) {
                    ; // $codepro.audit.disable extraSemicolon
                }
                String name = "Feature." + random;
                ObjectId newid = ObjectId.forString(name + "changed");
                Node ref = Node.create(name, newid, ObjectId.NULL, TYPE.FEATURE, null);
                randomEdits.put(random, ref);
            }
            RevTreeBuilder mutable = tree.builder(odb);
            sw.reset().start();
            for (Node ref : randomEdits.values()) {
                mutable.put(ref);
            }
            mutable.build();
            sw.stop();
            System.err.println(randomEdits.size() + " random modifications in " + sw);
        }

        // CharSequence treeStr =
        // repo.command(CatObject.class).setObject(Suppliers.ofInstance(tree))
        // .call();
        // System.out.println(treeStr);

        final FindTreeChild childFinder = repo.command(FindTreeChild.class).setParent(tree);

        sw.reset().start();
        System.err.println("Reading " + numEntries + " entries....");
        for (int i = 0; i < numEntries; i++) {
            if ((i + 1) % (numEntries / 10) == 0) {
                System.err.print("#" + (i + 1));
            } else if ((i + 1) % (numEntries / 100) == 0) {
                System.err.print('.');
            }
            String key = "Feature." + i;
            // ObjectId oid = ObjectId.forString(key);
            Optional<NodeRef> ref = childFinder.setChildPath(key).call();
            assertTrue(key, ref.isPresent());
            // assertEquals(key, ref.get().getPath());
            // assertEquals(key, oid, ref.get().getObjectId());
        }
        sw.stop();
        System.err.println("\nGot " + numEntries + " in " + sw.elapsed(TimeUnit.MILLISECONDS)
                + "ms (" + Math.round(numEntries / (sw.elapsed(TimeUnit.MILLISECONDS) / 1000D))
                + "/s)\n");

    }

    @Test
    public void testRemove() throws Exception {
        final int numEntries = 1000;
        ObjectId treeId = createAndSaveTree(numEntries, true);
        final RevTree tree = odb.getTree(treeId);

        // collect some keys to remove
        final Set<String> removedKeys = new HashSet<String>();
        {
            int i = 0;
            DepthTreeIterator it = new DepthTreeIterator("", ObjectId.NULL, tree, odb,
                    Strategy.CHILDREN);
            for (; it.hasNext(); i++) {
                NodeRef entry = it.next();
                if (i % 10 == 0) {
                    removedKeys.add(entry.path());
                }
            }
            assertEquals(100, removedKeys.size());
        }

        final RevTreeBuilder builder = tree.builder(odb);
        for (String key : removedKeys) {
            assertTrue(builder.get(key).isPresent());
            builder.remove(key);
            assertFalse(builder.get(key).isPresent());
        }

        final RevTree tree2 = builder.build();

        for (String key : removedKeys) {
            assertFalse(repo.getTreeChild(tree2, key).isPresent());
        }
    }

    @Test
    public void testRemoveSplittedTree() throws Exception {
        final int numEntries = (int) (1.5 * RevTree.NORMALIZED_SIZE_LIMIT);
        final ObjectId treeId = createAndSaveTree(numEntries, true);
        final RevTree tree = odb.getTree(treeId);

        // collect some keys to remove
        final Set<String> removedKeys = new HashSet<String>();
        {
            int i = 0;
            DepthTreeIterator it = new DepthTreeIterator("", ObjectId.NULL, tree, odb,
                    Strategy.CHILDREN);
            for (; it.hasNext(); i++) {
                NodeRef entry = it.next();
                if (i % 10 == 0) {
                    removedKeys.add(entry.path());
                }
            }
            assertTrue(removedKeys.size() > 0);
        }

        RevTreeBuilder builder = tree.builder(odb);
        for (String key : removedKeys) {
            assertTrue(key, builder.get(key).isPresent());
            builder.remove(key);
            assertFalse(key, builder.get(key).isPresent());
        }

        for (String key : removedKeys) {
            assertFalse(builder.get(key).isPresent());
        }

        final RevTree tree2 = builder.build();

        for (String key : removedKeys) {
            assertFalse(key, repo.getTreeChild(tree2, key).isPresent());
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
        testEquality(100 + RevTree.NORMALIZED_SIZE_LIMIT);
    }

    private void testEquality(final int numEntries) throws Exception {
        final ObjectId treeId1;
        final ObjectId treeId2;
        treeId1 = createAndSaveTree(numEntries, true);
        treeId2 = createAndSaveTree(numEntries, false);

        assertEquals(treeId1, treeId2);
    }

    private ObjectId createAndSaveTree(final int numEntries, final boolean insertInAscendingKeyOrder)
            throws Exception {

        RevTreeBuilder treeBuilder = createTree(numEntries, insertInAscendingKeyOrder);
        RevTree tree = treeBuilder.build();
        odb.put(tree);
        return tree.getId();
    }

    private RevTreeBuilder createTree(final int numEntries, final boolean insertInAscendingKeyOrder) {
        RevTreeBuilder tree = new RevTreeBuilder(odb);

        final int increment = insertInAscendingKeyOrder ? 1 : -1;
        final int from = insertInAscendingKeyOrder ? 0 : numEntries - 1;
        final int breakAt = insertInAscendingKeyOrder ? numEntries : -1;

        int c = 0;
        for (int i = from; i != breakAt; i += increment, c++) {
            addNode(tree, i);
            if (numEntries > 100) {
                if ((c + 1) % (numEntries / 10) == 0) {
                    System.err.print("#" + (c + 1));
                } else if ((c + 1) % (numEntries / 100) == 0) {
                    System.err.print('.');
                }
            }
        }
        System.err.print('\n');
        return tree;
    }

    private static final ObjectId FAKE_ID = ObjectId.forString("fake");

    private void addNode(RevTreeBuilder tree, int i) {
        String key = "Feature." + i;
        // ObjectId oid = ObjectId.forString(key);
        // ObjectId metadataId = ObjectId.forString("FeatureType");
        // Node ref = new Node(key, oid, metadataId, TYPE.FEATURE);

        Node ref = Node.create(key, FAKE_ID, FAKE_ID, TYPE.FEATURE, boundsOf(points1));
        tree.put(ref);
    }

    @Test
    public void testNodeOrderPassSplitThreshold() {
        final int splitThreshold = RevTree.NORMALIZED_SIZE_LIMIT;
        List<Node> expectedOrder = nodes(splitThreshold + 1);
        Collections.sort(expectedOrder, new NodeStorageOrder());

        final List<Node> flat = expectedOrder.subList(0, splitThreshold);
        RevTreeBuilder flatTreeBuilder = new RevTreeBuilder(odb);
        RevTreeBuilder bucketTreeBuilder = new RevTreeBuilder(odb);

        for (Node n : flat) {
            flatTreeBuilder.put(n);
            bucketTreeBuilder.put(n);
        }
        bucketTreeBuilder.put(expectedOrder.get(expectedOrder.size() - 1));
        RevTree flatTree = flatTreeBuilder.build();
        RevTree bucketTree = bucketTreeBuilder.build();
        assertFalse(flatTree.buckets().isPresent());
        assertTrue(bucketTree.buckets().isPresent());
        odb.put(flatTree);
        odb.put(bucketTree);

        List<Node> flatNodes = lstree(flatTree);
        assertEquals(flat, flatNodes);

        List<Node> splitNodes = lstree(bucketTree);
        assertEquals(expectedOrder, splitNodes);
    }

    @Test
    public void testResultingTreeBounds() throws Exception {
        checkTreeBounds(10);
        checkTreeBounds(100);
        checkTreeBounds(1000);
        checkTreeBounds(10 * 1000);
        checkTreeBounds(100 * 1000);
    }

    private void checkTreeBounds(int size) {
        RevTree tree;
        Envelope bounds;
        tree = tree(size).build();
        bounds = SpatialOps.boundsOf(tree);
        Envelope expected = new Envelope(0, size, 0, size);
        assertEquals(expected, bounds);
    }

    private List<Node> lstree(RevTree tree) {
        Iterator<NodeRef> refs = geogig.command(LsTreeOp.class)
                .setReference(tree.getId().toString()).setStrategy(FEATURES_ONLY).call();
        List<Node> nodes = new ArrayList<Node>();
        while (refs.hasNext()) {
            nodes.add(refs.next().getNode());
        }
        return nodes;
    }

    private RevTreeBuilder tree(int nfeatures) {
        RevTreeBuilder b = new RevTreeBuilder(odb);
        for (Node n : nodes(nfeatures)) {
            b.put(n);
        }
        return b;
    }

    private List<Node> nodes(int size) {
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
    private static Node node(int i) {
        String key = String.valueOf(i);
        ObjectId oid = ObjectId.forString(key);
        Envelope bounds = new Envelope(i, i + 1, i, i + 1);
        Node node = Node.create(key, oid, ObjectId.NULL, TYPE.FEATURE, bounds);
        return node;
    }
}
