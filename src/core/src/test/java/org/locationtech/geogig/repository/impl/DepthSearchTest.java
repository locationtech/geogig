/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.model.ObjectId.NULL;
import static org.locationtech.geogig.model.RevObject.TYPE.FEATURE;
import static org.locationtech.geogig.model.RevObject.TYPE.TREE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.di.HintsModule;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.UpdateTree;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.test.MemoryModule;
import org.locationtech.geogig.test.TestPlatform;

import com.google.common.base.Optional;
import com.google.inject.Guice;
import com.google.inject.util.Modules;

/**
 *
 */
public class DepthSearchTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    private GeoGIG fakeGeogig;

    private ObjectDatabase odb;

    private DepthSearch search;

    private ObjectId rootTreeId;

    private ObjectId fakeTreeMetadataId = RevObjectTestSupport.hashString("fakeMdId");

    @Before
    public void setUp() throws IOException {
        File envHome = tempFolder.getRoot();
        Platform testPlatform = new TestPlatform(envHome);
        Context injector = Guice
                .createInjector(Modules.override(new GeogigModule()).with(new MemoryModule(),
                        new HintsModule(new Hints().platform(testPlatform))))
                .getInstance(Context.class);

        fakeGeogig = new GeoGIG(injector);
        Repository fakeRepo = fakeGeogig.getOrCreateRepository();
        odb = fakeRepo.objectDatabase();
        search = new DepthSearch(odb);

        RevTree root = RevTree.EMPTY;
        root = addTree(root, "path/to/tree1", "node11", "node12", "node13");
        root = addTree(root, "path/to/tree2", "node21", "node22", "node23");
        root = addTree(root, "tree3", "node31", "node32", "node33");

        rootTreeId = root.getId();
    }

    private RevTree addTree(RevTree root, final String treePath, String... singleNodeNames) {

        Context mockInjector = mock(Context.class);
        when(mockInjector.objectDatabase()).thenReturn(odb);
        RevTreeBuilder subTreeBuilder = CanonicalTreeBuilder.create(mockInjector.objectDatabase());

        if (singleNodeNames != null) {
            for (String singleNodeName : singleNodeNames) {
                String nodePath = NodeRef.appendChild(treePath, singleNodeName);
                ObjectId fakeFeatureOId = RevObjectTestSupport.hashString(nodePath);
                ObjectId fakeTypeOId = ObjectId.NULL;// forString(treePath);
                subTreeBuilder.put(RevObjectFactory.defaultInstance().createNode(singleNodeName,
                        fakeFeatureOId, fakeTypeOId, TYPE.FEATURE, null, null));
            }
        }

        RevTree subtree = subTreeBuilder.build();
        NodeRef childTreeNode = NodeRef.tree(treePath, subtree.getId(), fakeTreeMetadataId);
        RevTree newRoot = fakeGeogig.command(UpdateTree.class).setRoot(root).setChild(childTreeNode)
                .call();
        return newRoot;
    }

    @Test
    public void testFindFromRoot() {
        final ObjectId mdId = fakeTreeMetadataId;

        assertNode(find(rootTreeId, "path"), TREE, NULL, "path");
        assertNode(find(rootTreeId, "path/to"), TREE, NULL, "path/to");
        assertNode(find(rootTreeId, "path/to/tree1"), TREE, mdId, "path/to/tree1");
        assertNode(find(rootTreeId, "path/to/tree1/node11"), FEATURE, fakeTreeMetadataId,
                "path/to/tree1/node11");
        assertNode(find(rootTreeId, "path/to/tree1/node12"), FEATURE, fakeTreeMetadataId,
                "path/to/tree1/node12");
        assertNode(find(rootTreeId, "path/to/tree1/node13"), FEATURE, fakeTreeMetadataId,
                "path/to/tree1/node13");
        assertFalse(find(rootTreeId, "path/to/tree1/node14").isPresent());

        assertNode(find(rootTreeId, "path/to/tree2"), TREE, mdId, "path/to/tree2");
        assertNode(find(rootTreeId, "path/to/tree2/node21"), FEATURE, mdId, "path/to/tree2/node21");
        assertNode(find(rootTreeId, "path/to/tree2/node22"), FEATURE, mdId, "path/to/tree2/node22");
        assertNode(find(rootTreeId, "path/to/tree2/node23"), FEATURE, mdId, "path/to/tree2/node23");
        assertFalse(find(rootTreeId, "path/to/tree2/node24").isPresent());

        assertNode(find(rootTreeId, "tree3"), TYPE.TREE, mdId, "tree3");
        assertNode(find(rootTreeId, "tree3/node31"), FEATURE, mdId, "tree3/node31");
        assertNode(find(rootTreeId, "tree3/node32"), FEATURE, mdId, "tree3/node32");
        assertNode(find(rootTreeId, "tree3/node33"), FEATURE, mdId, "tree3/node33");
        assertFalse(find(rootTreeId, "tree3/node34").isPresent());

        assertFalse(find(rootTreeId, "tree4").isPresent());

        try {
            find(rootTreeId, "");
            fail("expected IAE on empty child path");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("empty child path"));
        }

        try {
            find(rootTreeId, "/");
            fail("expected IAE on empty child path");
        } catch (IllegalArgumentException expected) {
            assertTrue(true);
        }
    }

    private Optional<NodeRef> find(ObjectId rootTreeId, String rootChildPath) {
        return search.find(rootTreeId, rootChildPath);
    }

    private void assertNode(Optional<NodeRef> ref, TYPE type, ObjectId expectedMdId, String path) {
        assertTrue(ref.isPresent());
        assertEquals(type, ref.get().getType());
        assertEquals(path, ref.get().path());
        assertEquals(expectedMdId, ref.get().getMetadataId());
    }
}
