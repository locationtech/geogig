/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.diff;

import static org.locationtech.geogig.api.NodeRef.ROOT;
import static org.locationtech.geogig.api.ObjectId.NULL;

import java.util.Iterator;

import javax.annotation.Nullable;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.diff.DepthTreeIterator.Strategy;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectDatabse;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class MutableTreeTest extends Assert {

    MutableTree root;

    @Before
    public void setUp() {
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
    public void testRoot() {
        assertNode(root, id("abc"), null, ROOT);
    }

    @Test
    @Ignore
    public void testBuild() {
        ObjectDatabase origin = new HeapObjectDatabse();
        origin.open();
        ObjectDatabase target = new HeapObjectDatabse();
        target.open();
        RevTree tree = root.build(origin, target);

        Iterator<NodeRef> treeRefs = new DepthTreeIterator("", ObjectId.NULL, tree, target,
                Strategy.RECURSIVE_TREES_ONLY);
        MutableTree createFromRefs = MutableTree.createFromRefs(root.getNode().getObjectId(),
                treeRefs);
        // TODO finish
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
        Node node = Node.create(name, treeId, metadataId, TYPE.TREE, null);
        return node;
    }

    private static ObjectId id(String hash) {
        hash = Strings.padEnd(hash, 2 * ObjectId.NUM_BYTES, '0');
        return ObjectId.valueOf(hash);
    }
}
