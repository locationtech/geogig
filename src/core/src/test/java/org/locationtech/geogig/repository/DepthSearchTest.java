/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.api.ObjectId.NULL;
import static org.locationtech.geogig.api.RevObject.TYPE.FEATURE;
import static org.locationtech.geogig.api.RevObject.TYPE.TREE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.MemoryModule;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.api.plumbing.CreateTree;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.WriteBack;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.storage.ObjectDatabase;

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

    private ObjectId fakeTreeMetadataId = ObjectId.forString("fakeMdId");

    @Before
    public void setUp() throws IOException {
        File envHome = tempFolder.getRoot();
        Platform testPlatform = new TestPlatform(envHome);
        Context injector = Guice.createInjector(Modules.override(new GeogigModule()).with(
                new MemoryModule(testPlatform))).getInstance(Context.class);

        fakeGeogig = new GeoGIG(injector);
        Repository fakeRepo = fakeGeogig.getOrCreateRepository();
        odb = fakeRepo.objectDatabase();
        search = new DepthSearch(odb);

        RevTreeBuilder root = new RevTreeBuilder(odb);
        root = addTree(root, "path/to/tree1", "node11", "node12", "node13");
        root = addTree(root, "path/to/tree2", "node21", "node22", "node23");
        root = addTree(root, "tree3", "node31", "node32", "node33");
        RevTree rootTree = root.build();
        odb.put(rootTree);
        rootTreeId = rootTree.getId();
    }

    private RevTreeBuilder addTree(RevTreeBuilder root, final String treePath,
            String... singleNodeNames) {

        Context mockInjector = mock(Context.class);
        when(mockInjector.objectDatabase()).thenReturn(odb);
        CreateTree op = new CreateTree().setIndex(false);
        op.setContext(mockInjector);
        RevTreeBuilder subTreeBuilder =op.call();
        
        if (singleNodeNames != null) {
            for (String singleNodeName : singleNodeNames) {
                String nodePath = NodeRef.appendChild(treePath, singleNodeName);
                ObjectId fakeFeatureOId = ObjectId.forString(nodePath);
                ObjectId fakeTypeOId = ObjectId.NULL;// forString(treePath);
                subTreeBuilder.put(Node.create(singleNodeName, fakeFeatureOId, fakeTypeOId,
                        TYPE.FEATURE, null));
            }
        }

        RevTree subtree = subTreeBuilder.build();
        WriteBack writeBack = fakeGeogig.command(WriteBack.class).setAncestor(root)
                .setChildPath(treePath).setTree(subtree).setMetadataId(fakeTreeMetadataId);
        ObjectId newRootId = writeBack.call();

        return fakeGeogig.command(RevObjectParse.class).setObjectId(newRootId).call(RevTree.class)
                .get().builder(odb);
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
