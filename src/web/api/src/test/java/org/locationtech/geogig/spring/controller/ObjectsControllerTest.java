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

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.Iterator;

import org.junit.Test;
import org.locationtech.geogig.model.RevCommit;
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
public class ObjectsControllerTest extends AbstractControllerTest {

    @Test
    public void testInvalidId() throws Exception {
        repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get("/repos/repo1/repo/objects/invalid");
        perform(get).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("Invalid hash string")));
    }

    @Test
    public void testIdNotFound() throws Exception {
        repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get(
                        "/repos/repo1/repo/objects/0000000000000000000000000000000000000000");
        perform(get).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("Object does not exist")));
    }

    @Test
    public void testIdFound() throws Exception {
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
            serialFac.write(next, baos);
            // build the objects API request with the oid String
            String oid = next.getId().toString();
            MockHttpServletRequestBuilder get =
                    MockMvcRequestBuilders.get(
                            "/repos/repo1/repo/objects/" + oid);
            byte[] contentAsByteArray = perform(get).andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                    // verify the bytes
                    .andReturn().getResponse().getContentAsByteArray();
            assertArrayEquals(baos.toByteArray(), contentAsByteArray);
        }
        repo.close();
    }
}
