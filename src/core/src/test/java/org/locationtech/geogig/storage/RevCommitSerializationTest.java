/* Copyright (c) 2012-2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.CommitBuilder;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public abstract class RevCommitSerializationTest extends Assert {

    protected ObjectSerializingFactory serializer;

    private CommitBuilder testCommit;

    @Before
    public void before() {
        this.serializer = getObjectSerializingFactory();
        ObjectId treeId = ObjectId.forString("treeid");
        testCommit = testCommit(treeId, "groldan", "groldan@boundlessgeo.com", 5000L, "jd",
                "jd@lmnsolutions.com", 10000L, "test message", ObjectId.forString("first parent"));
    }

    protected abstract ObjectSerializingFactory getObjectSerializingFactory();

    @Test
    public void testCommitSerialization() throws IOException {
        RevCommit commit = testCommit.build();
        testCommit(commit);
    }

    @Test
    public void testCommitSerializationMultipleLinesMessage() throws IOException {
        testCommit.setMessage("this\n is a \n  multiple lines\n message");
        RevCommit commit = testCommit.build();
        testCommit(commit);
    }

    @Test
    public void testCommitSerializationNoAuthor() throws IOException {
        testCommit.setAuthor(null);
        testCommit.setAuthorEmail(null);
        RevCommit commit = testCommit.build();
        testCommit(commit);
    }

    @Test
    public void testCommitSerializationNoCommitter() throws IOException {
        testCommit.setCommitter(null);
        testCommit.setCommitterEmail(null);
        RevCommit commit = testCommit.build();
        testCommit(commit);
    }

    @Test
    public void testCommitSerializationNoMessage() throws IOException {
        testCommit.setMessage(null);
        RevCommit commit = testCommit.build();
        testCommit(commit);
    }

    @Test
    public void testCommitSerializationNoParents() throws IOException {
        testCommit.setParentIds(null);
        RevCommit commit = testCommit.build();
        testCommit(commit);
    }

    @Test
    public void testCommitSerializationMultipleParents() throws IOException {
        testCommit.setParentIds(ImmutableList.of(ObjectId.forString("parent1"),
                ObjectId.forString("parent2"), ObjectId.forString("parent3"),
                ObjectId.forString("parent4")));
        RevCommit commit = testCommit.build();
        testCommit(commit);
    }

    private void testCommit(RevCommit commit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        serializer.write(commit, out);

        RevObject read = serializer.read(commit.getId(), new ByteArrayInputStream(out.toByteArray()));
        assertEquals(commit, read);
    }

    private CommitBuilder testCommit(ObjectId treeId, String author, String authorEmail,
            long authorTimestamp, String committer, String committerEmail, long committerTimestamp,
            String message, ObjectId... parentIds) {
        CommitBuilder b = new CommitBuilder();
        b.setTreeId(treeId);
        b.setAuthor(author);
        b.setAuthorEmail(authorEmail);
        b.setCommitter(committer);
        b.setCommitterEmail(committerEmail);
        b.setMessage(message);
        b.setAuthorTimestamp(authorTimestamp);
        b.setCommitterTimestamp(committerTimestamp);
        if (parentIds != null) {
            b.setParentIds(Lists.newArrayList(parentIds));
        }
        return b;
    }

    @Test
    public void testCommitRoundTrippin() throws Exception {
        long currentTime = System.currentTimeMillis();
        int timeZoneOffset = TimeZone.getDefault().getOffset(currentTime);
        CommitBuilder builder = new CommitBuilder();
        String author = "groldan";
        builder.setAuthor(author);
        String authorEmail = "groldan@boundlessgeo.com";
        builder.setAuthorEmail(authorEmail);
        builder.setAuthorTimestamp(currentTime);
        builder.setAuthorTimeZoneOffset(timeZoneOffset);
        String committer = "mleslie";
        builder.setCommitter(committer);
        String committerEmail = "mleslie@boundlessgeo.com";
        builder.setCommitterEmail(committerEmail);
        builder.setCommitterTimestamp(currentTime);
        builder.setCommitterTimeZoneOffset(timeZoneOffset);

        ObjectId treeId = ObjectId.forString("Fake tree");
        builder.setTreeId(treeId);

        ObjectId parent1 = ObjectId.forString("Parent 1 of fake commit");
        ObjectId parent2 = ObjectId.forString("Parent 2 of fake commit");
        List<ObjectId> parents = Arrays.asList(parent1, parent2);
        builder.setParentIds(parents);

        RevCommit cmtIn = builder.build();
        assertNotNull(cmtIn);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        serializer.write(cmtIn, bout);

        byte[] bytes = bout.toByteArray();
        assertTrue(bytes.length > 0);

        ByteArrayInputStream bin = new ByteArrayInputStream(bytes);

        RevCommit cmtOut = (RevCommit) serializer.read(cmtIn.getId(), bin);

        assertEquals(treeId, cmtOut.getTreeId());
        assertEquals(parents, cmtOut.getParentIds());
        assertEquals(author, cmtOut.getAuthor().getName().get());
        assertEquals(authorEmail, cmtOut.getAuthor().getEmail().get());
        assertEquals(committer, cmtOut.getCommitter().getName().get());
        assertEquals(committerEmail, cmtOut.getCommitter().getEmail().get());
        assertEquals(currentTime, cmtOut.getCommitter().getTimestamp());
        assertEquals(timeZoneOffset, cmtOut.getCommitter().getTimeZoneOffset());
        assertEquals(currentTime, cmtOut.getAuthor().getTimestamp());
        assertEquals(timeZoneOffset, cmtOut.getAuthor().getTimeZoneOffset());

    }
}
