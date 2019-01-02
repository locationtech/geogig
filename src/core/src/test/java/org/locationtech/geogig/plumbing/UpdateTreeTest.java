/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static org.locationtech.geogig.model.impl.RevObjectTestSupport.hashString;

import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.impl.DepthSearch;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 *
 */
public class UpdateTreeTest extends RepositoryTestCase {

    private Context context;

    private ObjectDatabase odb;

    @Override
    protected void setUpInternal() throws Exception {
        context = super.repo.context();
        odb = context.objectDatabase();
    }

    @Test
    public void testSimple() {
        RevTree tree = createTree("blob");
        NodeRef child = NodeRef.tree("subtree", tree.getId(), ObjectId.NULL);
        RevTree newRoot = context.command(UpdateTree.class).setRoot(RevTree.EMPTY).setChild(child)
                .call();
        assertTrue(odb.exists(newRoot.getId()));

        Optional<NodeRef> ref = new DepthSearch(odb).find(newRoot.getId(), "subtree");
        assertTrue(ref.isPresent());
        assertEquals(child, ref.get());
    }

    private RevTree createTree(String... blobs) {
        RevTreeBuilder builder = RevTreeBuilder.builder(odb);
        for (String blob : blobs) {
            builder.put(blob(blob));
        }
        RevTree tree = builder.build();
        return tree;
    }

    @Test
    public void testSingleLevel() {

        RevTree tree = createTree("blob");

        NodeRef level1 = NodeRef.tree("level1", tree.getId(), hashString("fake"));
        RevTree newRoot = context.command(UpdateTree.class).setRoot(RevTree.EMPTY).setChild(level1)
                .call();
        assertTrue(odb.exists(newRoot.getId()));

        // created the intermediate tree node?
        Optional<NodeRef> ref;
        DepthSearch depthSearch = new DepthSearch(odb);
        ref = depthSearch.find(newRoot, "level1");
        assertTrue(ref.isPresent());

        ref = depthSearch.find(newRoot, "level1/blob");
        assertTrue(ref.isPresent());
    }

    @Test
    public void testSingleNested() {

        RevTree tree = createTree("blob");

        NodeRef level2 = NodeRef.tree("level1/level2", tree.getId(), hashString("fake"));

        RevTree newRoot = context.command(UpdateTree.class).setRoot(RevTree.EMPTY).setChild(level2)
                .call();

        // created the intermediate tree node?
        Optional<NodeRef> ref;
        DepthSearch depthSearch = new DepthSearch(odb);
        ref = depthSearch.find(newRoot, "level1");
        assertTrue(ref.isPresent());

        ref = depthSearch.find(newRoot, "level1/level2");
        assertTrue(ref.isPresent());

        ref = depthSearch.find(newRoot, "level1/level2/blob");
        assertTrue(ref.isPresent());
    }

    @Test
    public void testSiblingsSingleLevel() {

        RevTree tree1 = createTree("blob");
        RevTree tree2 = createTree("blob");

        NodeRef subtree1 = NodeRef.tree("subtree1", tree1.getId(), hashString("md1"));
        RevTree newRoot1 = context.command(UpdateTree.class).setRoot(RevTree.EMPTY)
                .setChild(subtree1).call();

        NodeRef subtree2 = NodeRef.tree("subtree2", tree2.getId(), hashString("md2"));
        RevTree newRoot2 = context.command(UpdateTree.class).setRoot(newRoot1).setChild(subtree2)
                .call();

        // created the intermediate tree node?
        DepthSearch depthSearch = new DepthSearch(odb);
        assertTrue(depthSearch.find(newRoot2, "subtree1").isPresent());
        assertTrue(depthSearch.find(newRoot2, "subtree2").isPresent());
        assertTrue(depthSearch.find(newRoot2, "subtree1/blob").isPresent());
        assertTrue(depthSearch.find(newRoot2, "subtree2/blob").isPresent());
    }

