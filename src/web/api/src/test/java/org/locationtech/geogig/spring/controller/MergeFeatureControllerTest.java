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
public class MergeFeatureControllerTest extends AbstractControllerTest {

    private static final String ERROR_MSG =
            "<response><success>false</success><error>Invalid POST data.</error></response>";

    @Test
    public void testEmptyPost() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();

        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/mergefeature").contentType(
                        MediaType.APPLICATION_JSON).content("{}");
        perform(post).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString(ERROR_MSG)));
        repo.close();
    }

    @Test
    public void testMissingPath() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();

        // build JSON payload
        JsonObject json = buildWithoutPath();

        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/mergefeature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBytes(json));
        perform(post).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString(ERROR_MSG)));
        repo.close();
    }

    @Test
    public void testMissingTheirs() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        Optional<RevObject> oursRevObject = repo.command(RevObjectParse.class)
                .setRefSpec("master").call();
        String oursId = oursRevObject.get().getId().toString();

        // build JSON payload
        JsonObject json = buildWithoutTheirs(oursId);

        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/mergefeature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBytes(json));
        perform(post).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_XML));
        repo.close();
    }

    @Test
    public void testMissingOurs() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();

        // build JSON payload
        JsonObject json = buildWithoutOurs();

        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/mergefeature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBytes(json));
        perform(post).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString(ERROR_MSG)));
        repo.close();
    }

    @Test
    public void testMergeFeature() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        Optional<RevObject> oursRevObject = repo.command(RevObjectParse.class)
                .setRefSpec("master").call();
        String oursId = oursRevObject.get().getId().toString();
        Optional<RevObject> theirsRevObject = repo.command(RevObjectParse.class)
                .setRefSpec("branch1").call();
        String theirsId = theirsRevObject.get().getId().toString();
        // build JSON payload
        JsonObject json = buildJson(oursId, theirsId, "Points/Point.1");
        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/mergefeature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBytes(json));
        perform(post).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN));
        repo.close();
    }

    private JsonObject buildJson(String ours, String theirs, String path) {
        JsonObject json = new JsonObject();
        json.addProperty("path", path != null ? path : "path");
        json.addProperty("ours", ours != null ? ours : "ours");
        json.addProperty("theirs", theirs != null ? theirs : "theirs");
        json.add("merges", buildMerges());
        return json;
    }

    private JsonObject buildWithoutPath() {
        JsonObject json = new JsonObject();
        json.addProperty("ours", "ours");
        json.addProperty("theirs", "theirs");
        json.add("merges", buildMerges());
        return json;
    }

    private JsonObject buildWithoutOurs() {
        JsonObject json = new JsonObject();
        json.addProperty("path", "path");
        json.addProperty("theirs", "theirs");
        json.add("merges", buildMerges());
        return json;
    }

    private JsonObject buildWithoutTheirs(String ours) {
        JsonObject json = new JsonObject();
        json.addProperty("ours", "ours");
        json.addProperty("path", "path");
        json.add("merges", buildMerges());
        return json;
    }

    private JsonObject buildMerges() {
        JsonObject merges = new JsonObject();
        JsonObject ip = new JsonObject();
        ip.addProperty("ours", Boolean.TRUE);
        JsonObject sp = new JsonObject();
        sp.addProperty("theirs", Boolean.TRUE);
        merges.add("ip", ip);
        merges.add("sp", sp);
        return merges;
    }
}
