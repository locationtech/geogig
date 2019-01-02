/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.controller;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.Iterator;

import org.junit.Test;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.RevertOp;
import org.locationtech.geogig.remote.http.BinaryPackedChanges;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV1;
import org.locationtech.geogig.test.TestData;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

/**
 *
 */
public class ApplyChangesControllerTest extends AbstractControllerTest {

    @Test
    public void testEmtpyPost() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/applychanges");
        perform(post).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString(
                        "<response><success>false</success></response>")));
        repo.close();
    }

    @Test
    public void testApplyChanges() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        // get most recent commit on master
        Iterator<RevCommit> masterCommits = repo.command(LogOp.class).setLimit(2).call();
        assertTrue(masterCommits.hasNext());
        RevCommit masterCommit = masterCommits.next();
        // get the Object serializer
        final RevObjectSerializer serialFac = DataStreamRevObjectSerializerV1.INSTANCE;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serialFac.write(masterCommit, baos);
        // number of parent IDs?
        ImmutableList<ObjectId> parentIds = masterCommit.getParentIds();
        baos.write(parentIds.size());
        for (ObjectId parent : parentIds) {
            baos.write(parent.getRawValue());
        }
        // now, get the previous commit
        assertTrue(masterCommits.hasNext());
        final RevCommit previousCommit = masterCommits.next();
        // roll repo back 1 commit
        repo.command(RevertOp.class).addCommit(new Supplier<ObjectId>() {
            @Override
            public ObjectId get() {
                return previousCommit.getId();
            }
        }).call();
        // compute the diff
        try (AutoCloseableIterator<DiffEntry> call = repo.command(DiffOp.class)
                .setNewVersion(masterCommit.getId()).setOldVersion(previousCommit.getId())
                .setReportTrees(true).call()) {
            BinaryPackedChanges changes = new BinaryPackedChanges(repo);
            changes.write(baos, call);
        }
        // send the bytes
        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/applychanges")
                        .content(baos.toByteArray());
        String contentAsString = perform(post).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andReturn().getResponse().getContentAsString();
        // if the return value is not an ObjectId, the parsing on the next line will fail the test
        ObjectId.valueOf(contentAsString);
        repo.close();
    }
}
