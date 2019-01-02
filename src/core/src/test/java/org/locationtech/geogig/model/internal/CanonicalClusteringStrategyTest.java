/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.featureNode;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.featureNodes;
import static org.mockito.Mockito.doReturn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.impl.LegacyTreeBuilder;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator.Strategy;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectStore;
import org.mockito.Mockito;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

@SuppressWarnings("deprecation")
public abstract class CanonicalClusteringStrategyTest {

    private ObjectStore store;

    private ClusteringStrategyBuilder canonical;

    private ClusteringStrategy strategy;

    @Before
    public void before() {
        store = new HeapObjectStore();
        store.open();
        canonical = ClusteringStrategyBuilder.canonical(store);
        canonical = Mockito.spy(canonical);
        DAGStorageProvider dagStore = createStorageProvider(store);
        doReturn(dagStore).when(canonical).createDAGStoreageProvider();
    }

    protected abstract DAGStorageProvider createStorageProvider(ObjectStore source);

    @After
    public void after() {
        if (strategy != null) {
            strategy.dispose();
        }
        store.close();
    }

    @Test
    public void buildSimpleDAGFromScratch() {
        strategy = canonical.build();
        for (int i = 0; i < strategy.normalizedSizeLimit(0); i++) {
            Node node = featureNode("f", i);
            strategy.put(node);
        }
        DAG root = strategy.buildRoot();
        assertTrue(buckets(root).isEmpty());
        assertFalse(children(root).isEmpty());
        assertEquals(strategy.normalizedSizeLimit(0), children(root).size());
        assertEquals(0, strategy.depth());
    }

    @Test
    public void buildSplittedDAGFromScratch() {
        strategy = canonical.build();

        final int numNodes = 2 * strategy.normalizedSizeLimit(0);

        for (int i = 0; i < numNodes; i++) {
            Node node = featureNode("f", i, true);
            strategy.put(node);
        }
        DAG root = strategy.buildRoot();
        assertTrue(children(root).isEmpty());
        assertFalse(buckets(root).isEmpty());
        assertEquals(1, strategy.depth());

        List<NodeId> flattenedNodes = flatten(root);
        assertEquals(numNodes, flattenedNodes.size());

    }

    @Test
    public void promoteLeafNodes() {
        final RevTree original = manuallyCreateLeafTree(
                CanonicalNodeNameOrder.normalizedSizeLimit(0));
        store.put(original);

        strategy = canonical.original(original).build();

        final int numNodes = 2 * strategy.normalizedSizeLimit(0);

        Stopwatch sw = Stopwatch.createStarted();
        for (int i = 0; i < numNodes; i++) {
            Node node = featureNode("f", i, true);
            strategy.put(node);
        }
        System.err.printf("Added %,d nodes in %s\n", numNodes, sw.stop());

        DAG root = strategy.buildRoot();
        assertTrue(children(root).isEmpty());
        assertFalse(buckets(root).isEmpty());
        assertEquals(1, strategy.depth());

        List<NodeId> flattenedNodes = flatten(root);
        assertEquals(numNodes, flattenedNodes.size());

    }

    @Test
    public void promoteBucketNodes() {
        final RevTree original = manuallyCreateBucketsTree();
        store.put(original);

        strategy = canonical.original(original).build();

        final int numNodes = 10_000;

        Stopwatch sw = Stopwatch.createStarted();
        for (int i = 0; i < numNodes; i++) {
            Node node = featureNode("f", i, false);
            strategy.put(node);
        }
        System.err.printf("Added %,d nodes in %s\n", numNodes, sw.stop());

        DAG root = strategy.buildRoot();
        assertTrue(children(root).isEmpty());
        assertFalse(buckets(root).isEmpty());
        // assertEquals(1, strategy.depth());

        List<NodeId> flattenedNodes = flatten(root);
        assertEquals(numNodes, flattenedNodes.size());

    }

