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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Test;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.test.TestData;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.google.common.base.Optional;
import com.google.gson.JsonObject;

/**
 *
 */
public class FilteredChangesControllerTest extends AbstractControllerTest {

    @Test
    public void testNoCommit() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();

        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/filteredchanges");
        perform(post).andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString(
                        "Object does not exist: 0000000000000000000000000000000000000000")));
        repo.close();
    }

    @Test
    public void testFilteredChanges() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        Optional<RevObject> masterRevObject = repo.command(RevObjectParse.class)
                .setRefSpec("master").call();
        String commitId = masterRevObject.get().getId().toString();
        JsonObject json = new JsonObject();
        json.addProperty("commitId", commitId);
        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/filteredchanges").contentType(
                        MediaType.APPLICATION_JSON).content(getBytes(json));
        perform(post).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }
}
