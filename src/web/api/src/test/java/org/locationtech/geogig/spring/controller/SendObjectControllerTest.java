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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV1;
import org.locationtech.geogig.test.TestData;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 *
 */
public class SendObjectControllerTest extends AbstractControllerTest {

    @Test
    public void testNoObjectData() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        MockHttpServletRequestBuilder post = MockMvcRequestBuilders
                .post("/repos/repo1/repo/sendobject");
        perform(post).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(""));
        repo.close();
    }

    @Test
    public void testInvalidObjectData() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        String garbage = "This is just a bunch of random data that should cause the Object parser to choke";
        // payload needs to end with byte value 0 for the serilaizer to know the data is finished
        byte[] content = new byte[garbage.length() + 1];
        System.arraycopy(garbage.getBytes(), 0, content, 0, content.length - 1);
        content[content.length - 1] = 0;
        MockHttpServletRequestBuilder post = MockMvcRequestBuilders
                .post("/repos/repo1/repo/sendobject").content(content);
        perform(post).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("Unrecognized object header:")));
        repo.close();
    }

    @Test
    public void testSendObjects() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        Iterator<RevCommit> call = repo.command(LogOp.class).call();
        assertTrue(call.hasNext());
        // get the Object serializer
        final RevObjectSerializer serialFac = DataStreamRevObjectSerializerV1.INSTANCE;
        while (call.hasNext()) {
            RevCommit next = call.next();
            // serialize the RevCommit
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // need to write the 20 byte ObjectId first?
            byte[] oidHeader = next.getId().getRawValue();
            assertEquals(ObjectId.NUM_BYTES, oidHeader.length);
            baos.write(oidHeader);
            serialFac.write(next, baos);
            // build the API request
            MockHttpServletRequestBuilder post = MockMvcRequestBuilders
                    .post("/repos/repo1/repo/sendobject").content(baos.toByteArray());
            perform(post).andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                    .andExpect(content().string(""));
            // TODO: The only way to know that this worked is to inspect the INFO logs as the API
            // response is just a 200 OK wether or not objects were ingested.
        }
        repo.close();
    }

    @Test
    public void testSendMultipleObjects() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        Iterator<RevCommit> call = repo.command(LogOp.class).call();
        assertTrue(call.hasNext());
        // get the Object serializer
        final RevObjectSerializer serialFac = DataStreamRevObjectSerializerV1.INSTANCE;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (call.hasNext()) {
            RevCommit next = call.next();
            // serialize the RevCommit
            // need to write the 20 byte ObjectId first?
            byte[] oidHeader = next.getId().getRawValue();
            assertEquals(ObjectId.NUM_BYTES, oidHeader.length);
            baos.write(oidHeader);
            serialFac.write(next, baos);
        }
        // build the API request
        MockHttpServletRequestBuilder post = MockMvcRequestBuilders
                .post("/repos/repo1/repo/sendobject").content(baos.toByteArray());
        perform(post).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(""));
        // TODO: The only way to know that this worked is to inspect the INFO logs as the API
        // response is just a 200 OK wether or not objects were ingested.
        repo.close();
    }

    @Test
    public void testSendObject() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        // get the HEAD commit
        Iterator<RevCommit> commitLogs = repo.command(LogOp.class).setLimit(1).call();
        assertTrue(commitLogs.hasNext());
        RevCommit headCommit = commitLogs.next();
        assertFalse(commitLogs.hasNext());
        // create a new commit that is basically a clone of the HEAD
        RevCommitBuilder builder = RevCommit.builder().init(headCommit);
        // alter the builder to make a distinct commit
        builder.message(headCommit.getMessage() + " MODIFIED FOR SEND OBJECT");
        RevCommit sendObjectCommit = builder.build();
        // ensure the new commit doesn't yet exist in the repo
        RevCommit preCheck = repo.objectDatabase().getIfPresent(sendObjectCommit.getId(),
                RevCommit.class);
        assertNull("Modified commit should not be in the repository yet.", preCheck);
        // get the Object serializer
        final RevObjectSerializer serialFac = DataStreamRevObjectSerializerV1.INSTANCE;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // write the commit OID
        byte[] commitOid = sendObjectCommit.getId().getRawValue();
        baos.write(commitOid);
        // serialize the commit
        serialFac.write(sendObjectCommit, baos);
        // build the API request
        MockHttpServletRequestBuilder post = MockMvcRequestBuilders
                .post("/repos/repo1/repo/sendobject").content(baos.toByteArray());
        perform(post).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(""));
        // now verify the commit is present in the repo
        RevCommit postCheck = repo.objectDatabase().getIfPresent(sendObjectCommit.getId(),
                RevCommit.class);
        assertNotNull("Modified commit should be in the repository.", postCheck);
        repo.close();
    }

    @Test
    public void testSendObject_gzipped() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        // get the HEAD commit
        Iterator<RevCommit> commitLogs = repo.command(LogOp.class).setLimit(1).call();
        assertTrue(commitLogs.hasNext());
        RevCommit headCommit = commitLogs.next();
        assertFalse(commitLogs.hasNext());
        // create a new commit that is basically a clone of the HEAD
        RevCommitBuilder builder = RevCommit.builder().init(headCommit);
        // alter the builder to make a distinct commit
        builder.message(headCommit.getMessage() + " MODIFIED FOR SEND OBJECT");
        RevCommit sendObjectCommit = builder.build();
        // ensure the new commit doesn't yet exist in the repo
        RevCommit preCheck = repo.objectDatabase().getIfPresent(sendObjectCommit.getId(),
                RevCommit.class);
        assertNull("Modified commit should not be in the repository yet.", preCheck);
        // get the Object serializer
        final RevObjectSerializer serialFac = DataStreamRevObjectSerializerV1.INSTANCE;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // wrap it with GZIP
        GZIPOutputStream gzos = new GZIPOutputStream(baos, true);
        // write the commit OID
        byte[] commitOid = sendObjectCommit.getId().getRawValue();
        gzos.write(commitOid);
        // serialize the commit
        serialFac.write(sendObjectCommit, gzos);
        // flush and close
        // gzos.close();
        // build the API request
        MockHttpServletRequestBuilder post = MockMvcRequestBuilders
                .post("/repos/repo1/repo/sendobject").content(baos.toByteArray())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).header("Content-encoding", "gzip");
        perform(post).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(""));
        // now verify the commit is present in the repo
        RevCommit postCheck = repo.objectDatabase().getIfPresent(sendObjectCommit.getId(),
                RevCommit.class);
        assertNotNull("Modified commit should be in the repository.", postCheck);
        repo.close();
    }
}