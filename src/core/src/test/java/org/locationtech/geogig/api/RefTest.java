/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

public class RefTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        injector.configDatabase().put("user.name", "groldan");
        injector.configDatabase().put("user.email", "groldan@opengeo.org");
    }

    @Test
    public void testConstructor() throws Exception {
        insertAndAdd(points1);
        RevCommit oid = geogig.command(CommitOp.class).call();

        Ref testRef = new Ref(Ref.REFS_PREFIX + "commit1", oid.getId());

        assertEquals(Ref.REFS_PREFIX + "commit1", testRef.getName());
        assertEquals(Ref.REFS_PREFIX, testRef.namespace());
        assertEquals("commit1", testRef.localName());
        assertEquals(oid.getId(), testRef.getObjectId());
    }

    @Test
    public void testToString() throws Exception {
        insertAndAdd(points1);
        RevCommit oid = geogig.command(CommitOp.class).call();

        Ref testRef = new Ref(Ref.REFS_PREFIX + "commit1", oid.getId());

        assertEquals("Ref[" + testRef.getName() + " -> " + testRef.getObjectId().toString() + "]",
                testRef.toString());
    }

    @Test
    public void testEquals() throws Exception {
        insertAndAdd(points1);
        RevCommit oid = geogig.command(CommitOp.class).call();

        Ref testRef = new Ref(Ref.REFS_PREFIX + "commit1", oid.getId());

        insertAndAdd(lines1);
        RevCommit oid2 = geogig.command(CommitOp.class).call();

        Ref testRef2 = new Ref(Ref.REFS_PREFIX + "commit2", oid2.getId());

        assertFalse(testRef.equals(testRef2));

        testRef2 = new Ref(Ref.REFS_PREFIX + "commit1", oid.getTreeId());

        assertFalse(testRef.equals(testRef2));

        assertFalse(testRef.equals("not a ref"));

        assertTrue(testRef.equals(testRef));
    }

    @Test
    public void testCompare() throws Exception {
        insertAndAdd(points1);
        RevCommit oid = geogig.command(CommitOp.class).call();

        Ref testRef = new Ref(Ref.REFS_PREFIX + "commit1", oid.getId());

        insertAndAdd(lines1);
        RevCommit oid2 = geogig.command(CommitOp.class).call();

        Ref testRef2 = new Ref(Ref.REFS_PREFIX + "commit2", oid2.getId());

        assertTrue(testRef.compareTo(testRef2) < 0);
        assertTrue(testRef2.compareTo(testRef) > 0);
        assertEquals(0, testRef.compareTo(testRef));
    }

    @Test
    public void testLocalNameAndNamespace() {
        String ref = Ref.localName(Ref.HEADS_PREFIX + "branch1");
        assertEquals("branch1", ref);

        ref = Ref.localName(Ref.REFS_PREFIX + "commit1");
        assertEquals("commit1", ref);

        ref = Ref.localName(Ref.REMOTES_PREFIX + "origin/branch1");
        assertEquals("branch1", ref);

        ref = Ref.localName(Ref.TAGS_PREFIX + "tag1");
        assertEquals("tag1", ref);

        ref = Ref.localName("ref1");
        assertEquals("ref1", ref);

        ref = Ref.namespace(Ref.HEADS_PREFIX + "branch1");
        assertEquals(Ref.HEADS_PREFIX, ref);

        ref = Ref.namespace(Ref.REFS_PREFIX + "commit1");
        assertEquals(Ref.REFS_PREFIX, ref);

        ref = Ref.namespace(Ref.REMOTES_PREFIX + "origin/branch1");
        assertEquals(Ref.REMOTES_PREFIX + "/origin", ref);

        ref = Ref.namespace(Ref.TAGS_PREFIX + "tag1");
        assertEquals(Ref.TAGS_PREFIX, ref);

        ref = Ref.namespace("ref1");
        assertEquals("ref1", ref);
    }

    @Test
    public void testAppendAndChild() {
        String ref = "ref1";
        ref = Ref.append(Ref.HEADS_PREFIX, ref);
        assertEquals(Ref.HEADS_PREFIX + "ref1", ref);
        ref = Ref.child(Ref.HEADS_PREFIX, ref);
        assertEquals("ref1", ref);

        ref = Ref.append("", ref);
        assertEquals("ref1", ref);

        ref = Ref.append(Ref.HEADS_PREFIX, ref + "/");
        assertEquals(Ref.HEADS_PREFIX + "ref1", ref);

        ref = Ref.child(Ref.HEADS_PREFIX.substring(0, Ref.HEADS_PREFIX.length() - 1), ref);
        assertEquals("ref1", ref);

        ref = Ref.append(Ref.HEADS_PREFIX, "/" + ref);
        assertEquals(Ref.HEADS_PREFIX + "ref1", ref);

        ref = Ref.append(ref, "");
        assertEquals(Ref.HEADS_PREFIX + "ref1", ref);
    }
}
