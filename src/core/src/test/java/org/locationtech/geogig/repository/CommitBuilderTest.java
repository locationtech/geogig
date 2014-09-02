/*******************************************************************************
 * Copyright (c) 2012, 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.repository;

import java.util.List;

import junit.framework.TestCase;

import org.junit.Test;
import org.locationtech.geogig.api.CommitBuilder;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;

import com.google.common.collect.ImmutableList;

public class CommitBuilderTest extends TestCase {

    @Override
    protected void setUp() throws Exception {

    }

    public void testBuildEmpty() throws Exception {
        CommitBuilder b = new CommitBuilder();
        try {
            b.build();
            fail("expected IllegalStateException on null tree id");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("tree"));
        }
    }

    public void testBuildFull() throws Exception {
        CommitBuilder b = new CommitBuilder();
        b.setAuthor("groldan");
        b.setAuthorEmail("groldan@boundlessgeo.com");
        b.setCommitter("jdeolive");
        b.setCommitterEmail("jdeolive@boundlessgeo.com");
        b.setMessage("cool this works");
        b.setCommitterTimestamp(1000);
        b.setCommitterTimeZoneOffset(10);
        b.setAuthorTimestamp(500);
        b.setAuthorTimeZoneOffset(-5);

        ObjectId treeId = ObjectId.forString("fake tree content");

        b.setTreeId(treeId);

        ObjectId parentId1 = ObjectId.forString("fake parent content 1");
        ObjectId parentId2 = ObjectId.forString("fake parent content 2");
        List<ObjectId> parentIds = ImmutableList.of(parentId1, parentId2);
        b.setParentIds(parentIds);

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
        CommitBuilder b = new CommitBuilder();
        b.setAuthor("groldan");
        b.setAuthorEmail("groldan@boundlessgeo.com");
        b.setCommitter("jdeolive");
        b.setCommitterEmail("jdeolive@boundlessgeo.com");
        b.setMessage("cool this works");
        b.setAuthorTimestamp(1000);

        ObjectId treeId = ObjectId.forString("fake tree content");

        b.setTreeId(treeId);

        b.setParentIds(null);

        assertEquals(ImmutableList.of(), b.getParentIds());
    }

    @Test
    public void testNoMessage() throws Exception {
        CommitBuilder b = new CommitBuilder();
        b.setAuthor("groldan");
        b.setAuthorEmail("groldan@boundlessgeo.com");
        b.setCommitter("jdeolive");
        b.setCommitterEmail("jdeolive@boundlessgeo.com");
        b.setMessage(null);
        b.setAuthorTimestamp(1000);

        ObjectId treeId = ObjectId.forString("fake tree content");

        b.setTreeId(treeId);

        ObjectId parentId1 = ObjectId.forString("fake parent content 1");
        ObjectId parentId2 = ObjectId.forString("fake parent content 2");
        List<ObjectId> parentIds = ImmutableList.of(parentId1, parentId2);
        b.setParentIds(parentIds);

        assertEquals(null, b.getMessage());

        RevCommit commit2 = b.build();
        assertEquals("", commit2.getMessage());
    }

    @Test
    public void testNoAuthorTimeStamp() throws Exception {
        CommitBuilder b = new CommitBuilder();
        b.setAuthor("groldan");
        b.setAuthorEmail("groldan@boundlessgeo.com");
        b.setCommitter("jdeolive");
        b.setCommitterEmail("jdeolive@boundlessgeo.com");
        b.setCommitterTimestamp(1000);
        b.setMessage("cool this works");

        assertEquals(1000, b.getAuthorTimestamp());
    }

    @Test
    public void testCommitBuilder() throws Exception {

        CommitBuilder b = new CommitBuilder();
        b.setAuthor("groldan");
        b.setAuthorEmail("groldan@boundlessgeo.com");
        b.setCommitter("jdeolive");
        b.setCommitterEmail("jdeolive@boundlessgeo.com");
        b.setMessage("cool this works");
        b.setAuthorTimestamp(1000);

        ObjectId treeId = ObjectId.forString("fake tree content");

        b.setTreeId(treeId);

        ObjectId parentId1 = ObjectId.forString("fake parent content 1");
        ObjectId parentId2 = ObjectId.forString("fake parent content 2");
        List<ObjectId> parentIds = ImmutableList.of(parentId1, parentId2);
        b.setParentIds(parentIds);

        RevCommit commit1 = b.build();

        CommitBuilder builder = new CommitBuilder(commit1);

        assertEquals("groldan", builder.getAuthor());
        assertEquals("jdeolive", builder.getCommitter());
        assertEquals("groldan@boundlessgeo.com", builder.getAuthorEmail());
        assertEquals("jdeolive@boundlessgeo.com", builder.getCommitterEmail());
        assertEquals(commit1.getMessage(), builder.getMessage());
        assertEquals(commit1.getParentIds(), builder.getParentIds());
        assertEquals(commit1.getTreeId(), builder.getTreeId());
        assertEquals(commit1.getAuthor().getTimestamp(), builder.getAuthorTimestamp());

        RevCommit commit2 = builder.build();

        assertEquals(commit1, commit2);
    }
}