    @Test
    public void promoteBucketNodes2() {

        final List<Node> nodes = featureNodes(0, 10_000, false);
        final List<Node> origNodes = nodes.subList(0, 5_000);
        final List<Node> addedNodes = nodes.subList(5_000, nodes.size());
        final RevTree original;
        {
            LegacyTreeBuilder legacyBuilder = new LegacyTreeBuilder(store);
            for (Node n : origNodes) {
                legacyBuilder.put(n);
            }
            original = legacyBuilder.build();
        }
        // original = manuallyCreateBucketsTree();
        store.put(original);

        strategy = canonical.original(original).build();

        Stopwatch sw = Stopwatch.createStarted();
        for (Node node : addedNodes) {
            strategy.put(node);
        }
        System.err.printf("Added %,d nodes in %s\n", addedNodes.size(), sw.stop());

        DAG root = strategy.buildRoot();
        assertTrue(children(root).isEmpty());
        assertFalse(buckets(root).isEmpty());
        // assertEquals(1, strategy.depth());

        List<NodeId> flattenedNodes = flatten(root);
        assertEquals(nodes.size(), flattenedNodes.size());

    }

    @Test
    public void promoteBucketNodesWithOverlap() {

        final List<Node> nodes = featureNodes(0, 10_000, false);
        final List<Node> origNodes = nodes.subList(0, 7_000);
        final List<Node> addedNodes = nodes.subList(3_000, nodes.size());
        final RevTree original;
        {
            LegacyTreeBuilder legacyBuilder = new LegacyTreeBuilder(store);
            for (Node n : origNodes) {
                legacyBuilder.put(n);
            }
            original = legacyBuilder.build();
        }
        // original = manuallyCreateBucketsTree();
        store.put(original);

        strategy = canonical.original(original).build();

        Stopwatch sw = Stopwatch.createStarted();
        for (Node node : addedNodes) {
            strategy.put(node);
        }
        System.err.printf("Added %,d nodes in %s\n", addedNodes.size(), sw.stop());

        DAG root = strategy.buildRoot();
        assertTrue(children(root).isEmpty());
        assertFalse(buckets(root).isEmpty());
        // assertEquals(1, strategy.depth());

        List<NodeId> flattenedNodes = flatten(root);
        assertEquals(nodes.size(), flattenedNodes.size());

    }

    @Test
    public void randomEdits() throws Exception {
        final int numEntries = 20 * CanonicalNodeNameOrder.normalizedSizeLimit(0) + 1500;

        strategy = canonical.build();

        List<Node> nodes = featureNodes(0, numEntries, false);
        for (Node n : nodes) {
            strategy.put(n);
        }

        Set<Node> initial = Sets.newTreeSet(
                Lists.transform(flatten(strategy.buildRoot()), nid -> strategy.getNode(nid)));
        assertEquals(nodes.size(), initial.size());

        final Map<Integer, Node> randomEdits = Maps.newHashMap();
        {
            Random randGen = new Random();
            for (int i = 0; i < numEntries / 2; i++) {
                int random;
                while (randomEdits.containsKey(random = randGen.nextInt(numEntries))) {
                    ; // $codepro.audit.disable extraSemicolon
                }
                Node n = featureNode("f", random, true);
                randomEdits.put(random, n);
            }

            for (Node ref : randomEdits.values()) {
                NodeId nodeId = strategy.computeId(ref);
                Node currNode = strategy.getNode(nodeId);
                strategy.put(ref);
                Node newNode = strategy.getNode(nodeId);
                assertFalse(currNode.equals(newNode));
            }
        }

        Set<Node> result = Sets.newTreeSet(
                Lists.transform(flatten(strategy.buildRoot()), nid -> strategy.getNode(nid)));
        assertEquals(nodes.size(), result.size());

        Set<Node> difference = Sets.difference(Sets.newHashSet(result), Sets.newHashSet(initial));
        assertEquals(randomEdits.size(), difference.size());

        assertEquals(new HashSet<>(randomEdits.values()), difference);
    }

