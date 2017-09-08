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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.rest.repository.TestMultiRepositoryProvider;
import org.locationtech.geogig.spring.config.GeoGigWebAPISpringConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { GeoGigWebAPISpringConfig.class })
@WebAppConfiguration
public abstract class AbstractControllerTest {
    @Rule
    public TestMultiRepositoryProvider repoProvider = new TestMultiRepositoryProvider();

    @Autowired
    private WebApplicationContext appContext;

    private MockMvc mockMvc;

    @Before
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.appContext).build();
    }

    public ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        // need to embed a RepsoitoryResolver into the request
        request.requestAttr(RepositoryProvider.KEY, repoProvider);
        return mockMvc.perform(request);

    }

    protected final byte[] getBytes(JsonObject json) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonWriter jsonWriter = new JsonWriter(new PrintWriter(baos))) {
            Streams.write(json, jsonWriter);
        }
        return baos.toByteArray();
    }}
