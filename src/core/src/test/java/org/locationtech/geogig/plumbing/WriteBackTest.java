/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DepthSearch;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.test.MemoryModule;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.inject.Guice;
import com.google.inject.util.Modules;

/**
 *
 */
public class WriteBackTest extends Assert {

    private WriteBack writeBack;

    ObjectDatabase odb;

    ConflictsDatabase conflicts;

    @Before
    public void setUp() {
        Context injector = Guice
                .createInjector(Modules.override(new GeogigModule()).with(new MemoryModule()))
                .getInstance(Context.class);

        odb = injector.objectDatabase();
        odb.open();
        conflicts = injector.conflictsDatabase();

        writeBack = injector.command(WriteBack.class);
    }

    @Test
    public void testSimple() {

        RevTree oldRoot = RevTreeBuilder.EMPTY;
        RevTree tree = RevTreeBuilder.canonical(odb).put(blob("blob")).build();
        ObjectId newRootId = writeBack.setAncestor(oldRoot).setChildPath("subtree").setTree(tree)
                .call();

        Optional<NodeRef> ref = new DepthSearch(odb).find(newRootId, "subtree");
        assertTrue(ref.isPresent());
    }

    @Test
    public void testSingleLevel() {

        RevTree oldRoot = RevTreeBuilder.EMPTY;

        RevTree tree = RevTreeBuilder.canonical(odb).put(blob("blob")).build();

        ObjectId newRootId = writeBack.setAncestor(oldRoot).setChildPath("level1").setTree(tree)
                .call();

        // created the intermediate tree node?
        Optional<NodeRef> ref;
        DepthSearch depthSearch = new DepthSearch(odb);
        ref = depthSearch.find(newRootId, "level1");
        assertTrue(ref.isPresent());

        ref = depthSearch.find(newRootId, "level1/blob");
        assertTrue(ref.isPresent());
    }

    @Test
    public void testSingleNested() {

        RevTree oldRoot = RevTreeBuilder.EMPTY;

        RevTree tree = RevTreeBuilder.canonical(odb).put(blob("blob")).build();

        ObjectId newRootId = writeBack.setAncestor(oldRoot).setChildPath("level1/level2")
                .setTree(tree).call();

        // created the intermediate tree node?
        Optional<NodeRef> ref;
        DepthSearch depthSearch = new DepthSearch(odb);
        ref = depthSearch.find(newRootId, "level1");
        assertTrue(ref.isPresent());

        ref = depthSearch.find(newRootId, "level1/level2");
        assertTrue(ref.isPresent());

        ref = depthSearch.find(newRootId, "level1/level2/blob");
        assertTrue(ref.isPresent());
    }

    @Test
    public void testSiblingsSingleLevel() {

        RevTree ancestor = RevTreeBuilder.EMPTY;

        RevTree tree1 = RevTreeBuilder.canonical(odb).put(blob("blob")).build();
        RevTree tree2 = RevTreeBuilder.canonical(odb).put(blob("blob")).build();

        ObjectId newRootId1 = writeBack.setAncestor(ancestor).setChildPath("subtree1")
                .setTree(tree1).call();

        ancestor = odb.getTree(newRootId1);
        ObjectId newRootId2 = writeBack.setAncestor(ancestor).setChildPath("subtree2")
                .setTree(tree2).call();

        // created the intermediate tree node?
        DepthSearch depthSearch = new DepthSearch(odb);
        assertTrue(depthSearch.find(newRootId2, "subtree1").isPresent());
        assertTrue(depthSearch.find(newRootId2, "subtree2").isPresent());
        assertTrue(depthSearch.find(newRootId2, "subtree1/blob").isPresent());
        assertTrue(depthSearch.find(newRootId2, "subtree2/blob").isPresent());
    }

    @Test
    public void testSiblingsNested() {

        RevTree tree1 = RevTreeBuilder.canonical(odb).put(blob("blob")).build();
        RevTree tree2 = RevTreeBuilder.canonical(odb).put(blob("blob")).build();

        Preconditions.checkState(odb.isOpen());
        RevTree oldRoot = RevTreeBuilder.EMPTY;
        ObjectId newRootId1 = writeBack.setAncestor(oldRoot).setChildPath("subtree1/level2")
                .setTree(tree1).call();

        Preconditions.checkState(odb.isOpen());
        RevTree newRevTree = odb.getTree(newRootId1);
        ObjectId newRootId2 = writeBack.setAncestor(newRevTree)
                .setChildPath("subtree2/level2/level3").setTree(tree2).call();

        // created the intermediate tree node?
        DepthSearch depthSearch = new DepthSearch(odb);
        assertTrue(depthSearch.find(newRootId2, "subtree1").isPresent());
        assertTrue(depthSearch.find(newRootId2, "subtree1/level2").isPresent());
        assertTrue(depthSearch.find(newRootId2, "subtree1/level2/blob").isPresent());

        assertTrue(depthSearch.find(newRootId2, "subtree2").isPresent());
        assertTrue(depthSearch.find(newRootId2, "subtree2/level2").isPresent());
        assertTrue(depthSearch.find(newRootId2, "subtree2/level2/level3").isPresent());
        assertTrue(depthSearch.find(newRootId2, "subtree2/level2/level3/blob").isPresent());
    }

    @Test
    public void testPreserveMetadataId() {

        RevTree oldRoot = RevTreeBuilder.EMPTY;

        RevTree tree = RevTreeBuilder.canonical(odb).put(blob("blob")).build();

        final ObjectId treeMetadataId = ObjectId.forString("fakeMdId");

        ObjectId newRootId = writeBack.setAncestor(oldRoot).setChildPath("level1/level2")
                .setTree(tree).setMetadataId(treeMetadataId).call();

        Optional<NodeRef> ref;
        DepthSearch depthSearch = new DepthSearch(odb);
        ref = depthSearch.find(newRootId, "level1/level2");
        assertTrue(ref.isPresent());
        assertTrue(ref.get().getNode().getMetadataId().isPresent());
        assertFalse(ref.get().getNode().getMetadataId().get().isNull());
        assertEquals(treeMetadataId, ref.get().getNode().getMetadataId().get());
    }

    private Node blob(String path) {
        return Node.create(path, ObjectId.forString(path), ObjectId.NULL, TYPE.FEATURE, null);
    }
}
