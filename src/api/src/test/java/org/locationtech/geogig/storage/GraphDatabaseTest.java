/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.storage.GraphDatabase.Direction;
import org.locationtech.geogig.storage.GraphDatabase.GraphEdge;
import org.locationtech.geogig.storage.GraphDatabase.GraphNode;

public class GraphDatabaseTest {

    private class TestGraphNode extends GraphNode {

        private final ObjectId identifier;

        public TestGraphNode(ObjectId identifier) {
            this.identifier = identifier;
        }

        @Override
        public ObjectId getIdentifier() {
            return identifier;
        }

        @Override
        public Iterator<GraphEdge> getEdges(Direction direction) {
            return null;
        }

        @Override
        public boolean isSparse() {
            return false;
        }

    }

    @Test
    public void testGraphNode() {
        GraphNode node1 = new TestGraphNode(
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"));
        GraphNode node1_identical = new TestGraphNode(
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"));
        GraphNode node2 = new TestGraphNode(
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"));
        assertTrue(node1.equals(node1));
        assertTrue(node1.equals(node1_identical));
        assertFalse(node1.equals(node2));
        assertFalse(node1.equals("node1"));
        assertFalse(node1.equals(null));
        assertEquals(node1.hashCode(), node1_identical.hashCode());
        assertNotSame(node1.hashCode(), node2.hashCode());
    }

    @Test
    public void testGraphEdge() {
        GraphNode node1 = new TestGraphNode(
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"));
        GraphNode node2 = new TestGraphNode(
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"));

        GraphEdge edge = new GraphEdge(node1, node2);
        assertEquals(node1, edge.getFromNode());
        assertEquals(node2, edge.getToNode());
        assertEquals(node1.toString() + ":" + node2.toString(), edge.toString());
    }

    @Test
    public void testEnumerations() {
        assertEquals(GraphDatabase.Direction.BOTH, GraphDatabase.Direction.valueOf("BOTH"));
        assertEquals(GraphDatabase.Direction.IN, GraphDatabase.Direction.valueOf("IN"));
        assertEquals(GraphDatabase.Direction.OUT, GraphDatabase.Direction.valueOf("OUT"));
        assertEquals(3, GraphDatabase.Direction.values().length);
    }
}
