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

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;

import com.google.common.base.Strings;

public class TreeDifferenceTest extends Assert {

    /**
     * Tree made of the following nodes "<path> -> <treeId> (<metadataId>)"
     * 
     * <pre>
     * <code>
     * roads                    ->      a1      (null)
     * roads/highways           ->      a2      (d1)
     * roads/streets            ->      a3      (d2)
     * buildings                ->      a4      (null)
     * buildings/stores         ->      a5      (d3)
     * buildings/unknown        ->      a6      (d4)
     * buildings/towers         ->      a7      (d5)    //deleted in rightTree 
     * </code>
     * </pre>
     */
    private MutableTree leftTree;

    /**
     * Tree made of the following nodes "<path> -> <treeId> (<metadataId>)"
     * 
     * <pre>
     * <code>
     * roads                    ->      a11     (null)  // id changed
     * roads/highways           ->      a2      (d1)    // not changed
     * roads/streetsRenamed     ->      a3      (d2)    // renamed, same id and mdid
     * buildings                ->      a41     (null)  // id changed  
     * buildings/stores         ->      a5      (d31)   // only metadataId changed 
     * buildings/knownUnknown   ->      a61     (d41)   // renamed, changed, and new metadataid. Counts as a whole new tree.
     * admin                    ->      c1      (d5)    // new tree
     * admin/area               ->      c2      (d6)    // new tree
     * admin/line               ->      c3      (d7)    // new tree
     * </code>
     * </pre>
     */
    private MutableTree rightTree;

    private TreeDifference treeDifference;

    @Before
    public void setUp() {
        leftTree = MutableTree.createFromRefs(id("abc"), //
                tree("roads", "a1", null), //
                tree("roads/highways", "a2", "d1"), //
                tree("roads/streets", "a3", "d2"), //
                tree("buildings", "a4", null), //
                tree("buildings/stores", "a5", "d3"), //
                tree("buildings/unknown", "a6", "d4"), //
                tree("buildings/towers", "a7", "d5")//
        );
        rightTree = MutableTree.createFromRefs(id("abc2"), //
                tree("roads", "a11", null), //
                tree("roads/highways", "a2", "d1"), //
                tree("roads/streetsRenamed", "a3", "d2"), //
                tree("buildings", "a41", null), //
                tree("buildings/stores", "a5", "d31"), //
                tree("buildings/knownUnknown", "a61", "d41"), //
                tree("admin", "c1", "d5"), //
                tree("admin/area", "c2", "d6"), //
                tree("admin/line", "c3", "d7")//
        );
        treeDifference = TreeDifference.create(leftTree, rightTree);
    }

    @Test
    public void testCreate() {
        assertNotNull(treeDifference);
        assertSame(leftTree, treeDifference.getLeftTree());
        assertSame(rightTree, treeDifference.getRightTree());
    }

    @Test
    public void testInverse() {
        TreeDifference inverse = treeDifference.inverse();
        assertNotNull(inverse);
        assertNotSame(treeDifference, inverse);
        assertSame(leftTree, inverse.getRightTree());
        assertSame(rightTree, inverse.getLeftTree());
    }

    /**
     * Only {@code roads/streetsRenamed} is purely a rename. {@code buildings/unknown} is renamed to
     * {@code buildings/kownUnknown} but it also points to a different tree, so there's no way to
     * know it was renamed
     */
    @Test
    public void testFindRenames() {
        Map<NodeRef, NodeRef> renames = treeDifference.findRenames();
        assertNotNull(renames);
        assertEquals(1, renames.size());

        Entry<NodeRef, NodeRef> e = renames.entrySet().iterator().next();
        NodeRef oldRef = e.getKey();
        NodeRef newRef = e.getValue();
        assertEquals(tree("roads/streets", "a3", "d2"), oldRef);
        assertEquals(tree("roads/streetsRenamed", "a3", "d2"), newRef);
    }

    @Test
    public void testFindNewTrees() {

        Set<NodeRef> newTrees = treeDifference.findNewTrees();
        // buildings/knownUnknown, admin, admin/area, admin/line
        assertEquals(4, newTrees.size());
        assertTrue(newTrees.contains(tree("buildings/knownUnknown", "a61", "d41")));
        assertTrue(newTrees.contains(tree("admin", "c1", "d5")));
        assertTrue(newTrees.contains(tree("admin/area", "c2", "d6")));
        assertTrue(newTrees.contains(tree("admin/line", "c3", "d7")));
    }

    @Test
    public void testFindDeletes() {
        Set<NodeRef> deletes = treeDifference.findDeletes();
        // buildings/towers, buildings/unknown
        assertEquals(2, deletes.size());
        assertTrue(deletes.contains(tree("buildings/towers", "a7", "d5")));
        assertTrue(deletes.contains(tree("buildings/unknown", "a6", "d4")));
    }

    @Test
    public void testFindChanges() {
        Map<NodeRef, NodeRef> changes = treeDifference.findChanges();
        assertNotNull(changes);
        assertEquals(changes.toString(), 2, changes.size());
        NodeRef l1 = tree("roads", "a1", null);
        NodeRef r1 = tree("roads", "a11", null);
        NodeRef l2 = tree("buildings", "a4", null);
        NodeRef r2 = tree("buildings", "a41", null);
        assertEquals(r1, changes.get(l1));
        assertEquals(r2, changes.get(l2));
    }

    @Test
    public void testFindPureMetadataChanges() {
        // buildings/stores -> a5 (d31) // only metadataId changed
        Map<NodeRef, NodeRef> metadataChanges = treeDifference.findPureMetadataChanges();
        assertNotNull(metadataChanges);
        assertEquals(1, metadataChanges.size());

        NodeRef left = tree("buildings/stores", "a5", "d3");
        NodeRef right = tree("buildings/stores", "a5", "d31");
        assertEquals(right, metadataChanges.get(left));
    }

    private NodeRef tree(String path, String treeId, @Nullable String metadataId) {
        ObjectId mdId = metadataId == null ? null : id(metadataId);
        return tree(path, id(treeId), mdId);
    }

    private NodeRef tree(String path, ObjectId treeId, @Nullable ObjectId metadataId) {

        String parentPath = NodeRef.parentPath(path);
        String name = NodeRef.nodeFromPath(path);

        Node node = treeNode(name, treeId, metadataId);

        return new NodeRef(node, parentPath, ObjectId.NULL);
    }

    private Node treeNode(String name, ObjectId treeId, @Nullable ObjectId metadataId) {
        if (metadataId == null) {
            metadataId = ObjectId.NULL;
        }
        Node node = RevObjectFactory.defaultInstance().createNode(name, treeId, metadataId,
                TYPE.TREE, null, null);
        return node;
    }

    private static ObjectId id(String hash) {
        hash = Strings.padEnd(hash, 2 * ObjectId.NUM_BYTES, '0');
        return ObjectId.valueOf(hash);
    }
}