    @Test
    public void testSiblingsNested() {

        RevTree tree1 = createTree("blob");
        RevTree tree2 = createTree("blob");

        Preconditions.checkState(odb.isOpen());

        NodeRef level2 = NodeRef.tree("subtree1/level2", tree1.getId(), hashString("tree1"));

        RevTree newRoot1 = context.command(UpdateTree.class).setRoot(RevTree.EMPTY).setChild(level2)
                .call();
        assertTrue(odb.exists(newRoot1.getId()));

        NodeRef level3 = NodeRef.tree("subtree2/level2/level3", tree2.getId(), hashString("tree2"));

        RevTree newRoot2 = context.command(UpdateTree.class).setRoot(newRoot1).setChild(level3)
                .call();

        // created the intermediate tree node?
        DepthSearch depthSearch = new DepthSearch(odb);
        assertTrue(depthSearch.find(newRoot2, "subtree1").isPresent());
        assertTrue(depthSearch.find(newRoot2, "subtree1/level2").isPresent());
        assertTrue(depthSearch.find(newRoot2, "subtree1/level2/blob").isPresent());

        assertTrue(depthSearch.find(newRoot2, "subtree2").isPresent());
        assertTrue(depthSearch.find(newRoot2, "subtree2/level2").isPresent());
        assertTrue(depthSearch.find(newRoot2, "subtree2/level2/level3").isPresent());
        assertTrue(depthSearch.find(newRoot2, "subtree2/level2/level3/blob").isPresent());
    }

    @Test
    public void testRemove() {

        RevTree tree1 = createTree("blob");
        RevTree tree2 = createTree("blob");

        NodeRef level2 = NodeRef.tree("subtree1/level2", tree1.getId(), hashString("tree1"));
        NodeRef level3 = NodeRef.tree("subtree2/level2/level3", tree2.getId(), hashString("tree2"));

        final RevTree root = context.command(UpdateTree.class).setRoot(RevTree.EMPTY)
                .setChild(level2).setChild(level3).call();
        assertTrue(odb.exists(root.getId()));

        // created the intermediate tree node?
        DepthSearch depthSearch = new DepthSearch(odb);
        assertTrue(depthSearch.find(root, "subtree1").isPresent());
        assertTrue(depthSearch.find(root, "subtree1/level2").isPresent());
        assertTrue(depthSearch.find(root, "subtree1/level2/blob").isPresent());
        assertTrue(depthSearch.find(root, "subtree2").isPresent());
        assertTrue(depthSearch.find(root, "subtree2/level2").isPresent());
        assertTrue(depthSearch.find(root, "subtree2/level2/level3").isPresent());
        assertTrue(depthSearch.find(root, "subtree2/level2/level3/blob").isPresent());

        // remove
        final RevTree newRoot = context.command(UpdateTree.class).setRoot(root)
                .removeChildTree("subtree2/level2").call();

        assertTrue(depthSearch.find(newRoot, "subtree1").isPresent());
        assertTrue(depthSearch.find(newRoot, "subtree1/level2").isPresent());
        assertTrue(depthSearch.find(newRoot, "subtree1/level2/blob").isPresent());
        assertTrue(depthSearch.find(newRoot, "subtree2").isPresent());
        assertFalse(depthSearch.find(newRoot, "subtree2/level2").isPresent());

        final RevTree newRoot2 = context.command(UpdateTree.class).setRoot(newRoot)
                .removeChildTree("subtree1").call();
        assertFalse(depthSearch.find(newRoot2, "subtree1").isPresent());
        assertFalse(depthSearch.find(newRoot2, "subtree1/level2").isPresent());
        assertTrue(depthSearch.find(newRoot2, "subtree2").isPresent());
        assertFalse(depthSearch.find(newRoot2, "subtree2/level2").isPresent());
    }

    @Test
    public void testPreserveMetadataId() {

        RevTree tree = createTree("blob");

        final ObjectId treeMetadataId = hashString("fakeMdId");

        NodeRef child = NodeRef.tree("level1/level2", tree.getId(), treeMetadataId);

        RevTree newRoot = context.command(UpdateTree.class).setRoot(RevTree.EMPTY).setChild(child)
                .call();

        Optional<NodeRef> ref;
        DepthSearch depthSearch = new DepthSearch(odb);
        ref = depthSearch.find(newRoot.getId(), "level1/level2");
        assertTrue(ref.isPresent());
        assertTrue(ref.get().getNode().getMetadataId().isPresent());
        assertFalse(ref.get().getNode().getMetadataId().get().isNull());
        assertEquals(treeMetadataId, ref.get().getNode().getMetadataId().get());
    }

    private Node blob(String path) {
        return RevObjectFactory.defaultInstance().createNode(path, hashString(path), ObjectId.NULL,
                TYPE.FEATURE, null, null);
    }

}
