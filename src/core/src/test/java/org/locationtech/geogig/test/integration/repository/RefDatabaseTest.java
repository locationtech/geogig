/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.test.TestPlatform;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;

public abstract class RefDatabaseTest {

    protected RefDatabase refDb;

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    private static final ObjectId sampleId = RevObjectTestSupport.hashString("some random string");

    @Before
    public void setUp() throws Exception {
        File repoDir = tmpFolder.newFolder("repo", ".geogig");
        File workingDirectory = repoDir.getParentFile();
        File userHomeDirectory = tmpFolder.newFolder("home");
        Platform platform = new TestPlatform(workingDirectory, userHomeDirectory);
        refDb = createDatabase(platform);
        refDb.create();
    }

    @After
    public void tearDown() throws Exception {
        refDb.close();
    }

    protected abstract RefDatabase createDatabase(Platform platform) throws Exception;

    @Test
    public void testEmpty() {
        refDb.putRef(Ref.MASTER, ObjectId.NULL.toString());
        refDb.putSymRef(Ref.HEAD, Ref.MASTER);
        assertEquals(ObjectId.NULL.toString(), refDb.getRef(Ref.MASTER));
        assertEquals(Ref.MASTER, refDb.getSymRef(Ref.HEAD));
    }

    @Test
    public void testPutGetRef() {
        byte[] raw = new byte[20];
        Arrays.fill(raw, (byte) 1);
        ObjectId oid = new ObjectId(raw);

        String value = refDb.getRef(Ref.MASTER);
        assertNull(value + " is not null", value);

        refDb.putRef(Ref.MASTER, oid.toString());
        assertEquals(oid.toString(), refDb.getRef(Ref.MASTER));

        refDb.putRef(Ref.WORK_HEAD, sampleId.toString());
        assertEquals(sampleId.toString(), refDb.getRef(Ref.WORK_HEAD));
    }

    @Test
    public void testGetSymRefWhenRef() {
        refDb.putRef(Ref.MASTER, ObjectId.NULL.toString());
        expected.expect(IllegalArgumentException.class);
        expected.expectMessage(Ref.MASTER + " is not a symbolic ref");
        refDb.getSymRef(Ref.MASTER);
    }

    @Test
    public void testPutGetSymRef() {

        String branch = "refs/heads/branch";

        assertNull(Ref.MASTER, refDb.getSymRef(Ref.HEAD));

        refDb.putSymRef(Ref.HEAD, branch);

        assertEquals(branch, refDb.getSymRef(Ref.HEAD));
    }

    @Test
    public void testRemove() {
        final String origin = Ref.append(Ref.ORIGIN, "master");
        refDb.putRef(origin, sampleId.toString());
        refDb.putSymRef(Ref.HEAD, origin);

        assertEquals(sampleId.toString(), refDb.getRef(origin));
        assertEquals(origin, refDb.getSymRef(Ref.HEAD));

        assertEquals(sampleId.toString(), refDb.remove(origin));
        assertNull(refDb.getRef(origin));
        assertNull(refDb.getSymRef(origin));

        assertEquals(origin, refDb.remove(Ref.HEAD));
        assertNull(refDb.getSymRef(Ref.HEAD));
        assertNull(refDb.getRef(Ref.HEAD));
    }

    @Test
    public void testGetAll() {
        Map<String, String> allrefs = createTestRefs();
        Map<String, String> allOnNullNamespace = refDb.getAll();
        for (Map.Entry<String, String> e : allrefs.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (key.startsWith(Ref.append(Ref.TRANSACTIONS_PREFIX, "txnamespace"))) {
                // createRefs added txnamespace1 and txnamespace2
                assertFalse(
                        key + " is in a transaction namespace, "
                                + "shall not be returned by getAll()",
                        allOnNullNamespace.containsKey(key));
            } else {
                assertTrue(key + " not found", allOnNullNamespace.containsKey(key));
                assertEquals(value, allOnNullNamespace.get(key));
            }
        }
    }

    @Test
    public void testGetAllNullNamespace() {
        expected.expect(NullPointerException.class);
        expected.expectMessage("namespace can't be null");
        refDb.getAll(null);
    }

