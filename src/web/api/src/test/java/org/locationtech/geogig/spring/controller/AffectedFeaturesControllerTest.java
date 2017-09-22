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

/**
 *
 */
public class AffectedFeaturesControllerTest extends AbstractControllerTest {

    @Test
    public void testMissingCommitId() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get("/repos/repo1/repo/affectedfeatures");
        perform(get).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString(
                        "You must specify a commit id.")));
        repo.close();
    }

    @Test
    public void testInvalidCommitId() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get("/repos/repo1/repo/affectedfeatures?commitId=1234");
        perform(get).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString(
                        "You must specify a valid commit id.")));
        repo.close();
    }

    @Test
    public void testCommitIdNotFound() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get(
                        "/repos/repo1/repo/affectedfeatures?commitId=0123456789012345678901234567890123456789");
        perform(get).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString(
                        "<response><success>false</success><error>Object does not exist: 0123456789012345678901234567890123456789</error></response>")));
        repo.close();
    }

    @Test
    public void testAffectedFeatures() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        TestData testData = new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        // make a new commit
        testData.addAndCommit("new test commit", TestData.point1_modified);
        // get most recent commit on master
        Iterator<RevCommit> masterCommits = repo.command(LogOp.class).setLimit(1).call();
        assertTrue(masterCommits.hasNext());
        RevCommit masterCommit = masterCommits.next();
        MockHttpServletRequestBuilder get =
                MockMvcRequestBuilders.get(
                        "/repos/repo1/repo/affectedfeatures?commitId=" + masterCommit.getId().toString());
        String contentAsString = perform(get).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andReturn().getResponse().getContentAsString();
        // the response should be a valid ObjectId, if the parsing fails the test fails
        ObjectId.valueOf(contentAsString);
        repo.close();
    }

}