    @Test
    public void bucketDAGShrinksOnRemoveBellowThreshold() {

        final List<Node> nodes = featureNodes(0, 513, false);
        final List<Node> removeNodes = nodes.subList(100, 500);
        final RevTree original;
        {
            LegacyTreeBuilder legacyBuilder = new LegacyTreeBuilder(store);
            for (Node n : nodes) {
                legacyBuilder.put(n);
            }
            original = legacyBuilder.build();
        }
        // original = manuallyCreateBucketsTree();
        store.put(original);

        strategy = canonical.original(original).build();

        Stopwatch sw = Stopwatch.createStarted();
        for (Node node : removeNodes) {
            strategy.remove(node);
        }
        System.err.printf("Removed %,d nodes in %s\n", removeNodes.size(), sw.stop());

        DAG root = strategy.buildRoot();
        assertFalse(children(root).isEmpty());
        assertTrue(buckets(root).isEmpty());
        // assertEquals(1, strategy.depth());

        List<NodeId> flattenedNodes = flatten(root);
        assertEquals(nodes.size() - removeNodes.size(), flattenedNodes.size());

        assertTrue(buckets(root).isEmpty());
        assertFalse(children(root).isEmpty());
        assertEquals(nodes.size() - removeNodes.size(), children(root).size());
    }

    @Test
    public void bigBucketDAGShrinksOnRemoveBellowThreshold() {

        final List<Node> nodes = featureNodes(0, 32768, false);
        final List<Node> removeNodes = nodes.subList(100, 32700);
        final RevTree original;
        {
            LegacyTreeBuilder legacyBuilder = new LegacyTreeBuilder(store);
            for (Node n : nodes) {
                legacyBuilder.put(n);
            }
            original = legacyBuilder.build();
        }
        // original = manuallyCreateBucketsTree();
        store.put(original);

        strategy = canonical.original(original).build();

        Stopwatch sw = Stopwatch.createStarted();
        for (Node node : removeNodes) {
            strategy.remove(node);
        }
        System.err.printf("Removed %,d nodes in %s\n", removeNodes.size(), sw.stop());

        DAG root = strategy.buildRoot();
        assertEquals(nodes.size() - removeNodes.size(), root.getTotalChildCount());
        assertFalse(children(root).isEmpty());
        assertTrue(buckets(root).isEmpty());
        // assertEquals(1, strategy.depth());

        List<NodeId> flattenedNodes = flatten(root);
        assertEquals(nodes.size() - removeNodes.size(), flattenedNodes.size());

        assertTrue(buckets(root).isEmpty());
        assertFalse(children(root).isEmpty());
        assertEquals(nodes.size() - removeNodes.size(), children(root).size());
    }

    @Test
    public void nodeReplacedOnEdits() {
        strategy = canonical.build();

        final int numNodes = 2 * strategy.normalizedSizeLimit(0);

        final Set<Node> original;
        final Set<Node> edited;
        {
            original = new HashSet<>();
            edited = new HashSet<>();
            for (int i = 0; i < numNodes; i++) {
                Node orig = featureNode("f", i, false);
                Node edit = featureNode("f", i, true);

                original.add(orig);
                edited.add(edit);
            }
        }

        assertFalse(original.equals(edited));

        for (Node n : original) {
            strategy.put(n);
        }

        Set<Node> originalResult = new HashSet<>();
        Set<Node> edittedResult = new HashSet<>();

        DAG root = strategy.buildRoot();
        originalResult.addAll(toNode(flatten(root)));

        assertEquals(original, originalResult);

        for (Node n : edited) {
            strategy.put(n);
        }

        root = strategy.buildRoot();
        edittedResult.addAll(toNode(flatten(root)));

        assertEquals(edited, edittedResult);
    }

    @Test
    public void nodeReplacedOnEditsWithBaseRevTree() {
        final RevTree origTree = manuallyCreateBucketsTree();
        store.put(origTree);

        final Set<Node> original = new HashSet<>();
        final Set<Node> edited = new HashSet<>();
        {
            Iterator<NodeRef> it = new DepthTreeIterator("", ObjectId.NULL, origTree, store,
                    Strategy.RECURSIVE_FEATURES_ONLY);
            while (it.hasNext()) {
                original.add(it.next().getNode());
            }
            for (Node n : original) {
                ObjectId oid = RevObjectTestSupport.hashString(n.toString());
                Node edit = RevObjectFactory.defaultInstance().createNode(n.getName(), oid,
                        ObjectId.NULL, TYPE.FEATURE, n.bounds().orNull(), null);
                edited.add(edit);

            }
            assertFalse(original.equals(edited));
        }

        strategy = canonical.original(origTree).build();

        for (Node n : edited) {
            strategy.put(n);
        }

        Set<Node> edittedResult = new HashSet<>();

        DAG root = strategy.buildRoot();

        edittedResult.addAll(toNode(flatten(root)));

        assertEquals(edited.size(), edittedResult.size());
        assertEquals(edited, edittedResult);
    }

