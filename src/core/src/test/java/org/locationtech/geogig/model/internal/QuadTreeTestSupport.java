
/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.geometry.jts.JTS;
import org.junit.Assert;
import org.junit.rules.ExternalResource;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectStore;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

public class QuadTreeTestSupport extends ExternalResource {

    public static Envelope wgs84Bounds() {
        return new Envelope(-180, 180, -90, 90);
    }

    public static Envelope epsg3857Bounds() {
        return new Envelope(-2.0037508342789244E7, 2.0037508342789244E7, -2.00489661040146E7,
                2.0048966104014594E7);
    }

    private ObjectStore store;

    private Envelope maxBoundsFloat64;

    private int maxDepth = -1;

    @Override
    public void before() {
        store = new HeapObjectStore();
        store.open();

        maxBoundsFloat64 = QuadTreeTestSupport.wgs84Bounds();
    }

    @Override
    public void after() {
        store.close();
    }

    public ObjectStore store() {
        return store;
    }

    public void setMaxBounds(Envelope env) {
        this.maxBoundsFloat64 = new Envelope(env);
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public QuadTreeClusteringStrategy newStrategy() {
        return newStrategy(RevTree.EMPTY);
    }

    public QuadTreeClusteringStrategy newStrategy(RevTree original) {
        QuadTreeClusteringStrategy quadStrategy = ClusteringStrategyBuilder//
                .quadTree(store)//
                .original(original)//
                .maxBounds(maxBoundsFloat64)//
                .maxDepth(maxDepth)//
                .build();
        Assert.assertEquals(getMaxBounds(), quadStrategy.getMaxBounds());
        return quadStrategy;
    }

    public QuadTreeClusteringStrategy clone(QuadTreeClusteringStrategy strategy) {
        QuadTreeClusteringStrategy clone = newStrategy();
        clone.root.init(strategy.root);
        List<DAG> dagTrees = strategy.getDagTrees(new HashSet<>(strategy.root.bucketList()));
        for (DAG d : dagTrees) {
            clone(strategy, clone, d);
        }
        return clone;
    }

    private void clone(QuadTreeClusteringStrategy source, QuadTreeClusteringStrategy target,
            DAG dag) {

        DAG clone = dag.clone();
        target.dagCache.add(clone);

        Set<NodeId> children = new HashSet<>(dag.childrenList());
        Map<NodeId, Node> nodes = source.storageProvider.getNodes(children);
        Map<NodeId, DAGNode> dagnodes = Maps.transformValues(nodes, (n) -> DAGNode.of(n));
        target.storageProvider.saveNodes(dagnodes);

        Set<TreeId> buckets = new HashSet<>(dag.bucketList());
        List<DAG> dagTrees = source.getDagTrees(buckets);
        for (DAG d : dagTrees) {
            clone(source, target, d);
        }
    }

    public RevTreeBuilder newTreeBuilder() {
        return newTreeBuilder(RevTree.EMPTY);
    }

    public RevTreeBuilder newTreeBuilder(RevTree original) {
        return RevTreeBuilder.quadBuilder(store, store, original, maxBoundsFloat64);
    }

    public Node createNode(String name, @Nullable Envelope bounds) {
        ObjectId id = RevObjectTestSupport.hashString(name);
        Node n = RevObjectFactory.defaultInstance().createNode(name, id, ObjectId.NULL,
                RevObject.TYPE.FEATURE, bounds, null);

        if (bounds != null && !bounds.isNull()) {
            Envelope float32Bounds = n.bounds().get();
            Assert.assertTrue(float32Bounds.contains(bounds));
        }

        return n;
    }

    public List<Node> createNodes(int count, List<Quadrant> quads) {
        return createNodes(count, "", quads);
    }

    public List<Node> createNodes(int count, String namePrefix, List<Quadrant> quads) {
        Envelope quadBounds = quadBounds(quads.toArray(new Quadrant[0]));
        return createNodes(count, namePrefix, quadBounds);
    }

    public List<Node> createNodes(int count, Envelope sharedBounds) {
        return createNodes(count, "", sharedBounds);
    }

    public List<Node> createNodes(int count, String namePrefix, Envelope sharedBounds) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = namePrefix + i;
            Node node = createNode(name, sharedBounds);
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * given a list of quandrants, create a node with a bounding box for a point that's at the
     * center of the last quadrant's bounds
     */
    public Node createPointNode(String name, Quadrant... quadrants) {

        Envelope nodeBounds = getQuadCenter(quadrants);

        Node node = createNode(name, nodeBounds);

        return node;
    }

    public Envelope getQuadCenter(Quadrant... quadrants) {
        Envelope quadBounds = getMaxBounds();
        if (quadrants != null) {
            for (Quadrant quad : quadrants) {
                quadBounds = quad.slice(quadBounds);
            }
        }

        Coordinate center = quadBounds.centre();
        Envelope nodeBounds = RevObjects.makePrecise(new Envelope(center));

        if (!quadBounds.contains(nodeBounds)) {
            GeometryFactory gf = new GeometryFactory();
            Polygon qgeom = JTS.toGeometry(quadBounds, gf);
            ArrayList<Geometry> pointGeoms = Lists.newArrayList(gf.createPoint(center),
                    JTS.toGeometry(nodeBounds, gf));
            Geometry point = gf.buildGeometry(pointGeoms);
            String msg = qgeom + " does not contain " + point;
            Assert.fail(msg);
        }

        return nodeBounds;
    }

    private Envelope getMaxBounds() {
        return RevObjects.makePrecise(maxBoundsFloat64);
    }

    public Node createNode(String name, List<Quadrant> quadrants) {
        return createNode(name, quadrants.toArray(new Quadrant[quadrants.size()]));
    }

    public Node createPointNode(String name, List<Quadrant> quadrants) {
        return createPointNode(name, quadrants.toArray(new Quadrant[quadrants.size()]));
    }

    /**
     * given a list of quandrants, create a node with a bounding box that JUST fits inside
     */
    public Node createNode(String name, Quadrant... quadrants) {

        Envelope nodeBounds = createBounds(quadrants);

        Node node = createNode(name, nodeBounds);

        if (!nodeBounds.contains(node.bounds().get())) {
            node = createPointNode(name, quadrants);
        }

        return node;
    }

    /**
     * @param quadKey e.g. {@code "[0, 1, 2, 3]"} for quads {@code [SW, NW, NE, SE]}
     * @return
     */
    public Envelope createBounds(String quadKey) {
        TreeId id = TreeId.fromString(quadKey);
        Quadrant[] quads = new Quadrant[id.depthLength()];
        for (int i = 0; i < id.depthLength(); i++) {
            quads[i] = Quadrant.VALUES[id.bucketIndex(i)];
        }
        return createBounds(quads);
    }

    /**
     * @return the exact bounds of the leaf quad addressed by the given list of quadrants
     */
    public Envelope quadBounds(Quadrant... quadrants) {
        Envelope env = getMaxBounds();
        for (Quadrant q : quadrants) {
            env = q.slice(env);
        }
        return env;
    }

    /**
     * Creates an envelope that "just fits" inside the quadarant bounds
     */
    public Envelope createBounds(Quadrant... quadrants) {
        Envelope quadBounds = getMaxBounds();
        if (quadrants != null) {
            for (Quadrant quad : quadrants) {
                quadBounds = quad.slice(quadBounds);
            }
        }

        Envelope nodeBounds;
        double deltaX = quadBounds.getWidth() / 100.0;
        double deltaY = quadBounds.getHeight() / 100.0;

        double x1 = quadBounds.getMinX() + deltaX;
        double x2 = quadBounds.getMaxX() - deltaX;
        double y1 = quadBounds.getMinY() + deltaY;
        double y2 = quadBounds.getMaxY() - deltaY;

        nodeBounds = RevObjects.makePrecise(new Envelope(x1, x2, y1, y2));

        if (!quadBounds.contains(nodeBounds)) {
            nodeBounds = new Envelope(quadBounds);
            Assert.assertTrue(quadBounds.contains(nodeBounds));
        }
        return nodeBounds;
    }

    public Node putNode(QuadTreeClusteringStrategy quad, Quadrant... location) {
        Preconditions.checkNotNull(location);
        long fnumb = quad.root == null ? 0 : quad.root.getTotalChildCount();
        String quadInfo = Arrays.toString(location);

        Node n = createNode("node # " + fnumb + ", at " + quadInfo, location);

        quad.put(n);
        return n;
    }

    public List<Node> putNodes(int numNodes, QuadTreeClusteringStrategy quad,
            List<Quadrant> location) {
        Preconditions.checkNotNull(location);
        return putNodes(numNodes, quad, location.toArray(new Quadrant[location.size()]));
    }

    public List<Node> putNodes(int numNodes, QuadTreeClusteringStrategy quad,
            Quadrant... location) {
        Preconditions.checkNotNull(location);
        Preconditions.checkArgument(location.length > 0);
        List<Node> result = new ArrayList<>(numNodes);
        for (int t = 0; t < numNodes; t++) {
            result.add(putNode(quad, location));
        }
        return result;
    }

    public DAG findDAG(QuadTreeClusteringStrategy quadStrategy, Quadrant... key) {
        return findDAG(quadStrategy, Arrays.asList(key));
    }

    public DAG findDAG(QuadTreeClusteringStrategy quadStrategy, List<Quadrant> key) {
        List<Integer> bucketNumbers = Lists.transform(key,
                (q) -> Integer.valueOf(q.getBucketNumber()));
        String skey = bucketNumbers.toString();
        return findDAG(quadStrategy, skey);
    }

    public @Nullable DAG findDAG(QuadTreeClusteringStrategy quadStrategy, String key) {
        TreeId id = TreeId.fromString(key);
        return findDAG(quadStrategy, id);
    }

    /**
     * Traverses the strategy's root DAG until finding the child {@code id} and returns it.
     */
    public @Nullable DAG findDAG(QuadTreeClusteringStrategy quadStrategy, TreeId id) {
        List<TreeId> path = id.deglose();
        DAG dag = quadStrategy.root;
        for (TreeId child : path) {
            if (dag.containsBucket(child)) {
                dag = getDAG(quadStrategy, child);
                if (dag == null) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return dag;
    }

    /**
     * Returns the DAG from {@code quadStrategy} without checking the root DAG leads to it
     */
    public @Nullable DAG getDAG(QuadTreeClusteringStrategy quadStrategy, TreeId id) {
        List<DAG> dags;
        try {
            dags = quadStrategy.getDagTrees(Collections.singleton(id));
        } catch (NoSuchElementException e) {
            return null;
        }
        Preconditions.checkState(dags.size() == 1);
        DAG dag = dags.get(0);
        return dag;
    }

    public void putNodes(QuadTreeClusteringStrategy strategy, List<Node> nodes) {
        for (Node node : nodes) {
            int retCode = strategy.put(node);
            if (retCode != 1) {
                NodeId nodeid = strategy.computeId(node);
                Assert.assertEquals("Node " + nodeid + " was not inserted", 1, strategy.put(node));
            }
        }
    }

    public void removeNodes(QuadTreeClusteringStrategy strategy, List<Node> nodes) {
        for (Node node : nodes) {
            if (!strategy.remove(node)) {
                NodeId nodeid = strategy.computeId(node);
                Assert.fail("Node " + nodeid + " was not removed");
            }
        }
    }

    public void updateNodes(QuadTreeClusteringStrategy strategy, List<Node> oldNodes,
            List<Node> newNodes) {
        Preconditions.checkArgument(oldNodes.size() == newNodes.size());

        for (int i = 0; i < oldNodes.size(); i++) {
            Node oldNode = oldNodes.get(i);
            Node newNode = newNodes.get(i);
            Assert.assertEquals("Nodes shall be provided in equal order", oldNode.getName(),
                    newNode.getName());

            int retCode = strategy.update(oldNode, newNode);
            int expected = newNode.getObjectId().isNull() ? -1 : 1;

            if (retCode != expected) {
                NodeId oldnodeid = strategy.computeId(oldNode);
                NodeId newnodeid = strategy.computeId(newNode);
                Assert.assertEquals(
                        String.format("Node update failed %s -> %s", oldnodeid, newnodeid),
                        expected, retCode);
            }
        }

    }

    public Set<NodeId> getAllNodes(QuadTreeClusteringStrategy strategy) {
        return getAllNodes(strategy, strategy.root);
    }

    /**
     * @throws IllegalStateException if a node is repeated
     */
    public Set<NodeId> getAllNodes(QuadTreeClusteringStrategy strategy, DAG root)
            throws IllegalStateException {
        Set<NodeId> nodes = new HashSet<>();
        if (root.numChildren() > 0) {
            List<NodeId> childrenList = root.childrenList();
            for (NodeId id : childrenList) {
                if (!nodes.add(id)) {
                    throw new IllegalStateException("Node " + id + " was found twice in the DAG");
                }
            }
        }
        if (root.numBuckets() > 0) {
            for (TreeId bucketId : root.bucketList()) {
                DAG child = getDAG(strategy, bucketId);
                Preconditions.checkNotNull(child, "DAG %s not found", bucketId);
                Set<NodeId> childnodes = getAllNodes(strategy, child);
                for (NodeId id : childnodes) {
                    if (!nodes.add(id)) {
                        throw new IllegalStateException(
                                "Node " + id + " was found twice in the DAG");
                    }
                }
            }
        }
        return nodes;
    }

    public Set<NodeId> toNodeIds(QuadTreeClusteringStrategy strategy, Iterable<Node> nodes) {
        Set<NodeId> ids = new HashSet<>();
        for (Node n : nodes) {
            NodeId id = strategy.computeId(n);
            Assert.assertTrue("got duplicated node", ids.add(id));
        }
        return ids;
    }

    public void assertDag(QuadTreeClusteringStrategy strategy, String childdagKey,
            Iterable<Node> expectedNodes) {

        strategy = clone(strategy);

        final DAG dag = findDAG(strategy, childdagKey);
        assertNotNull("Expected dag at " + childdagKey, dag);

        Set<NodeId> expected = toNodeIds(strategy, expectedNodes);
        Set<NodeId> dagNodes = getAllNodes(strategy, dag);

        SetView<NodeId> notAdded = Sets.difference(expected, dagNodes);
        SetView<NodeId> notRemoved = Sets.difference(dagNodes, expected);
        if (!(notAdded.isEmpty() && notRemoved.isEmpty())) {
            String msg = "Nodes not added: " + notAdded + "\nNodes not removed: " + notRemoved;
            Assert.fail(msg);
        }
        final int expectedSize = Iterables.size(expectedNodes);
        assertEquals(expectedSize, dag.getTotalChildCount());
    }

}
