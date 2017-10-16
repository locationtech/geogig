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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Iterator;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.test.TestData;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.google.common.collect.ImmutableList;

/**
 *
 */
public class ParentsControllerTest extends AbstractControllerTest {

    @Test
    public void testMissingCommitId() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get("/repos/repo1/repo/getparents");
        perform(get).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(""));
        repo.close();
    }

    @Test
    public void testInvalidCommitId() throws Exception {
        Repository repo =repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get("/repos/repo1/repo/getparents?commitId=1234");
        perform(get).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("You must specify a valid commit id."));
        repo.close();
    }

    @Test
    public void testCommitIdNotFound() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get(
                        "/repos/repo1/repo/getparents?commitId=0000000000000000000000000000000000000000");
        perform(get).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(""));
        repo.close();
    }

    @Test
    public void testGetParents() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        Iterator<RevCommit> call = repo.command(LogOp.class).call();
        assertTrue(call.hasNext());
        while (call.hasNext()) {
            RevCommit next = call.next();
            ImmutableList<ObjectId> parentIds = next.getParentIds();
            // build the API request
            String oid = next.getId().toString();
            MockHttpServletRequestBuilder get =
                    MockMvcRequestBuilders.get(
                            "/repos/repo1/repo/getparents?commitId=" + oid);
            String response = perform(get).andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                    // verify the bytes
                    .andReturn().getResponse().getContentAsString();
            String[] actualIds = response.split("\\s+");
            if (actualIds.length == 1 && actualIds[0].isEmpty()) {
                // the actual IDs is empty
                actualIds = new String[0];
            }
            String[] expectedIds = new String[parentIds.size()];
            int i = 0;
            for (ObjectId parentId : parentIds) {
                expectedIds[i++] = parentId.toString();
            }
            assertArrayEquals(expectedIds, actualIds);
        }
        repo.close();
    }
}