    private RevTree manuallyCreateBucketsTree() {
        // final int numNodes = 4096;
        // RevTreeBuilder legacyBuilder = new RevTreeBuilder(store);
        //
        // for (int i = 0; i < numNodes; i++) {
        // boolean randomIds = false;
        // Node node = featureNode("f", i, randomIds);
        // legacyBuilder.put(node);
        // }
        //
        // RevTree tree = legacyBuilder.build();
        // return tree;

        final int numNodes = 4096;

        Multimap<Integer, Node> nodesByBucketIndex = ArrayListMultimap.create();
        for (int i = 0; i < numNodes; i++) {
            boolean randomIds = false;
            Node node = featureNode("f", i, randomIds);
            Integer bucket = CanonicalNodeOrder.INSTANCE.bucket(node, 0);
            nodesByBucketIndex.put(bucket, node);
        }

        SortedSet<Bucket> buckets = new TreeSet<>();

        for (Integer bucketIndex : new HashSet<>(nodesByBucketIndex.keySet())) {
            Collection<Node> nodes = nodesByBucketIndex.get(bucketIndex);
            RevTree leaf = createLeafTree(nodes);
            store.put(leaf);
            Bucket bucket = RevObjectFactory.defaultInstance().createBucket(leaf.getId(),
                    bucketIndex.intValue(), SpatialOps.boundsOf(leaf));
            buckets.add(bucket);
        }

        long size = numNodes;
        int childTreeCount = 0;
        ImmutableList<Node> trees = null;
        ImmutableList<Node> features = null;

        RevTree tree = RevTreeBuilder.build(size, childTreeCount, trees, features, buckets);
        return tree;
    }

    private RevTree manuallyCreateLeafTree(final int nodeCount) {
        Preconditions.checkArgument(nodeCount <= CanonicalNodeNameOrder.normalizedSizeLimit(0));

        ImmutableList.Builder<Node> nodes = ImmutableList.builder();
        for (int i = 0; i < nodeCount; i++) {
            nodes.add(featureNode("f", i));
        }
        return createLeafTree(nodes.build());
    }

    private RevTree createLeafTree(Collection<Node> featureNodes) {
        int childTreeCount = 0;
        ImmutableList<Node> trees = null;
        List<Node> sorted = new ArrayList<Node>(featureNodes);
        Collections.sort(sorted, CanonicalNodeOrder.INSTANCE);
        ImmutableList<Node> features = ImmutableList.copyOf(sorted);
        SortedSet<Bucket> buckets = null;
        int size = features.size();
        RevTree tree = RevTreeBuilder.build(size, childTreeCount, trees, features, buckets);
        return tree;
    }

    private List<Node> toNode(List<NodeId> nodeIds) {

        SortedMap<NodeId, Node> nodes = strategy.getNodes(new HashSet<>(nodeIds));
        assertEquals(nodeIds.size(), nodes.size());
        return new ArrayList<>(nodes.values());
    }

    private List<NodeId> flatten(DAG root) {
        List<NodeId> nodes = new ArrayList<NodeId>();

        Set<NodeId> children = children(root);
        Set<TreeId> buckets = buckets(root);
        if (children != null) {
            nodes.addAll(children);
        }

        if (buckets != null) {
            for (TreeId bucketTreeId : buckets) {
                DAG bucketDAG = strategy.getOrCreateDAG(bucketTreeId);
                nodes.addAll(flatten(bucketDAG));
            }
        }
        return nodes;
    }

    Set<NodeId> children(DAG root) {
        Set<NodeId> ids = new HashSet<>();
        root.forEachChild((id) -> ids.add(id));
        return ids;
    }

    Set<TreeId> buckets(DAG root) {
        Set<TreeId> ids = new HashSet<>();
        root.forEachBucket((id) -> ids.add(id));
        return ids;
    }

}
