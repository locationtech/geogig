/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import static org.locationtech.geogig.model.NodeRef.ROOT;
import static org.locationtech.geogig.model.ObjectId.NULL;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectStore;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class MutableTreeTest extends Assert {

    ObjectStore store;

    MutableTree root;

    @Rule
    public ExpectedException ex = ExpectedException.none();

    @Before
    public void setUp() {
        store = new HeapObjectStore();
        store.open();
        ObjectId md1 = id("d1");
        ObjectId md2 = id("d2");
        ObjectId md3 = id("d3");
        ObjectId md4 = id("d4");

        ObjectId t1 = id("a1");
        ObjectId t2 = id("a2");
        ObjectId t3 = id("a3");
        ObjectId t4 = id("a4");
        ObjectId t5 = id("a5");
        ObjectId t6 = id("a6");

        NodeRef r1 = tree("roads", t1, ObjectId.NULL);
        NodeRef r11 = tree("roads/highways", t2, md1);
        NodeRef r12 = tree("roads/streets", t3, md2);

        NodeRef r2 = tree("buildings", t4, ObjectId.NULL);
        NodeRef r21 = tree("buildings/stores", t5, md3);
        NodeRef r22 = tree("buildings/unknown", t6, md4);

        ImmutableList<NodeRef> refs = ImmutableList.of(r1, r11, r12, r2, r21, r22);
        root = MutableTree.createFromRefs(id("abc"), refs.iterator());
        assertNotNull(root);
    }

    @Test
    public void nullNameArg() {
        // ex.expect(NullPointerException.class);
        // MutableTree.createFromPaths(rootId, entries)
    }

    @Test
    public void testRoot() {
        assertNode(root, id("abc"), null, ROOT);
    }

    @Test
    public void testGetChild() {
        assertNode(root.getChild("roads"), id("a1"), null, "roads");
        assertNode(root.getChild("roads/highways"), id("a2"), id("d1"), "highways");
        assertNode(root.getChild("roads/streets"), id("a3"), id("d2"), "streets");

        assertNode(root.getChild("roads").getChild("highways"), id("a2"), id("d1"), "highways");

        assertNode(root.getChild("buildings"), id("a4"), null, "buildings");
        assertNode(root.getChild("buildings/stores"), id("a5"), id("d3"), "stores");
        assertNode(root.getChild("buildings/unknown"), id("a6"), id("d4"), "unknown");

        assertNode(root.getChild("buildings").getChild("unknown"), id("a6"), id("d4"), "unknown");
    }

    @Test
    public void testRemoveLeafRoot() {
        assertNotNull(root.getChild("roads"));
        assertNull(root.removeChild("nonExistent"));
        assertNotNull(root.removeChild("roads"));
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("No child named roads exists");
        root.getChild("roads");
    }

    @Test
    public void testRemoveNested() {
        assertNotNull(root.getChild("roads/highways"));
        assertNotNull(root.getChild("roads/streets"));

        assertNull(root.removeChild("nonExistent"));
        assertNotNull(root.removeChild("roads/streets"));

        assertNotNull(root.getChild("roads"));
        assertNotNull(root.getChild("roads/highways"));

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("No child named streets exists");
        root.getChild("roads/streets");

    }

    @Test
    public void testSet() {
        Node node = treeNode("roads", id("a11"), id("d1"));
        root.setChild("", node);
        assertEquals(node, root.getChild("roads").getNode());

        node = treeNode("stores", id("a51"), id("d3"));
        root.setChild("buildings", node);
        assertEquals(node, root.getChild("buildings/stores").getNode());
    }

    @Test
    public void testGetChildPreconditions() {
        try {
            assertNode(root.getChild(""), NULL, null, ROOT);
            fail("expected IAE on empty path");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        try {
            assertNull(root.getChild("notAChild"));
            fail("expected IAE on invalid child name");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            assertNull(root.getChild("not/a/child"));
            fail("expected IAE on invalid child name");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    private void assertNode(MutableTree mutableTree, ObjectId treeId, @Nullable ObjectId metadtaId,
            String nodeName) {

        assertNotNull(mutableTree);
        Node node = mutableTree.getNode();
        assertNotNull(node);

        assertEquals(treeId, node.getObjectId());
        if (metadtaId == null) {
            assertFalse(node.getMetadataId().isPresent());
        } else {
            assertEquals(metadtaId, node.getMetadataId().get());
        }
        assertEquals(nodeName, node.getName());
        assertEquals(TYPE.TREE, node.getType());

    }

    private NodeRef tree(String path, ObjectId treeId, ObjectId metadataId) {

        String parentPath = NodeRef.parentPath(path);
        String name = NodeRef.nodeFromPath(path);

        Node node = treeNode(name, treeId, metadataId);

        return new NodeRef(node, parentPath, ObjectId.NULL);
    }

    private Node treeNode(String name, ObjectId treeId, ObjectId metadataId) {
        Node node = RevObjectFactory.defaultInstance().createNode(name, treeId, metadataId,
                TYPE.TREE, null, null);
        return node;
    }

    private static ObjectId id(String hash) {
        hash = Strings.padEnd(hash, 2 * ObjectId.NUM_BYTES, '0');
        return ObjectId.valueOf(hash);
    }
}
