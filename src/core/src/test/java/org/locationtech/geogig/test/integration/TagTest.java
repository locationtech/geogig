/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.TagCreateOp;
import org.locationtech.geogig.porcelain.TagRemoveOp;

import com.google.common.base.Optional;

public class TagTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue("groldan").call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue("groldan@boundlessgeo.com").call();
    }

    @Test
    public void testInvalidTagName() throws Exception {
        insertAndAdd(points1);
        RevCommit commit = geogig.command(CommitOp.class).call();

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Component of ref cannot have two consecutive dots (..) anywhere.");
        geogig.command(TagCreateOp.class).setCommitId(commit.getId()).setName("Tag..1").call();
    }

    @Test
    public void testTagCreation() throws Exception {
        insertAndAdd(points1);
        RevCommit commit = geogig.command(CommitOp.class).call();
        RevTag tag = geogig.command(TagCreateOp.class).setCommitId(commit.getId()).setName("Tag1")
                .call();
        Optional<RevTag> databaseTag = geogig.command(RevObjectParse.class).setRefSpec("Tag1")
                .call(RevTag.class);
        assertTrue(databaseTag.isPresent());
        assertEquals(tag, databaseTag.get());
    }

    @Test
    public void testTagRemoval() throws Exception {
        insertAndAdd(points1);
        RevCommit commit = geogig.command(CommitOp.class).call();
        RevTag tag = geogig.command(TagCreateOp.class).setCommitId(commit.getId()).setName("Tag1")
                .call();
        Optional<RevTag> databaseTag = geogig.command(RevObjectParse.class).setRefSpec("Tag1")
                .call(RevTag.class);
        assertTrue(databaseTag.isPresent());
        RevTag removedTag = geogig.command(TagRemoveOp.class).setName("Tag1").call();
        assertEquals(tag, removedTag);
        Optional<ObjectId> databaseTagId = geogig.command(RevParse.class).setRefSpec("Tag1").call();
        assertFalse(databaseTagId.isPresent());

    }

}
