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

/**
 *
 */
public class DepthControllerTest extends AbstractControllerTest {

    @Test
    public void testInvalidCommitId() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();

        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get("/repos/repo1/repo/getdepth?commitId=1234");
        perform(get).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("You must specify a valid commit id.")));
        repo.close();
    }

    @Test
    public void testNotFoundCommitId() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();

        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get("/repos/repo1/repo/getdepth?commitId=1234567890123456789012345678901234567890");
        perform(get).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString(
                        "<response><success>false</success><error>Graph Object does not exist: 1234567890123456789012345678901234567890</error></response>")));
        repo.close();
    }

    @Test
    public void testgetDepth() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        Optional<RevObject> masterRevObject = repo.command(RevObjectParse.class)
                .setRefSpec("master").call();
        String masterId = masterRevObject.get().getId().toString();
        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get("/repos/repo1/repo/getdepth?commitId=" + masterId);
        perform(get).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("2"));
        repo.close();
    }
}
