/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.spring.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public class RepositoryInitControllerTest extends AbstractControllerTest {

    @Test
    public void testFailWithInitializedRepository() throws Exception {
        repoProvider.createGeogig("testRepo", null);
        repoProvider.getTestRepository("testRepo").initializeRpository();

        MockHttpServletRequestBuilder initRequest = MockMvcRequestBuilders
                .put("/repos/testRepo/init.json");

        perform(initRequest).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response.success").value(false))
                .andExpect(jsonPath("$.response.error")
                        .value("Cannot run init on an already initialized repository."));
    }

    @Test
    public void testInitRepo() throws Exception {
        MockHttpServletRequestBuilder initRequest = MockMvcRequestBuilders
                .put("/repos/testRepo/init.json");

        perform(initRequest).andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response.success").value(true))
                .andExpect(jsonPath("$.response.repo.name").value("testRepo"))
                .andExpect(jsonPath("$.response.repo.href")
                        .value("http://localhost/repos/testRepo.json"));
    }
}