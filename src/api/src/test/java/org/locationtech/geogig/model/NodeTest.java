/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.RevObject.TYPE;

import com.google.common.base.Optional;
import com.vividsolutions.jts.geom.Envelope;

public class NodeTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testNodeAccessorsAndConstructors() {
        ObjectId oid = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        Map<String, Object> extraData = new HashMap<String, Object>();
        extraData.put("key", "value");
        Node node = Node.create("Points", oid, ObjectId.NULL, TYPE.TREE, null, extraData);
        assertEquals(Optional.absent(), node.getMetadataId());
        assertEquals("Points", node.getName());
        assertEquals(oid, node.getObjectId());
        assertEquals(TYPE.TREE, node.getType());
        assertEquals(extraData, node.getExtraData());
    }

    @Test
    public void testIsEqual() {
        Node node = Node.create("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null);
        Node node2 = Node.create("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.FEATURE, null);
        Node node3 = Node.create("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0002"), TYPE.TREE, null);
        Node node4 = Node.create("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0003"), ObjectId.NULL,
                TYPE.FEATURE, null);

        assertFalse(node.equals("NotANode"));
        assertFalse(node.equals(node2));
        assertFalse(node2.equals(node3));
        assertFalse(node2.equals(node4));
        assertTrue(node.equals(node));
    }

    @Test
    public void testToString() {
        Node node = Node.create("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null);

        String readableNode = node.toString();
        String expected = "FeatureNode[Points.1 -> " + node.getObjectId() + "]";
        assertEquals(expected, readableNode.toString());
    }

    @Test
    public void testCompareTo() {
        Node node = Node.create("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null);
        Node node2 = Node.create("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.FEATURE, null);

        assertTrue(node.compareTo(node2) > 0);
        assertTrue(node2.compareTo(node) < 0);
        assertTrue(node.compareTo(node) == 0);
    }

    @Test
    public void testUpdate() {
        ObjectId oId1 = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        ObjectId oId2 = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");
        ObjectId mId = ObjectId.valueOf("abc123000000000000001234567890abcdef0002");
        Node node = Node.create("Points.1", oId1, ObjectId.NULL, TYPE.FEATURE, null);

        Node updated = node.update(oId2);
        assertEquals(oId1, node.getObjectId());
        assertEquals(oId2, updated.getObjectId());
        assertFalse(node.bounds().isPresent());
        assertFalse(updated.bounds().isPresent());
        assertEquals(node.getMetadataId(), updated.getMetadataId());
        assertEquals(node.getName(), updated.getName());
        assertEquals(node.getType(), updated.getType());

        // try a node with bounds
        node = Node.create("Points.1", oId1, ObjectId.NULL, TYPE.FEATURE, new Envelope(0, 1, 2, 3));
        updated = node.update(oId2);
        assertEquals(oId1, node.getObjectId());
        assertEquals(oId2, updated.getObjectId());
        assertEquals(node.bounds().get(), updated.bounds().get());
        assertEquals(node.getMetadataId(), updated.getMetadataId());
        assertEquals(node.getName(), updated.getName());
        assertEquals(node.getType(), updated.getType());

        // try with a non-null metadata id
        node = Node.create("Points.1", oId1, mId, TYPE.FEATURE, null);

        updated = node.update(oId2);
        assertEquals(oId1, node.getObjectId());
        assertEquals(oId2, updated.getObjectId());
        assertFalse(node.bounds().isPresent());
        assertFalse(updated.bounds().isPresent());
        assertEquals(node.getMetadataId(), updated.getMetadataId());
        assertEquals(node.getName(), updated.getName());
        assertEquals(node.getType(), updated.getType());
    }

    @Test
    public void testCreateFeatureBoundedAndUnbounded() {
        // Create unbounded feature nodes
        Node unbounded1 = Node.create("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null);
        Node unbounded2 = Node.create("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, new Envelope());
        Envelope testEnvelope = new Envelope(0, 0, 100, 100);
        assertFalse(unbounded1.bounds().isPresent());
        assertFalse(unbounded2.bounds().isPresent());
        assertFalse(unbounded1.intersects(testEnvelope));
        assertFalse(unbounded2.intersects(testEnvelope));
        assertFalse(unbounded1.intersects(new Envelope()));
        assertFalse(unbounded2.intersects(new Envelope()));
        unbounded1.expand(testEnvelope);
        assertEquals(new Envelope(0, 0, 100, 100), testEnvelope);
        unbounded2.expand(testEnvelope);
        assertEquals(new Envelope(0, 0, 100, 100), testEnvelope);

        // Create bounded feature nodes
        Node bounded1 = Node.create("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, new Envelope(0, 1, 2, 3));
        Node bounded2 = Node.create("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, new Envelope(0, 0, 1, 1));
        Envelope testEnvelope2 = new Envelope(0, 0, 2, 5);
        assertTrue(bounded1.bounds().isPresent());
        assertEquals(new Envelope(0, 1, 2, 3), bounded1.bounds().get());
        assertTrue(bounded2.bounds().isPresent());
        assertEquals(new Envelope(0, 0, 1, 1), bounded2.bounds().get());
        assertTrue(bounded1.intersects(testEnvelope2));
        // to the left
        assertFalse(bounded1.intersects(new Envelope(-1, -1, 2.5, 2.5)));
        // to the right
        assertFalse(bounded1.intersects(new Envelope(2, 2, 2.5, 2.5)));
        // above
        assertFalse(bounded1.intersects(new Envelope(0.5, 0.5, 4, 4)));
        // below
        assertFalse(bounded1.intersects(new Envelope(0.5, 0.5, 1, 1)));
        assertFalse(bounded2.intersects(testEnvelope2));
        assertFalse(bounded1.intersects(new Envelope()));
        assertFalse(bounded2.intersects(new Envelope()));
        bounded1.expand(testEnvelope2);
        assertEquals(new Envelope(0, 1, 2, 5), testEnvelope2);
        bounded2.expand(testEnvelope2);
        assertEquals(new Envelope(0, 1, 1, 5), testEnvelope2);
    }

    @Test
    public void testCreateTreeBoundedAndUnbounded() {
        // Create unbounded feature nodes
        Node unbounded1 = Node.tree("Points",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL);
        Node unbounded2 = Node.create("Points",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.TREE, new Envelope());
        Envelope testEnvelope = new Envelope(0, 0, 100, 100);
        assertFalse(unbounded1.bounds().isPresent());
        assertFalse(unbounded2.bounds().isPresent());
        assertFalse(unbounded1.intersects(testEnvelope));
        assertFalse(unbounded2.intersects(testEnvelope));
        assertFalse(unbounded1.intersects(new Envelope()));
        assertFalse(unbounded2.intersects(new Envelope()));
        unbounded1.expand(testEnvelope);
        assertEquals(new Envelope(0, 0, 100, 100), testEnvelope);
        unbounded2.expand(testEnvelope);
        assertEquals(new Envelope(0, 0, 100, 100), testEnvelope);

        // Create bounded feature node
        Node bounded1 = Node.create("Points",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.TREE, new Envelope(0, 1, 2, 3));
        Node bounded2 = Node.create("Points",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.TREE, new Envelope(0, 0, 1, 1));
        Envelope testEnvelope2 = new Envelope(0, 0, 2, 5);
        assertTrue(bounded1.bounds().isPresent());
        assertEquals(new Envelope(0, 1, 2, 3), bounded1.bounds().get());
        assertTrue(bounded2.bounds().isPresent());
        assertEquals(new Envelope(0, 0, 1, 1), bounded2.bounds().get());
        assertTrue(bounded1.intersects(testEnvelope2));
        // to the left
        assertFalse(bounded1.intersects(new Envelope(-1, -1, 2.5, 2.5)));
        // to the right
        assertFalse(bounded1.intersects(new Envelope(2, 2, 2.5, 2.5)));
        // above
        assertFalse(bounded1.intersects(new Envelope(0.5, 0.5, 4, 4)));
        // below
        assertFalse(bounded1.intersects(new Envelope(0.5, 0.5, 1, 1)));
        assertFalse(bounded2.intersects(testEnvelope2));
        assertFalse(bounded1.intersects(new Envelope()));
        assertFalse(bounded2.intersects(new Envelope()));
        bounded1.expand(testEnvelope2);
        assertEquals(new Envelope(0, 1, 2, 5), testEnvelope2);
        bounded2.expand(testEnvelope2);
        assertEquals(new Envelope(0, 1, 1, 5), testEnvelope2);
    }

    @Test
    public void testCreateInvalidType() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Only FEATURE and TREE nodes can be created");
        Node.create("Points", ObjectId.valueOf("abc123000000000000001234567890abcdef0000"),
                ObjectId.NULL, TYPE.FEATURETYPE, null);
    }

    @Test
    public void testSetExtraData() {
        Node node = Node.tree("Points",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL);
        assertEquals(null, node.getExtraData());
        Map<String, Object> extraData = new HashMap<String, Object>();
        extraData.put("key", "value");
        node.setExtraData(extraData);

        assertEquals(extraData, node.getExtraData());
    }

    @Test
    public void testHashCode() {
        Node node = Node.create("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null);
        Node node2 = Node.create("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.FEATURE, null);

        assertNotSame(node.hashCode(), node2.hashCode());
    }
}
