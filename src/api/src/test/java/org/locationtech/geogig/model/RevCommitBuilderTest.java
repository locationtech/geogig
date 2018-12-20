/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import java.nio.charset.Charset;
import java.util.List;

import org.junit.Test;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;

import junit.framework.TestCase;

public class RevCommitBuilderTest extends TestCase {

    @Override
    protected void setUp() throws Exception {

    }

    public void testBuildEmpty() throws Exception {
        RevCommitBuilder b = RevCommit.builder();
        try {
            b.build();
            fail("expected IllegalStateException on null tree id");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("tree"));
        }
    }

    public void testBuildFull() throws Exception {
        RevCommitBuilder b = RevCommit.builder();
        b.author("groldan");
        b.authorEmail("groldan@boundlessgeo.com");
        b.committer("jdeolive");
        b.committerEmail("jdeolive@boundlessgeo.com");
        b.message("cool this works");
        b.committerTimestamp(1000L);
        b.committerTimeZoneOffset(10);
        b.authorTimestamp(500L);
        b.authorTimeZoneOffset(-5);

        ObjectId treeId = hashString("fake tree content");

        b.treeId(treeId);

        ObjectId parentId1 = hashString("fake parent content 1");
        ObjectId parentId2 = hashString("fake parent content 2");
        List<ObjectId> parentIds = ImmutableList.of(parentId1, parentId2);
        b.parentIds(parentIds);

        RevCommit build = b.build();
        assertNotNull(build.getId());
        assertFalse(build.getId().isNull());
        assertEquals(treeId, build.getTreeId());
        assertEquals(parentIds, build.getParentIds());
        assertEquals("groldan", build.getAuthor().getName().get());
        assertEquals("groldan@boundlessgeo.com", build.getAuthor().getEmail().get());
        assertEquals("jdeolive", build.getCommitter().getName().get());
        assertEquals("jdeolive@boundlessgeo.com", build.getCommitter().getEmail().get());
        assertEquals("cool this works", build.getMessage());
        assertEquals(1000L, build.getCommitter().getTimestamp());
        assertEquals(10, build.getCommitter().getTimeZoneOffset());
        assertEquals(500L, build.getAuthor().getTimestamp());
        assertEquals(-5, build.getAuthor().getTimeZoneOffset());
    }

    @Test
    public void testPassingNullToSetParentIds() throws Exception {
        RevCommitBuilder b = RevCommit.builder();
        b.author("groldan");
        b.authorEmail("groldan@boundlessgeo.com");
        b.committer("jdeolive");
        b.committerEmail("jdeolive@boundlessgeo.com");
        b.message("cool this works");
        b.authorTimestamp(1000L);

        ObjectId treeId = hashString("fake tree content");

        b.treeId(treeId);

        b.parentIds(null);

        assertEquals(ImmutableList.of(), b.build().getParentIds());
    }

    @Test
    public void testNoMessage() throws Exception {
        RevCommitBuilder b = RevCommit.builder();
        b.author("groldan");
        b.authorEmail("groldan@boundlessgeo.com");
        b.committer("jdeolive");
        b.committerEmail("jdeolive@boundlessgeo.com");
        b.message(null);
        b.authorTimestamp(1000L);

        ObjectId treeId = hashString("fake tree content");

        b.treeId(treeId);

        ObjectId parentId1 = hashString("fake parent content 1");
        ObjectId parentId2 = hashString("fake parent content 2");
        List<ObjectId> parentIds = ImmutableList.of(parentId1, parentId2);
        b.parentIds(parentIds);

        assertEquals(null, b.message());

        RevCommit commit2 = b.build();
        assertEquals("", commit2.getMessage());
    }

    @Test
    public void testNoAuthorTimeStamp() throws Exception {
        RevCommitBuilder b = RevCommit.builder();
        b.author("groldan");
        b.authorEmail("groldan@boundlessgeo.com");
        b.committer("jdeolive");
        b.committerEmail("jdeolive@boundlessgeo.com");
        b.committerTimestamp(1000L);
        b.message("cool this works");
        b.treeId(hashString("some tree id"));
        assertEquals(1000, b.build().getAuthor().getTimestamp());
    }

    @Test
    public void testCommitBuilder() throws Exception {

        RevCommitBuilder b = RevCommit.builder();
        b.author("groldan");
        b.authorEmail("groldan@boundlessgeo.com");
        b.committer("jdeolive");
        b.committerEmail("jdeolive@boundlessgeo.com");
        b.message("cool this works");
        b.authorTimestamp(1000L);

        ObjectId treeId = hashString("fake tree content");

        b.treeId(treeId);

        ObjectId parentId1 = hashString("fake parent content 1");
        ObjectId parentId2 = hashString("fake parent content 2");
        List<ObjectId> parentIds = ImmutableList.of(parentId1, parentId2);
        b.parentIds(parentIds);

        RevCommit commit1 = b.build();

        RevCommitBuilder builder = RevCommit.builder().init(commit1);

        assertEquals("groldan", builder.author());
        assertEquals("jdeolive", builder.committer());
        assertEquals("groldan@boundlessgeo.com", builder.authorEmail());
        assertEquals("jdeolive@boundlessgeo.com", builder.committerEmail());
        assertEquals(commit1.getMessage(), builder.message());
        assertEquals(commit1.getParentIds(), builder.parentIds());
        assertEquals(commit1.getTreeId(), builder.treeId());
        assertEquals(commit1.getAuthor().getTimestamp(), builder.authorTimestamp().longValue());

        RevCommit commit2 = builder.build();

        assertEquals(commit1, commit2);
    }

    private static ObjectId hashString(final String strToHash) {
        Preconditions.checkNotNull(strToHash);
        HashCode hashCode = ObjectId.HASH_FUNCTION.hashString(strToHash, Charset.forName("UTF-8"));
        return ObjectId.create(hashCode.asBytes());
    }
}
