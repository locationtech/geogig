/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.test.integration;

import org.junit.Test;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.ConfigOp;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.api.porcelain.TagCreateOp;
import org.locationtech.geogig.api.porcelain.TagRemoveOp;

import com.google.common.base.Optional;

public class TagTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue("groldan").call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue("groldan@boundlessgeo.com").call();
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
