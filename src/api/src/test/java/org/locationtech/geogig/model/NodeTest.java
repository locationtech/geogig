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
import java.util.Map.Entry;
import java.util.TreeMap;

import org.geotools.geometry.jts.WKTReader2;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

public class NodeTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testNodeAccessorsAndConstructors() {
        ObjectId oid = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        Map<String, Object> extraData = new HashMap<String, Object>();
        extraData.put("key", "value");
        Node node = RevObjectFactory.defaultInstance().createNode("Points", oid, ObjectId.NULL,
                TYPE.TREE, null, extraData);
        assertEquals(Optional.absent(), node.getMetadataId());
        assertEquals("Points", node.getName());
        assertEquals(oid, node.getObjectId());
        assertEquals(TYPE.TREE, node.getType());
        assertEquals(extraData, node.getExtraData());
    }

    @Test
    public void testIsEqual() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        Node node3 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0002"), TYPE.TREE, null,
                null);
        Node node4 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0003"), ObjectId.NULL,
                TYPE.FEATURE, null, null);

        assertFalse(node.equals("NotANode"));
        assertFalse(node.equals(node2));
        assertFalse(node2.equals(node3));
        assertFalse(node2.equals(node4));
        assertTrue(node.equals(node));
    }

    @Test
    public void testToString() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);

        String readableNode = node.toString();
        String expected = RevObjects.toString(node);
        assertEquals(expected, readableNode.toString());
    }

    @Test
    public void testCompareTo() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.FEATURE, null, null);

        assertTrue(node.compareTo(node2) > 0);
        assertTrue(node2.compareTo(node) < 0);
        assertTrue(node.compareTo(node) == 0);
    }

    @Test
    public void testUpdate() {
        ObjectId oId1 = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        ObjectId oId2 = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");
        ObjectId mId = ObjectId.valueOf("abc123000000000000001234567890abcdef0002");
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1", oId1, ObjectId.NULL,
                TYPE.FEATURE, null, null);

        Node updated = node.update(oId2);
        assertEquals(oId1, node.getObjectId());
        assertEquals(oId2, updated.getObjectId());
        assertFalse(node.bounds().isPresent());
        assertFalse(updated.bounds().isPresent());
        assertEquals(node.getMetadataId(), updated.getMetadataId());
        assertEquals(node.getName(), updated.getName());
        assertEquals(node.getType(), updated.getType());

        // try a node with bounds
        node = RevObjectFactory.defaultInstance().createNode("Points.1", oId1, ObjectId.NULL,
                TYPE.FEATURE, new Envelope(0, 1, 2, 3), null);
        updated = node.update(oId2);
        assertEquals(oId1, node.getObjectId());
        assertEquals(oId2, updated.getObjectId());
        assertEquals(node.bounds().get(), updated.bounds().get());
        assertEquals(node.getMetadataId(), updated.getMetadataId());
        assertEquals(node.getName(), updated.getName());
        assertEquals(node.getType(), updated.getType());

        // try with a non-null metadata id
        node = RevObjectFactory.defaultInstance().createNode("Points.1", oId1, mId, TYPE.FEATURE,
                null, null);

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
        Node unbounded1 = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        Node unbounded2 = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, new Envelope(), null);
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
        Node bounded1 = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, new Envelope(0, 1, 2, 3), null);
        Node bounded2 = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, new Envelope(0, 0, 1, 1), null);
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
        Node unbounded1 = RevObjectFactory.defaultInstance().createNode("Points",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.TREE, null, null);
        Node unbounded2 = RevObjectFactory.defaultInstance().createNode("Points",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.TREE, new Envelope(), null);
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
        Node bounded1 = RevObjectFactory.defaultInstance().createNode("Points",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.TREE, new Envelope(0, 1, 2, 3), null);
        Node bounded2 = RevObjectFactory.defaultInstance().createNode("Points",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.TREE, new Envelope(0, 0, 1, 1), null);
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
        exception.expectMessage("only FEATURE and TREE nodes can be created");
        RevObjectFactory.defaultInstance().createNode("Points",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURETYPE, null, null);
    }

    @Test
    public void testHashCode() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.FEATURE, null, null);

        assertNotSame(node.hashCode(), node2.hashCode());
    }

    public @Test void testNestedExtraData() throws Exception {
        Map<String, Object> map1, map2, extraData;
        map1 = new HashMap<>();
        map2 = new TreeMap<>();

        map1.put("long", Long.valueOf(123));
        map2.put("long", Long.valueOf(123));

        map1.put("int", Integer.valueOf(456));
        map2.put("int", Integer.valueOf(456));

        map1.put("string", "hello");
        map2.put("string", "hello");

        map1.put("geom", geom("LINESTRING(1 1, 1.1 2.1, 100 1000)"));
        map2.put("geom", geom("LINESTRING(1 1, 1.1 2.1, 100 1000)"));

        extraData = ImmutableMap.of("I", (Object) "am", "a", (Object) "different", "map than",
                (Object) map1, "and", (Object) map2);

        Node n = RevObjectFactory.defaultInstance().createNode("fid", RevTree.EMPTY_TREE_ID,
                ObjectId.NULL, TYPE.FEATURE, null, extraData);

        Map<String, Object> actual = n.getExtraData();
        assertEqualsFully(extraData, actual);
    }

    private void assertEqualsFully(Map<?, ?> expected, Map<?, ?> actual) {
        assertNotSame(expected, actual);
        assertEquals(expected, actual);
        for (Entry<?, ?> e : expected.entrySet()) {
            String k = (String) e.getKey();
            Object v = e.getValue();
            Object v2 = actual.get(k);
            assertEquals(k, v, v2);
            if (v instanceof Map) {
                assertEqualsFully((Map<?, ?>) v, (Map<?, ?>) v2);
            } else if (v instanceof Geometry) {
                assertNotSame("geometry is mutable, should have been safe copied", v, v2);
            }
        }
    }

    private Geometry geom(String wkt) throws ParseException {
        Geometry value = new WKTReader2().read(wkt);
        return value;
    }
}
