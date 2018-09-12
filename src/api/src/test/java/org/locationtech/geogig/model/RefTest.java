/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RefTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    final ObjectId oid = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");

    final ObjectId oid2 = ObjectId.valueOf("abc123000000000000001234567890abcdef0002");

    final ObjectId oid3 = ObjectId.valueOf("abc123000000000000001234567890abcdef0003");

    @Test
    public void testConstructor() throws Exception {

        Ref testRef = new Ref(Ref.REFS_PREFIX + "commit1", oid);

        assertEquals(Ref.REFS_PREFIX + "commit1", testRef.getName());
        assertEquals(Ref.REFS_PREFIX, testRef.namespace());
        assertEquals("commit1", testRef.localName());
        assertEquals(oid, testRef.getObjectId());
    }

    @Test
    public void testToString() throws Exception {
        Ref testRef = new Ref(Ref.REFS_PREFIX + "commit1", oid);

        assertEquals("[" + testRef.getName() + " -> " + testRef.getObjectId().toString() + "]",
                testRef.toString());
    }

    @Test
    public void testEquals() throws Exception {

        Ref testRef = new Ref(Ref.REFS_PREFIX + "commit1", oid);
        Ref testRef2 = new Ref(Ref.REFS_PREFIX + "commit2", oid2);

        assertFalse(testRef.equals(testRef2));

        testRef2 = new Ref(Ref.REFS_PREFIX + "commit1", oid3);

        assertFalse(testRef.equals(testRef2));

        assertFalse(testRef.equals("not a ref"));

        assertTrue(testRef.equals(testRef));
    }

    @Test
    public void testCompare() throws Exception {
        Ref testRef = new Ref(Ref.REFS_PREFIX + "commit1", oid);
        Ref testRef2 = new Ref(Ref.REFS_PREFIX + "commit2", oid2);

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
        assertEquals(Ref.REMOTES_PREFIX + "origin/", ref);

        ref = Ref.namespace(Ref.TAGS_PREFIX + "tag1");
        assertEquals(Ref.TAGS_PREFIX, ref);

        ref = Ref.namespace("ref1");
        assertEquals("ref1", ref);
    }

    @Test
    public void testAppendAndChild() {
        assertEquals("refs/heads/ref1", Ref.append(Ref.HEADS_PREFIX, "ref1"));
        assertEquals("ref1", Ref.child(Ref.HEADS_PREFIX, "refs/heads/ref1"));

        assertEquals("ref1", Ref.append("", "ref1"));

        assertEquals("refs/heads/ref1", Ref.append(Ref.HEADS_PREFIX, "ref1/"));

        assertEquals("ref1", Ref.child("refs/heads", "refs/heads/ref1"));

        assertEquals("refs/heads/ref1", Ref.append(Ref.HEADS_PREFIX, "/ref1"));

        assertEquals("refs/heads/ref1", Ref.append("refs/heads/ref1", ""));
    }

    @Test
    public void testParentPath() {
        assertEquals("refs/heads", Ref.parentPath("refs/heads/ref1"));
        assertEquals("refs", Ref.parentPath("refs/heads"));
        assertEquals("", Ref.parentPath("refs"));
    }

    @Test
    public void testSimpleName() {
        assertEquals("ref1", Ref.simpleName("refs/heads/ref1"));
        assertEquals("HEAD", Ref.simpleName("HEAD"));
    }

    @Test
    public void testHashCode() {
        assertFalse(new Ref("refs/heads/master", oid)
                .hashCode() == new Ref("refs/heads/branch1", oid2).hashCode());
    }
}