    @Test
    public void testGetAllNonExistentNamespace() {
        Map<String, String> all;
        all = refDb.getAll(Ref.append(Ref.TRANSACTIONS_PREFIX, "nonexistentns"));
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    @Test
    public void testGetAllNamespace() {
        final String txNamespace1 = Ref.append(Ref.TRANSACTIONS_PREFIX, "txnamespace1");
        final String txNamespace2 = Ref.append(Ref.TRANSACTIONS_PREFIX, "txnamespace2");

        Map<String, String> allrefs = createTestRefs();
        Map<String, String> allOnNamespace;
        allOnNamespace = refDb.getAll(txNamespace1);
        assertNamespace(txNamespace1, allrefs, allOnNamespace);

        allOnNamespace = refDb.getAll(txNamespace2);
        assertNamespace(txNamespace2, allrefs, allOnNamespace);
    }

    private void assertNamespace(String namespace, Map<String, String> allrefs,
            Map<String, String> allOnNamespace) {

        for (Map.Entry<String, String> e : allrefs.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (key.startsWith(namespace)) {
                // createRefs added txnamespace1 and txnamespace2
                assertTrue(allOnNamespace.containsKey(key));
                assertEquals(value, allOnNamespace.get(key));
            } else {
                assertFalse(
                        key + " is NOT in a transaction namespace " + namespace
                                + ", shall not be returned by getAll(String namespace)",
                        allOnNamespace.containsKey(key));
            }
        }
    }

    @Test
    public void testRemoveAllNullNamespace() {
        expected.expect(NullPointerException.class);
        expected.expectMessage("provided namespace is null");
        refDb.removeAll(null);
    }

    @Test
    public void testRemoveAllNamespace() {
        final String txNamespace1 = Ref.append(Ref.TRANSACTIONS_PREFIX, "txnamespace1");
        final String txNamespace2 = Ref.append(Ref.TRANSACTIONS_PREFIX, "txnamespace2");
        final Map<String, String> allrefs = createTestRefs();

        Map<String, String> removed;

        removed = refDb.removeAll(txNamespace1);
        assertEquals(Maps.filterKeys(allrefs, new Predicate<String>() {
            @Override
            public boolean apply(String key) {
                return key.startsWith(txNamespace1);
            }
        }), removed);

        removed = refDb.removeAll(txNamespace2);
        assertEquals(Maps.filterKeys(allrefs, new Predicate<String>() {
            @Override
            public boolean apply(String key) {
                return key.startsWith(txNamespace2);
            }
        }), removed);
    }

    private Map<String, String> createTestRefs() {
        Map<String, String> refs = new TreeMap<String, String>();

        // known root refs
        putRef(Ref.CHERRY_PICK_HEAD, sampleId.toString(), refs);
        putRef(Ref.ORIG_HEAD, sampleId.toString(), refs);
        putSymRef(Ref.HEAD, "refs/heads/master", refs);
        putRef(Ref.WORK_HEAD, sampleId.toString(), refs);
        putRef(Ref.STAGE_HEAD, sampleId.toString(), refs);
        putRef(Ref.MERGE_HEAD, sampleId.toString(), refs);

        // some heads
        String branch1 = Ref.append(Ref.HEADS_PREFIX, "branch1");
        String tag1 = Ref.append(Ref.TAGS_PREFIX, "tag1");
        String remoteBranch1 = Ref.append(Ref.append(Ref.REMOTES_PREFIX, "r1"), "master");

        putRef(branch1, id("branch1"), refs);
        putRef(tag1, id("tag1"), refs);
        putRef(remoteBranch1, id("r1master"), refs);

        // some refs in a transaction namespace
        String txNamespace1 = Ref.append(Ref.TRANSACTIONS_PREFIX, "txnamespace1");
        String txNamespace2 = Ref.append(Ref.TRANSACTIONS_PREFIX, "txnamespace2");

        String tx1Head = Ref.append(txNamespace1, Ref.HEAD);
        String tx1Master = Ref.append(txNamespace1, Ref.MASTER);
        putRef(tx1Head, id("tx1Head"), refs);
        putRef(tx1Master, id("tx1Master"), refs);

        String tx2Head = Ref.append(txNamespace2, Ref.HEAD);
        String tx2Master = Ref.append(txNamespace2, Ref.MASTER);
        putRef(tx2Head, id("tx2Head"), refs);
        putRef(tx2Master, id("tx2Master"), refs);
        return refs;
    }

    private String id(String string) {
        return RevObjectTestSupport.hashString(string).toString();
    }

    private void putRef(String name, String value, Map<String, String> holder) {
        refDb.putRef(name, value);
        holder.put(name, value);
    }

    private void putSymRef(String name, String value, Map<String, String> holder) {
        refDb.putSymRef(name, value);
        holder.put(name, value);
    }
}