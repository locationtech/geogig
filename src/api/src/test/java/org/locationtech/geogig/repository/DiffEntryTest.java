/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.jts.geom.Envelope;

public class DiffEntryTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testChangeTypes() {
        assertEquals(ChangeType.ADDED, ChangeType.valueOf(ChangeType.ADDED.value()));
        assertEquals(ChangeType.REMOVED, ChangeType.valueOf(ChangeType.REMOVED.value()));
        assertEquals(ChangeType.MODIFIED, ChangeType.valueOf(ChangeType.MODIFIED.value()));
        exception.expect(ArrayIndexOutOfBoundsException.class);
        ChangeType.valueOf(3);
    }

    @Test
    public void testConstructorAndAccessors() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0010"), TYPE.FEATURE, null,
                null);
        NodeRef nodeRef = new NodeRef(node, "Points", ObjectId.NULL);
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0011"), TYPE.FEATURE, null,
                null);
        NodeRef nodeRef2 = new NodeRef(node2, "Lines", ObjectId.NULL);
        Node node3 = RevObjectFactory.defaultInstance().createNode("Lines",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0002"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0012"), TYPE.TREE, null,
                null);
        NodeRef nodeRef3 = new NodeRef(node3, NodeRef.ROOT, ObjectId.NULL);

        try {
            new DiffEntry(null, null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            new DiffEntry(nodeRef, nodeRef);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            new DiffEntry(nodeRef, nodeRef3);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        DiffEntry entry = new DiffEntry(nodeRef, null);
        assertEquals(nodeRef, entry.getOldObject());
        assertEquals(nodeRef, entry.oldObject().get());
        assertEquals(node.getObjectId(), entry.oldObjectId());
        assertEquals(node.getMetadataId().get(), entry.oldMetadataId());
        assertEquals(node.getName(), entry.oldName());
        assertEquals(node.getType(), entry.oldObjectType());
        assertEquals(nodeRef.path(), entry.oldPath());
        assertEquals(null, entry.getNewObject());
        assertFalse(entry.newObject().isPresent());
        assertEquals(ObjectId.NULL, entry.newObjectId());
        assertEquals(ObjectId.NULL, entry.newMetadataId());
        assertEquals(null, entry.newName());
        assertEquals(null, entry.newObjectType());
        assertEquals(null, entry.newPath());
        assertEquals(ChangeType.REMOVED, entry.changeType());
        assertTrue(entry.isDelete());
        assertFalse(entry.isAdd());
        assertFalse(entry.isChange());
        assertEquals(nodeRef.path(), entry.path());

        entry = new DiffEntry(null, nodeRef);
        assertEquals(null, entry.getOldObject());
        assertFalse(entry.oldObject().isPresent());
        assertEquals(ObjectId.NULL, entry.oldObjectId());
        assertEquals(ObjectId.NULL, entry.oldMetadataId());
        assertEquals(null, entry.oldName());
        assertEquals(null, entry.oldObjectType());
        assertEquals(null, entry.oldPath());
        assertEquals(nodeRef, entry.getNewObject());
        assertEquals(nodeRef, entry.newObject().get());
        assertEquals(node.getObjectId(), entry.newObjectId());
        assertEquals(node.getMetadataId().get(), entry.newMetadataId());
        assertEquals(node.getName(), entry.newName());
        assertEquals(node.getType(), entry.newObjectType());
        assertEquals(nodeRef.path(), entry.newPath());
        assertEquals(ChangeType.ADDED, entry.changeType());
        assertFalse(entry.isDelete());
        assertTrue(entry.isAdd());
        assertFalse(entry.isChange());
        assertEquals(nodeRef.path(), entry.path());

        entry = new DiffEntry(nodeRef, nodeRef2);
        assertEquals(nodeRef, entry.getOldObject());
        assertEquals(nodeRef, entry.oldObject().get());
        assertEquals(node.getObjectId(), entry.oldObjectId());
        assertEquals(node.getMetadataId().get(), entry.oldMetadataId());
        assertEquals(node.getName(), entry.oldName());
        assertEquals(node.getType(), entry.oldObjectType());
        assertEquals(nodeRef.path(), entry.oldPath());
        assertEquals(nodeRef2, entry.getNewObject());
        assertEquals(nodeRef2, entry.newObject().get());
        assertEquals(node2.getObjectId(), entry.newObjectId());
        assertEquals(node2.getMetadataId().get(), entry.newMetadataId());
        assertEquals(node2.getName(), entry.newName());
        assertEquals(node2.getType(), entry.newObjectType());
        assertEquals(nodeRef2.path(), entry.newPath());
        assertEquals(ChangeType.MODIFIED, entry.changeType());
        assertFalse(entry.isDelete());
        assertFalse(entry.isAdd());
        assertTrue(entry.isChange());
        assertEquals(nodeRef2.path(), entry.path());
    }

    @Test
    public void testToString() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0010"), TYPE.FEATURE, null,
                null);
        NodeRef nodeRef = new NodeRef(node, "Points", ObjectId.NULL);
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0011"), TYPE.FEATURE, null,
                null);
        NodeRef nodeRef2 = new NodeRef(node2, "Lines", ObjectId.NULL);

        DiffEntry entry = new DiffEntry(nodeRef, null);
        assertTrue(entry.toString().contains("REMOVED"));
        assertTrue(entry.toString().contains(nodeRef.toString()));

        entry = new DiffEntry(null, nodeRef);
        assertTrue(entry.toString().contains("ADDED"));
        assertTrue(entry.toString().contains(nodeRef.toString()));

        entry = new DiffEntry(nodeRef, nodeRef2);
        assertTrue(entry.toString().contains("MODIFIED"));
        assertTrue(entry.toString().contains("] -> ["));
        String[] tokens = entry.toString().split("\\] -> \\[");
        assertEquals(2, tokens.length);
        assertTrue(tokens[0].contains(nodeRef.toString()));
        assertTrue(tokens[1].contains(nodeRef2.toString()));
    }

    @Test
    public void testEquals() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0010"), TYPE.FEATURE, null,
                null);
        NodeRef nodeRef = new NodeRef(node, "Points", ObjectId.NULL);
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0011"), TYPE.FEATURE, null,
                null);
        NodeRef nodeRef2 = new NodeRef(node2, "Lines", ObjectId.NULL);

        DiffEntry entry1 = new DiffEntry(nodeRef, null);
        DiffEntry entry2 = new DiffEntry(null, nodeRef);
        DiffEntry entry3 = new DiffEntry(nodeRef, nodeRef2);
        DiffEntry entry4 = new DiffEntry(nodeRef2, nodeRef);
        assertEquals(entry1, entry1);
        assertEquals(entry2, entry2);
        assertEquals(entry3, entry3);
        assertEquals(entry4, entry4);
        assertFalse(entry1.equals(entry2));
        assertFalse(entry1.equals(entry3));
        assertFalse(entry1.equals(entry4));
        assertFalse(entry2.equals(entry3));
        assertFalse(entry2.equals(entry4));
        assertFalse(entry3.equals(entry4));

        assertFalse(entry1.equals(nodeRef));
    }

    @Test
    public void testExpand() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0010"), TYPE.FEATURE,
                new Envelope(0, 1, 0, 1), null);
        NodeRef nodeRef = new NodeRef(node, "Points", ObjectId.NULL);
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0011"), TYPE.FEATURE,
                new Envelope(1, 2, 1, 2), null);
        NodeRef nodeRef2 = new NodeRef(node2, "Lines", ObjectId.NULL);

        DiffEntry entry1 = new DiffEntry(nodeRef, null);
        DiffEntry entry2 = new DiffEntry(null, nodeRef);
        DiffEntry entry3 = new DiffEntry(nodeRef, nodeRef2);

        Envelope env = new Envelope();
        entry1.expand(env);
        assertEquals(new Envelope(0, 1, 0, 1), env);
        env = new Envelope();
        entry2.expand(env);
        assertEquals(new Envelope(0, 1, 0, 1), env);
        env = new Envelope();
        entry3.expand(env);
        assertEquals(new Envelope(0, 2, 0, 2), env);
    }

    @Test
    public void testHashCode() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0010"), TYPE.FEATURE,
                new Envelope(0, 1, 0, 1), null);
        NodeRef nodeRef = new NodeRef(node, "Points", ObjectId.NULL);
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0011"), TYPE.FEATURE,
                new Envelope(1, 2, 1, 2), null);
        NodeRef nodeRef2 = new NodeRef(node2, "Lines", ObjectId.NULL);

        DiffEntry entry1 = new DiffEntry(nodeRef, null);
        DiffEntry entry2 = new DiffEntry(null, nodeRef);
        DiffEntry entry3 = new DiffEntry(nodeRef, nodeRef2);

        assertNotSame(entry1.hashCode(), entry2.hashCode());
        assertNotSame(entry1.hashCode(), entry3.hashCode());
        assertNotSame(entry2.hashCode(), entry3.hashCode());
    }

    @Test
    public void testComparator() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0010"), TYPE.FEATURE,
                new Envelope(0, 1, 0, 1), null);
        NodeRef nodeRef = new NodeRef(node, "Points", ObjectId.NULL);
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0011"), TYPE.FEATURE,
                new Envelope(1, 2, 1, 2), null);
        NodeRef nodeRef2 = new NodeRef(node2, "Lines", ObjectId.NULL);
        Node node3 = RevObjectFactory.defaultInstance().createNode("Lines",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0002"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0012"), TYPE.TREE, null,
                null);
        NodeRef nodeRef3 = new NodeRef(node3, NodeRef.ROOT, ObjectId.NULL);

        DiffEntry entry1 = new DiffEntry(null, nodeRef);
        DiffEntry entry2 = new DiffEntry(null, nodeRef2);
        DiffEntry entry3 = new DiffEntry(null, nodeRef3);

        // Feature should come after a tree that it is a child of
        assertTrue(DiffEntry.COMPARATOR.compare(entry2, entry3) > 0);

        // Tree should come before a feature that it is a parent of
        assertTrue(DiffEntry.COMPARATOR.compare(entry3, entry2) < 0);

        // Tree should come after a feature that it is not the parent of
        assertTrue(DiffEntry.COMPARATOR.compare(entry3, entry1) > 0);

        // Feature should come before a tree that it is not a child of
        assertTrue(DiffEntry.COMPARATOR.compare(entry1, entry3) < 0);

        // If both are features, they should be ordered by their nodes
        assertEquals(DiffEntry.COMPARATOR.compare(entry1, entry2),
                CanonicalNodeOrder.INSTANCE.compare(nodeRef.getNode(), nodeRef2.getNode()));
    }
}
