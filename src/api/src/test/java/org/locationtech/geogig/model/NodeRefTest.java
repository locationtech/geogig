/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.model.NodeRef.allPathsTo;
import static org.locationtech.geogig.model.NodeRef.isChild;
import static org.locationtech.geogig.model.NodeRef.isDirectChild;
import static org.locationtech.geogig.model.NodeRef.parentPath;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.jts.geom.Envelope;

import com.google.common.collect.ImmutableList;

public class NodeRefTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    /**
     * Test method for {@link org.locationtech.geogig.model.Node#parentPath(java.lang.String)}.
     */
    @Test
    public void testParentPath() {
        assertNull(parentPath(null));
        assertNull(parentPath(""));
        assertEquals("", parentPath("node"));
        assertEquals("to", parentPath("to/node"));
        assertEquals("path/to", parentPath("path/to/node"));
    }

    /**
     * Test method for {@link org.locationtech.geogig.model.Node#allPathsTo(java.lang.String)}.
     */
    @Test
    public void testAllPathsTo() {
        try {
            allPathsTo(null);
            fail("Expected precondition violation");
        } catch (NullPointerException e) {
            assertTrue(true);
        }
        try {
            allPathsTo("");
            fail("Expected precondition violation");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        assertEquals(ImmutableList.of("path"), allPathsTo("path"));
        assertEquals(ImmutableList.of("path", "path/to"), allPathsTo("path/to"));
        assertEquals(ImmutableList.of("path", "path/to", "path/to/node"),
                allPathsTo("path/to/node"));
    }

    /**
     * Test method for {@link org.locationtech.geogig.model.Node#isDirectChild(String, String)}
     */
    @Test
    public void testIsDirectChild() {
        assertFalse(isDirectChild("", ""));
        assertTrue(isDirectChild("", "path"));
        assertFalse(isDirectChild("", "path/to"));

        assertFalse(isDirectChild("path", "path"));
        assertFalse(isDirectChild("path", ""));
        assertTrue(isDirectChild("path", "path/to"));
        assertFalse(isDirectChild("path", "path/to/node"));

        assertFalse(isDirectChild("path/to", ""));
        assertFalse(isDirectChild("path/to", "path"));
        assertFalse(isDirectChild("path/to", "path/to"));
        assertFalse(isDirectChild("path/to", "path2/to"));

        assertTrue(isDirectChild("path/to", "path/to/node"));

        assertTrue(isDirectChild("roads", "roads/highway"));
        assertFalse(isDirectChild("roads/highway", "roads"));
    }

    /**
     * Test method for {@link org.locationtech.geogig.model.Node#isChild(String, String)}
     */
    @Test
    public void testIsChild() {
        assertFalse(isChild("", ""));
        assertTrue(isChild("", "path"));
        assertTrue(isChild("", "path/to"));

        assertFalse(isChild("path", "path"));
        assertFalse(isChild("path", ""));
        assertTrue(isChild("path", "path/to"));
        assertTrue(isChild("path", "path/to/node"));

        assertFalse(isChild("path/to", ""));
        assertFalse(isChild("path/to", "path"));
        assertFalse(isChild("path/to", "path/to"));
        assertFalse(isChild("path/to", "path2/to"));

        assertTrue(isChild("path/to", "path/to/node"));
    }

    @Test
    public void testCheckValidPathNull() {
        exception.expect(IllegalArgumentException.class);
        NodeRef.checkValidPath(null);
    }

    @Test
    public void testCheckValidPathEmptyString() {
        exception.expect(IllegalArgumentException.class);
        NodeRef.checkValidPath("");
    }

    @Test
    public void testCheckValidPathPathEndingWithSeperator() {
        exception.expect(IllegalArgumentException.class);
        NodeRef.checkValidPath("Points/");
    }

    @Test
    public void testCheckValidPath() {
        NodeRef.checkValidPath("Points");
    }

    @Test
    public void testNodeFromPath() {
        String node = NodeRef.nodeFromPath("Points/Points.1");
        assertEquals(node, "Points.1");
        node = NodeRef.nodeFromPath("refs/heads/master");
        assertEquals(node, "master");
        node = NodeRef.nodeFromPath("Points.1");
        assertEquals(node, "Points.1");
        node = NodeRef.nodeFromPath("");
        assertNull(node);
        node = NodeRef.nodeFromPath(null);
        assertNull(node);
    }

    @Test
    public void testAppendChild() {
        String fullString = NodeRef.appendChild("Points", "Points.1");
        assertEquals("Points/Points.1", fullString);
        fullString = NodeRef.appendChild("", "refs");
        assertEquals("refs", fullString);
        assertEquals("", NodeRef.appendChild(null, ""));
        exception.expect(IllegalArgumentException.class);
        NodeRef.appendChild(null, "someValue");
    }

    @Test
    public void testAccessorsAndConstructors() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        NodeRef nodeRef = new NodeRef(node, "Points", ObjectId.NULL);
        assertEquals(ObjectId.NULL, nodeRef.getMetadataId());
        assertEquals(ObjectId.NULL, nodeRef.getDefaultMetadataId());
        assertEquals(node.getName(), nodeRef.name());
        assertEquals(node.getObjectId(), nodeRef.getObjectId());
        assertEquals(node, nodeRef.getNode());
        assertEquals(node.getType(), nodeRef.getType());
        assertEquals("Points", nodeRef.getParentPath());
        assertEquals("Points/Points.1", nodeRef.path());

        exception.expect(IllegalArgumentException.class);
        new NodeRef(node, null, ObjectId.NULL);
    }

    @Test
    public void testIsEqual() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        NodeRef nodeRef = new NodeRef(node, "Points", ObjectId.NULL);
        assertFalse(nodeRef.equals(node));
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        NodeRef nodeRef2 = new NodeRef(node2, "Lines", ObjectId.NULL);
        NodeRef nodeRef3 = new NodeRef(node2, "Lines",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"));
        assertFalse(nodeRef.equals(nodeRef2));
        assertTrue(nodeRef.equals(nodeRef));
        assertFalse(nodeRef2.equals(nodeRef3));
    }

    @Test
    public void testNodeAndNodeRefToString() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        NodeRef nodeRef = new NodeRef(node, "Points", ObjectId.NULL);

        String readableNode = nodeRef.toString();

        assertTrue(readableNode
                .equals("NodeRef[Points/Points.1 -> " + node.getObjectId().toString() + "]"));
    }

    @Test
    public void testCompareTo() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        NodeRef nodeRef = NodeRef.create("Points", node);
        assertFalse(nodeRef.equals(node));
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        NodeRef nodeRef2 = new NodeRef(node2, "Lines", ObjectId.NULL);
        assertTrue(nodeRef.compareTo(nodeRef2) > 0);
        assertTrue(nodeRef2.compareTo(nodeRef) < 0);
        assertTrue(nodeRef.compareTo(nodeRef) == 0);
    }

    @Test
    public void testUpdate() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        NodeRef nodeRef = new NodeRef(node, "Points", ObjectId.NULL);
        NodeRef updated = nodeRef.update(
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"),
                new Envelope(0, 1, 2, 3));

        assertNotSame(nodeRef, updated);
        assertEquals(ObjectId.valueOf("abc123000000000000001234567890abcdef0001"),
                updated.getObjectId());
        assertEquals(new Envelope(0, 1, 2, 3), updated.bounds().get());
    }

    @Test
    public void testCreateRoot() {
        Node node = RevObjectFactory.defaultInstance().createNode(NodeRef.ROOT,
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        NodeRef root = NodeRef.createRoot(node);
        assertEquals(node, root.getNode());

        Node node2 = RevObjectFactory.defaultInstance().createNode("nonRootPath",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        exception.expect(IllegalArgumentException.class);
        NodeRef.createRoot(node2);

    }

    @Test
    public void testSplit() {
        assertEquals(ImmutableList.of("Points", "sub", "points.1"),
                NodeRef.split("Points/sub/points.1"));
        assertEquals(ImmutableList.of(), NodeRef.split(""));

        exception.expect(NullPointerException.class);
        NodeRef.split(null);
    }

    @Test
    public void testDepth() {
        assertEquals(3, NodeRef.depth("Points/sub/points.1"));
        assertEquals(2, NodeRef.depth("Points/points.1"));
        assertEquals(1, NodeRef.depth("Points"));
        assertEquals(0, NodeRef.depth(""));
    }

    @Test
    public void testRemoveParent() {
        assertEquals("sub/points.1", NodeRef.removeParent("Points", "Points/sub/points.1"));
        assertEquals("points.1", NodeRef.removeParent("Points", "Points/points.1"));
        exception.expect(IllegalArgumentException.class);
        NodeRef.removeParent("Lines", "Points/points.1");
    }

    @Test
    public void testTree() {
        ObjectId oId = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        ObjectId metadataId = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");
        NodeRef nodeRef = NodeRef.tree("Points", oId, metadataId);
        assertEquals(oId, nodeRef.getObjectId());
        assertEquals(metadataId, nodeRef.getMetadataId());
        assertEquals("Points", nodeRef.getNode().getName());
    }

    @Test
    public void testHashCode() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        NodeRef nodeRef = NodeRef.create("Points", node);
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        NodeRef nodeRef2 = new NodeRef(node2, "Lines", ObjectId.NULL);
        assertNotSame(nodeRef.hashCode(), nodeRef2.hashCode());

        Node node3 = RevObjectFactory.defaultInstance().createNode(NodeRef.ROOT,
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        NodeRef root = NodeRef.createRoot(node3);
        root.hashCode();
    }

    @Test
    public void testIntersects() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, new Envelope(0, 1, 2, 3), null);
        NodeRef nodeRef = NodeRef.create("Points", node);
        assertTrue(nodeRef.intersects(new Envelope(0, 0.5, 2, 2.5)));
        assertFalse(nodeRef.intersects(new Envelope(2, 3, 2, 3)));
    }

    @Test
    public void testExpand() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, new Envelope(0, 1, 2, 3), null);
        NodeRef nodeRef = NodeRef.create("Points", node);
        Envelope env = new Envelope(1, 3, 1, 2);
        nodeRef.expand(env);
        assertEquals(new Envelope(0, 3, 1, 3), env);
    }
}
