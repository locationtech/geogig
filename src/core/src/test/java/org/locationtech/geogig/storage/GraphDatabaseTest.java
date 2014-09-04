/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.TestPlatform;

import com.google.common.collect.ImmutableList;

/**
 * Abstract test suite for {@link GraphDatabase} implementations.
 * <p>
 * Create a concrete subclass of this test suite and implement {@link #createInjector()} so that
 * {@code GraphDtabase.class} is bound to your implementation instance as a singleton.
 */
public abstract class GraphDatabaseTest {

    protected GraphDatabase database;

    protected TestPlatform platform;

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        File root = tmpFolder.getRoot();
        tmpFolder.newFolder(".geogig");
        platform = new TestPlatform(root);
        platform.setUserHome(tmpFolder.newFolder("fake_home"));
        database = createDatabase(platform);
        database.open();
    }

    @After
    public void tearDown() throws Exception {
        if (database != null) {
            database.close();
        }
    }

    protected abstract GraphDatabase createDatabase(Platform platform) throws Exception;

    @Test
    public void testNodes() throws IOException {
        ObjectId rootId = ObjectId.forString("root commit");
        ImmutableList<ObjectId> parents = ImmutableList.of();
        database.put(rootId, parents);
        ObjectId commit1 = ObjectId.forString("commit1");
        parents = ImmutableList.of(rootId);
        database.put(commit1, parents);
        ObjectId commit2 = ObjectId.forString("commit2");
        parents = ImmutableList.of(commit1);
        database.put(commit2, parents);

        ImmutableList<ObjectId> children = database.getChildren(commit2);
        parents = database.getParents(commit2);
        assertTrue(database.exists(commit2));
        assertEquals("Size of " + children, 0, children.size());
        assertEquals(1, parents.size());
        assertEquals(commit1, parents.get(0));
        children = database.getChildren(commit1);
        parents = database.getParents(commit1);
        assertTrue(database.exists(commit1));
        assertEquals(1, children.size());
        assertEquals(commit2, children.get(0));
        assertEquals(1, parents.size());
        assertEquals(rootId, parents.get(0));
        children = database.getChildren(rootId);
        parents = database.getParents(rootId);
        assertTrue(database.exists(rootId));
        assertEquals(1, children.size());
        assertEquals(commit1, children.get(0));
        assertEquals(0, parents.size());
    }

    @Test
    public void testMapNode() throws IOException {
        ObjectId commitId = ObjectId.forString("commitId");
        ObjectId mappedId = ObjectId.forString("mapped");
        database.put(commitId, new ImmutableList.Builder<ObjectId>().build());
        database.put(mappedId, new ImmutableList.Builder<ObjectId>().build());
        database.map(mappedId, commitId);
        ObjectId mapping = database.getMapping(mappedId);
        assertEquals(commitId + " : " + mappedId + " : " + mapping, commitId, mapping);

        // update mapping
        ObjectId commitId2 = ObjectId.forString("commitId2");
        database.map(mappedId, commitId2);
        mapping = database.getMapping(mappedId);
        assertEquals(commitId2 + " : " + mappedId + " : " + mapping, commitId2, mapping);
    }

    @Test
    public void testDepth() throws IOException {
        // Create the following revision graph
        // x o - root commit
        // | |\
        // | | o - commit1
        // | | |
        // | | o - commit2
        // | | |\
        // | | | o - commit3
        // | | | |\
        // | | | | o - commit4
        // | | | | |
        // | | | o | - commit5
        // | | | |/
        // | | | o - commit6
        // | | |
        // | o | - commit7
        // | | |
        // | | o - commit8
        // | |/
        // | o - commit9
        // |
        // o - commit10
        // |
        // o - commit11
        ObjectId rootId = ObjectId.forString("root commit");
        ImmutableList<ObjectId> parents = ImmutableList.of();
        database.put(rootId, parents);
        ObjectId commit1 = ObjectId.forString("commit1");
        parents = ImmutableList.of(rootId);
        database.put(commit1, parents);
        ObjectId commit2 = ObjectId.forString("commit2");
        parents = ImmutableList.of(commit1);
        database.put(commit2, parents);
        ObjectId commit3 = ObjectId.forString("commit3");
        parents = ImmutableList.of(commit2);
        database.put(commit3, parents);
        ObjectId commit4 = ObjectId.forString("commit4");
        parents = ImmutableList.of(commit3);
        database.put(commit4, parents);
        ObjectId commit5 = ObjectId.forString("commit5");
        parents = ImmutableList.of(commit3);
        database.put(commit5, parents);
        ObjectId commit6 = ObjectId.forString("commit6");
        parents = ImmutableList.of(commit5, commit4);
        database.put(commit6, parents);
        ObjectId commit7 = ObjectId.forString("commit7");
        parents = ImmutableList.of(rootId);
        database.put(commit7, parents);
        ObjectId commit8 = ObjectId.forString("commit8");
        parents = ImmutableList.of(commit2);
        database.put(commit8, parents);
        ObjectId commit9 = ObjectId.forString("commit9");
        parents = ImmutableList.of(commit7, commit8);
        database.put(commit9, parents);
        ObjectId commit10 = ObjectId.forString("commit10");
        parents = ImmutableList.of();
        database.put(commit10, parents);
        ObjectId commit11 = ObjectId.forString("commit11");
        parents = ImmutableList.of(commit10);
        database.put(commit11, parents);

        System.out.println("Testing depth");
        assertEquals(0, database.getDepth(rootId));
        System.out.println("Testing depth 9");
        assertEquals(2, database.getDepth(commit9));
        System.out.println("Testing depth 8");
        assertEquals(3, database.getDepth(commit8));
        System.out.println("Testing depth 6");
        assertEquals(5, database.getDepth(commit6));
        System.out.println("Testing depth 4");
        assertEquals(4, database.getDepth(commit4));
        System.out.println("Testing depth 11");
        assertEquals(1, database.getDepth(commit11));
    }
}
