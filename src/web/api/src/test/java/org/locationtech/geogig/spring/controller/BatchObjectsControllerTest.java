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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.Iterator;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV1;
import org.locationtech.geogig.test.TestData;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 *
 */
public class BatchObjectsControllerTest extends AbstractControllerTest {

    @Test
    public void testInvalidWant() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        JsonObject json = new JsonObject();
        JsonArray want = new JsonArray();
        json.add("want", want);
        want.add("bad want");
        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/batchobjects").contentType(
                        MediaType.APPLICATION_JSON).content(getBytes(json));
        perform(post).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString(
                        "<response><success>false</success><error>bad want")));
        repo.close();
    }

    @Test
    public void testInvalidHave() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        JsonObject json = new JsonObject();
        JsonArray want = new JsonArray();
        want.add("1234567890123456789012345678901234567890");
        json.add("want", want);
        JsonArray have = new JsonArray();
        have.add("bad have");
        json.add("have", have);
        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/batchobjects").contentType(
                        MediaType.APPLICATION_JSON).content(getBytes(json));
        perform(post).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString(
                        "<response><success>false</success><error>bad have ")));
        repo.close();
    }

    @Test
    public void testBatchObjects() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        Iterator<RevCommit> masterCommits = repo.command(LogOp.class).call();
        repo.command(CheckoutOp.class).setSource("branch1").call();
        Iterator<RevCommit> branch1Commits = repo.command(LogOp.class).call();
        JsonObject json = new JsonObject();
        JsonArray want = new JsonArray();
        json.add("want", want);
        JsonArray have = new JsonArray();
        json.add("have", have);
        while (masterCommits.hasNext()) {
            want.add(masterCommits.next().getId().toString());
        }
        while (branch1Commits.hasNext()) {
            have.add(branch1Commits.next().getId().toString());
        }
        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/batchobjects").contentType(
                        MediaType.APPLICATION_JSON).content(getBytes(json));
        byte[] content = perform(post).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn().getResponse().getContentAsByteArray();
        // get an InputStream from the content
        ByteArrayInputStream bais = new ByteArrayInputStream(content);
        // get the Object serializer
        final RevObjectSerializer serialFac = DataStreamRevObjectSerializerV1.INSTANCE;
        // expected number of RevObjects to be read
        final int expectedNumRevObjects = 14;
        // actual number of RevObject read
        int actualNumRevObjects = 0;
        // parse the response. If parsing throws an error, this test fails
        while (bais.available() > 0) {
            // next 20 bytes should be an ObjectId
            byte[] rawId = new byte[ObjectId.NUM_BYTES];
            // read the OID bytes from the stream
            bais.read(rawId);
            // parse the OID into an object
            ObjectId oid = ObjectId.create(rawId);
            // now read the RevObject from the stream
            serialFac.read(oid, bais);
            // increment the number of RevObjects read
            ++actualNumRevObjects;
        }
        // assert the number of RevObjects read
        assertEquals("Incorrect number of RevObjects in BatchObjects response",
                expectedNumRevObjects, actualNumRevObjects);
        repo.close();
    }
}
