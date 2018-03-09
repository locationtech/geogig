/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevPerson;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class RevCommitImplTest {

    @Test
    public void testConstructorAndAccessors() {
        RevPerson committer = RevPersonBuilder.build("ksishmael", "kelsey.ishmael@lmnsolutions.com",
                12345, 12345);
        RevPerson author = RevPersonBuilder.build("test", "test@email.com", 12345, 12345);
        ObjectId id = RevObjectTestSupport.hashString("new commit");
        ObjectId treeId = RevObjectTestSupport.hashString("test tree");
        String message = "This is a test commit";
        ImmutableList<ObjectId> parentIds = ImmutableList
                .of(RevObjectTestSupport.hashString("Parent 1"));
        RevCommit commit = CommitBuilder.create(id, treeId, parentIds, author, committer, message);

        assertEquals(committer, commit.getCommitter());
        assertEquals(author, commit.getAuthor());
        assertEquals(id, commit.getId());
        assertEquals(treeId, commit.getTreeId());
        assertEquals(message, commit.getMessage());
        assertEquals(parentIds, commit.getParentIds());
        assertEquals(TYPE.COMMIT, commit.getType());
        assertEquals(parentIds.get(0), commit.parentN(0).get());

        parentIds = ImmutableList.of();
        commit = CommitBuilder.create(id, treeId, parentIds, author, committer, message);
        assertEquals(Collections.EMPTY_LIST, commit.getParentIds());
        assertEquals(Optional.absent(), commit.parentN(0));
    }

    @Test
    public void testToStringAndEquals() {
        RevPerson committer = RevPersonBuilder.build("ksishmael", "kelsey.ishmael@lmnsolutions.com",
                12345, 12345);
        RevPerson author = RevPersonBuilder.build("test", "test@email.com", 12345, 12345);
        ObjectId id = RevObjectTestSupport.hashString("new commit");
        ObjectId treeId = RevObjectTestSupport.hashString("test tree");
        String message = "This is a test commit";
        ImmutableList<ObjectId> parentId = ImmutableList
                .of(RevObjectTestSupport.hashString("Parent 1"));
        ImmutableList<ObjectId> emptyParentIds = ImmutableList.of();
        RevCommit commit = CommitBuilder.create(id, treeId, parentId, author, committer, message);

        String commitString = commit.toString();

        assertEquals("Commit[" + id.toString() + ", '" + message + "']", commitString);

        RevCommit commit2 = CommitBuilder.create(RevObjectTestSupport.hashString("second commit"),
                treeId, parentId, author, committer, message);

        assertTrue(commit.equals(commit2));

        commit2 = CommitBuilder.create(id, RevObjectTestSupport.hashString("new test tree"),
                parentId, author, committer, message);

        assertFalse(commit.equals(commit2));

        commit2 = CommitBuilder.create(id, treeId, emptyParentIds, author, committer, message);

        assertFalse(commit.equals(commit2));

        commit2 = CommitBuilder.create(id, treeId, parentId, committer, committer, message);

        assertFalse(commit.equals(commit2));

        commit2 = CommitBuilder.create(id, treeId, parentId, author, author, message);

        assertFalse(commit.equals(commit2));

        commit2 = CommitBuilder.create(id, treeId, parentId, author, committer, "new message");

        assertFalse(commit.equals(commit2));

        assertFalse(commit.equals(author));

        assertTrue(commit.equals(commit));
    }

}
