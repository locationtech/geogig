/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.GraphDatabase.Direction;
import org.locationtech.geogig.storage.GraphDatabase.GraphEdge;
import org.locationtech.geogig.storage.GraphDatabase.GraphNode;
import org.locationtech.geogig.test.TestPlatform;

import com.google.common.collect.ImmutableList;

/**
 * Abstract test suite for {@link GraphDatabase} implementations.
 * <p>
 * Create a concrete subclass of this test suite and implement {@link #createInjector()} so that
 * {@code GraphDtabase.class} is bound to your implementation instance as a singleton.
 */
public abstract class GraphDatabaseTest {

    protected GraphDatabase database;

    protected TestPlatform platform;

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        File root = tmpFolder.getRoot();
        tmpFolder.newFolder(".geogig");
        platform = new TestPlatform(root);
        platform.setUserHome(tmpFolder.newFolder("fake_home"));
        database = createDatabase(platform);
        database.open();
    }

    @After
    public void tearDown() throws Exception {
        if (database != null) {
            database.close();
        }
    }

    protected abstract GraphDatabase createDatabase(Platform platform) throws Exception;

    @Test
    public void testNodes() throws IOException {
        ObjectId rootId = RevObjectTestSupport.hashString("root");
        ImmutableList<ObjectId> parents = ImmutableList.of();
        database.put(rootId, parents);
        ObjectId commit1 = RevObjectTestSupport.hashString("c1");
        parents = ImmutableList.of(rootId);
        database.put(commit1, parents);
        ObjectId commit2 = RevObjectTestSupport.hashString("c2");
        parents = ImmutableList.of(commit1);
        database.put(commit2, parents);

        ImmutableList<ObjectId> children = database.getChildren(commit2);
        parents = database.getParents(commit2);
        assertTrue(database.exists(commit2));
        assertEquals("Size of " + children, 0, children.size());
        assertEquals(1, parents.size());
        assertEquals(commit1, parents.get(0));
        children = database.getChildren(commit1);
        parents = database.getParents(commit1);
        assertTrue(database.exists(commit1));
        assertEquals(1, children.size());
        assertEquals(commit2, children.get(0));
        assertEquals(1, parents.size());
        assertEquals(rootId, parents.get(0));
        children = database.getChildren(rootId);
        parents = database.getParents(rootId);
        assertTrue(database.exists(rootId));
        assertEquals(1, children.size());
        assertEquals(commit1, children.get(0));
        assertEquals(0, parents.size());
    }

    @Test
    public void testMapNode() throws IOException {
        ObjectId commitId = RevObjectTestSupport.hashString("commitId");
        ObjectId mappedId = RevObjectTestSupport.hashString("mapped");
        database.put(commitId, new ImmutableList.Builder<ObjectId>().build());
        database.map(mappedId, commitId);
        ObjectId mapping = database.getMapping(mappedId);
        assertEquals(commitId + " : " + mappedId + " : " + mapping, commitId, mapping);

        // update mapping
        ObjectId commitId2 = RevObjectTestSupport.hashString("commitId2");
        database.map(mappedId, commitId2);
        mapping = database.getMapping(mappedId);
        assertEquals(commitId2 + " : " + mappedId + " : " + mapping, commitId2, mapping);
    }

    @Test
    public void testDepth() throws IOException {
        // Create the following revision graph
        // x o - root commit
        // | |\
        // | | o - commit1
        // | | |
        // | | o - commit2
        // | | |\
        // | | | o - commit3
        // | | | |\
        // | | | | o - commit4
        // | | | | |
        // | | | o | - commit5
        // | | | |/
        // | | | o - commit6
        // | | |
        // | o | - commit7
        // | | |
        // | | o - commit8
        // | |/
        // | o - commit9
        // |
        // o - commit10
        // |
        // o - commit11
        ObjectId rootId = RevObjectTestSupport.hashString("root commit");
        ImmutableList<ObjectId> parents = ImmutableList.of();
        database.put(rootId, parents);
        ObjectId commit1 = RevObjectTestSupport.hashString("commit1");
        parents = ImmutableList.of(rootId);
        database.put(commit1, parents);
        ObjectId commit2 = RevObjectTestSupport.hashString("commit2");
        parents = ImmutableList.of(commit1);
        database.put(commit2, parents);
        ObjectId commit3 = RevObjectTestSupport.hashString("commit3");
        parents = ImmutableList.of(commit2);
        database.put(commit3, parents);
        ObjectId commit4 = RevObjectTestSupport.hashString("commit4");
        parents = ImmutableList.of(commit3);
        database.put(commit4, parents);
        ObjectId commit5 = RevObjectTestSupport.hashString("commit5");
        parents = ImmutableList.of(commit3);
        database.put(commit5, parents);
        ObjectId commit6 = RevObjectTestSupport.hashString("commit6");
        parents = ImmutableList.of(commit5, commit4);
        database.put(commit6, parents);
        ObjectId commit7 = RevObjectTestSupport.hashString("commit7");
        parents = ImmutableList.of(rootId);
        database.put(commit7, parents);
        ObjectId commit8 = RevObjectTestSupport.hashString("commit8");
        parents = ImmutableList.of(commit2);
        database.put(commit8, parents);
        ObjectId commit9 = RevObjectTestSupport.hashString("commit9");
        parents = ImmutableList.of(commit7, commit8);
        database.put(commit9, parents);
        ObjectId commit10 = RevObjectTestSupport.hashString("commit10");
        parents = ImmutableList.of();
        database.put(commit10, parents);
        ObjectId commit11 = RevObjectTestSupport.hashString("commit11");
        parents = ImmutableList.of(commit10);
        database.put(commit11, parents);

        assertEquals(0, database.getDepth(rootId));
        assertEquals(2, database.getDepth(commit9));
        assertEquals(3, database.getDepth(commit8));
        assertEquals(5, database.getDepth(commit6));
        assertEquals(4, database.getDepth(commit4));
        assertEquals(1, database.getDepth(commit11));
    }

    @Test
    public void testProperties() throws IOException {
        ObjectId rootId = RevObjectTestSupport.hashString("root");
        ImmutableList<ObjectId> parents = ImmutableList.of();
        database.put(rootId, parents);

        database.setProperty(rootId, GraphDatabase.SPARSE_FLAG, "true");
        assertTrue(database.getNode(rootId).isSparse());
    }

    @Test
    public void testEdges() throws IOException {
        ObjectId rootId = RevObjectTestSupport.hashString("root");
        database.put(rootId, ImmutableList.of());
        ObjectId commit1 = RevObjectTestSupport.hashString("c1");
        database.put(commit1, ImmutableList.of(rootId));
        ObjectId commit2 = RevObjectTestSupport.hashString("c2");
        database.put(commit2, ImmutableList.of(commit1, rootId));

        GraphNode node;
        List<GraphEdge> edges;

        node = database.getNode(commit2);
        assertNotNull(node);

        edges = ImmutableList.copyOf(node.getEdges(Direction.IN));
        assertTrue(edges.isEmpty());

        edges = ImmutableList.copyOf(node.getEdges(Direction.OUT));
        assertEquals(2, edges.size());
        assertEquals(commit1, edges.get(0).getToNode().getIdentifier());
        assertEquals(rootId, edges.get(1).getToNode().getIdentifier());

        node = database.getNode(commit1);
        assertNotNull(node);

        edges = ImmutableList.copyOf(node.getEdges(Direction.IN));
        assertEquals(1, edges.size());

        edges = ImmutableList.copyOf(node.getEdges(Direction.OUT));
        assertEquals(1, edges.size());

    }

    @Test
    public void testTruncate() throws IOException {
        ObjectId rootId = RevObjectTestSupport.hashString("root");
        database.put(rootId, ImmutableList.of());
        ObjectId commit1 = RevObjectTestSupport.hashString("c1");
        database.put(commit1, ImmutableList.of(rootId));
        ObjectId commit2 = RevObjectTestSupport.hashString("c2");
        database.put(commit2, ImmutableList.of(commit1, rootId));

        assertTrue(database.exists(rootId));
        assertTrue(database.exists(commit1));
        assertTrue(database.exists(commit2));

        assertNotNull(database.getNode(rootId));
        assertNotNull(database.getNode(commit1));
        assertNotNull(database.getNode(commit2));
        assertEquals(1, database.getDepth(commit2));

        database.truncate();

        // not using getNode for assertions cause it's contract is not well defined for an invalid
        // argument and implementations behave differently. Created an issue to fix it.
        // assertNull(database.getNode(rootId));
        // assertNull(database.getNode(commit1));
        // assertNull(database.getNode(commit2));
        // assertEquals(0, database.getDepth(commit2));

        assertFalse(database.exists(rootId));
        assertFalse(database.exists(commit1));
        assertFalse(database.exists(commit2));
    }

    @Test
    public void testGetChildren() {
        ObjectId rootId = RevObjectTestSupport.hashString("root");
        database.put(rootId, ImmutableList.of());
        ObjectId commit1 = RevObjectTestSupport.hashString("c1");
        database.put(commit1, ImmutableList.of(rootId));
        ObjectId commit2 = RevObjectTestSupport.hashString("c2");
        database.put(commit2, ImmutableList.of(commit1, rootId));

        ImmutableList<ObjectId> children = database.getChildren(rootId);
        assertEquals(2, children.size());
        assertTrue(children.contains(commit1));
        assertTrue(children.contains(commit2));

        children = database.getChildren(commit1);
        assertEquals(1, children.size());
        assertTrue(children.contains(commit2));

        children = database.getChildren(commit2);
        assertEquals(0, children.size());

        children = database.getChildren(RevObjectTestSupport.hashString("nonexistent"));
        assertEquals(0, children.size());
    }

    @Test
    public void testGetParents() {
        ObjectId rootId = RevObjectTestSupport.hashString("root");
        database.put(rootId, ImmutableList.of());
        ObjectId commit1 = RevObjectTestSupport.hashString("c1");
        database.put(commit1, ImmutableList.of(rootId));
        ObjectId commit2 = RevObjectTestSupport.hashString("c2");
        database.put(commit2, ImmutableList.of(commit1, rootId));

        ImmutableList<ObjectId> parents = database.getParents(rootId);
        assertEquals(0, parents.size());

        parents = database.getParents(commit1);
        assertEquals(1, parents.size());
        assertTrue(parents.contains(rootId));

        parents = database.getParents(commit2);
        assertEquals(2, parents.size());
        assertEquals(commit1, parents.get(0));
        assertEquals(rootId, parents.get(1));

        parents = database.getParents(RevObjectTestSupport.hashString("nonexistent"));
        assertEquals(0, parents.size());
    }

    @Test
    public void testUpdateNode() {
        ObjectId nodeId = RevObjectTestSupport.hashString("node");
        ObjectId nodeParent = RevObjectTestSupport.hashString("nodeParent");
        boolean updated = database.put(nodeId, ImmutableList.of());
        assertTrue(updated);

        GraphNode node = database.getNode(nodeId);
        assertFalse(node.getEdges(Direction.BOTH).hasNext());

        updated = database.put(nodeId, ImmutableList.of(nodeParent));
        assertTrue(updated);
        node = database.getNode(nodeId);
        Iterator<GraphEdge> edges = node.getEdges(Direction.BOTH);
        assertTrue(edges.hasNext());
        GraphEdge edge = edges.next();
        assertEquals(nodeId, edge.getFromNode().getIdentifier());
        assertEquals(nodeParent, edge.getToNode().getIdentifier());

        updated = database.put(nodeId, ImmutableList.of(nodeParent));
        assertFalse(updated);
    }

    @Test
    public void testSparseNode() {
        ObjectId nodeId = RevObjectTestSupport.hashString("node");
        database.put(nodeId, ImmutableList.of());

        GraphNode node = database.getNode(nodeId);
        assertFalse(node.isSparse());

        database.setProperty(nodeId, GraphDatabase.SPARSE_FLAG, "true");

        node = database.getNode(nodeId);
        assertTrue(node.isSparse());

        database.setProperty(nodeId, GraphDatabase.SPARSE_FLAG, "false");

        node = database.getNode(nodeId);
        assertFalse(node.isSparse());
    }

    @Test
    public void testPutConcurrency() throws InterruptedException, ExecutionException {
        final int threadCount = 4;
        final int taskCount = 64;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        final ObjectId rootId = RevObjectTestSupport.hashString("root");
        final ObjectId commit1 = RevObjectTestSupport.hashString("c1");
        final ObjectId commit2 = RevObjectTestSupport.hashString("c2");

        for (int t = 0; t < taskCount; t++) {
            Future<?> future = executor.submit(new Runnable() {
                @Override
                public void run() {
                    database.put(rootId, ImmutableList.of());
                    database.put(commit1, ImmutableList.of(rootId));
                    database.put(commit2, ImmutableList.of(commit1, rootId));
                }
            });
            futures.add(future);
        }

        for (Future<?> f : futures) {
            f.get();
        }

        GraphNode node;
        List<GraphEdge> edges;

        node = database.getNode(commit2);
        assertNotNull(node);

        edges = ImmutableList.copyOf(node.getEdges(Direction.IN));
        assertTrue(edges.isEmpty());

        edges = ImmutableList.copyOf(node.getEdges(Direction.OUT));
        assertEquals(2, edges.size());
        assertEquals(commit1, edges.get(0).getToNode().getIdentifier());
        assertEquals(rootId, edges.get(1).getToNode().getIdentifier());

        node = database.getNode(commit1);
        assertNotNull(node);

        edges = ImmutableList.copyOf(node.getEdges(Direction.IN));
        assertEquals(1, edges.size());

        edges = ImmutableList.copyOf(node.getEdges(Direction.OUT));
        assertEquals(1, edges.size());

        node = database.getNode(rootId);
        assertNotNull(node);

        edges = ImmutableList.copyOf(node.getEdges(Direction.IN));
        assertEquals(2, edges.size());

        edges = ImmutableList.copyOf(node.getEdges(Direction.OUT));
        assertTrue(edges.isEmpty());
    }

    @Test
    public void testUpdateConcurrency() throws InterruptedException, ExecutionException {
        final int threadCount = 4;
        final int taskCount = 64;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        final ObjectId rootId = RevObjectTestSupport.hashString("root");
        final ObjectId commit1 = RevObjectTestSupport.hashString("c1");
        final ObjectId commit2 = RevObjectTestSupport.hashString("c2");

        database.put(rootId, ImmutableList.of());
        database.put(commit1, ImmutableList.of());
        database.put(commit2, ImmutableList.of());

        for (int t = 0; t < taskCount; t++) {
            Future<?> future = executor.submit(new Runnable() {
                @Override
                public void run() {
                    database.put(rootId, ImmutableList.of());
                    database.put(commit1, ImmutableList.of(rootId));
                    database.put(commit2, ImmutableList.of(commit1, rootId));
                }
            });
            futures.add(future);
        }

        for (Future<?> f : futures) {
            f.get();
        }

        GraphNode node;
        List<GraphEdge> edges;

        node = database.getNode(commit2);
        assertNotNull(node);

        edges = ImmutableList.copyOf(node.getEdges(Direction.IN));
        assertTrue(edges.isEmpty());

        edges = ImmutableList.copyOf(node.getEdges(Direction.OUT));
        assertEquals(2, edges.size());
        assertEquals(commit1, edges.get(0).getToNode().getIdentifier());
        assertEquals(rootId, edges.get(1).getToNode().getIdentifier());

        node = database.getNode(commit1);
        assertNotNull(node);

        edges = ImmutableList.copyOf(node.getEdges(Direction.IN));
        assertEquals(1, edges.size());

        edges = ImmutableList.copyOf(node.getEdges(Direction.OUT));
        assertEquals(1, edges.size());

        node = database.getNode(rootId);
        assertNotNull(node);

        edges = ImmutableList.copyOf(node.getEdges(Direction.IN));
        assertEquals(2, edges.size());

        edges = ImmutableList.copyOf(node.getEdges(Direction.OUT));
        assertTrue(edges.isEmpty());
    }

    @Test
    public void testGetConcurrency() throws InterruptedException, ExecutionException {
        final int threadCount = 4;
        final int taskCount = 64;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        final ObjectId rootId = RevObjectTestSupport.hashString("root");
        final ObjectId commit1 = RevObjectTestSupport.hashString("c1");
        final ObjectId commit2 = RevObjectTestSupport.hashString("c2");

        database.put(rootId, ImmutableList.of());
        database.put(commit1, ImmutableList.of(rootId));
        database.put(commit2, ImmutableList.of(commit1, rootId));

        for (int t = 0; t < taskCount; t++) {
            Future<?> future = executor.submit(new Runnable() {
                @Override
                public void run() {
                    GraphNode commit2Node = database.getNode(commit2);
                    GraphNode commit1Node = database.getNode(commit1);
                    GraphNode rootNode = database.getNode(rootId);
                    List<GraphEdge> edges;

                    assertNotNull(commit2Node);
                    assertNotNull(commit1Node);
                    assertNotNull(rootNode);

                    edges = ImmutableList.copyOf(commit2Node.getEdges(Direction.IN));
                    assertTrue(edges.isEmpty());

                    edges = ImmutableList.copyOf(commit2Node.getEdges(Direction.OUT));
                    assertEquals(2, edges.size());
                    assertEquals(commit1, edges.get(0).getToNode().getIdentifier());
                    assertEquals(rootId, edges.get(1).getToNode().getIdentifier());

                    edges = ImmutableList.copyOf(commit1Node.getEdges(Direction.IN));
                    assertEquals(1, edges.size());

                    edges = ImmutableList.copyOf(commit1Node.getEdges(Direction.OUT));
                    assertEquals(1, edges.size());

                    edges = ImmutableList.copyOf(rootNode.getEdges(Direction.IN));
                    assertEquals(2, edges.size());

                    edges = ImmutableList.copyOf(rootNode.getEdges(Direction.OUT));
                    assertTrue(edges.isEmpty());
                }
            });
            futures.add(future);
        }

        for (Future<?> f : futures) {
            f.get();
        }
    }
}
