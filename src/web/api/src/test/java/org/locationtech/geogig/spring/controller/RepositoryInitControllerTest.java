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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.rest.repository.TestMultiRepositoryProvider;
import org.locationtech.geogig.spring.config.GeoGigWebAPISpringConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { GeoGigWebAPISpringConfig.class })
@WebAppConfiguration
public class RepositoryInitControllerTest {

    @Rule
    public TestMultiRepositoryProvider repoProvider = new TestMultiRepositoryProvider();

    @Autowired
    private WebApplicationContext appContext;

    private MockMvc mockMvc;

    @Before
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.appContext).build();
    }

    @Test
    public void testFailWithInitializedRepository() throws Exception {
        repoProvider.createGeogig("testRepo", null);
        repoProvider.getTestRepository("testRepo").initializeRpository();

        MockHttpServletRequestBuilder initRequest = MockMvcRequestBuilders
                .put("/repos/testRepo/init.json");
        // need to embed a RepsoitoryResolver into the request
        initRequest.requestAttr(RepositoryProvider.KEY, repoProvider);

        mockMvc.perform(initRequest).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response.success").value(false))
                .andExpect(jsonPath("$.response.error")
                        .value("Cannot run init on an already initialized repository."));
    }

    @Test
    public void testInitRepo() throws Exception {
        MockHttpServletRequestBuilder initRequest = MockMvcRequestBuilders
                .put("/repos/testRepo/init.json");
        // need to embed a RepsoitoryResolver into the request
        initRequest.requestAttr(RepositoryProvider.KEY, repoProvider);

        mockMvc.perform(initRequest).andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response.success").value(true))
                .andExpect(jsonPath("$.response.repo.name").value("testRepo"))
                .andExpect(jsonPath("$.response.repo.href")
                        .value("http://localhost/repos/testRepo.json"));
    }
}