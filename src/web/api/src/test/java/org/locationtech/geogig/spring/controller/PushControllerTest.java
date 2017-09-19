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
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.test.TestData;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 *
 */
public class PushControllerTest extends AbstractControllerTest {

    @Test
    public void testBeginPush() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();

        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get("/repos/repo1/repo/beginpush");
        perform(get).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("Push began for address:")));
        repo.close();
    }

    @Test
    public void testEndPushWithNoBegin() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();

        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get("/repos/repo1/repo/endpush");
        perform(get).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString(
                        "<response><success>false</success><error>Tried to end a connection that didn't exist.</error></response>")));
        repo.close();
    }

    @Test
    public void testEndPush() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();

        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get("/repos/repo1/repo/beginpush");
        perform(get).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("Push began for address:")));
        get = MockMvcRequestBuilders.get("/repos/repo1/repo/endpush");
        perform(get).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("Push succeeded for address:")));
        repo.close();
    }
}
