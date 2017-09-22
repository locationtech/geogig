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

import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.StringReader;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;

import org.junit.After;
import org.junit.Test;
import org.locationtech.geogig.geotools.cli.TestHelper;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.AsyncContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.google.common.collect.Lists;

public class PostgisControllerTest extends AbstractControllerTest {

    @After
    public void after() {
        AsyncContext.close();
    }

    @Test
    public void testImport() throws Exception {
        PostgisController.dataStoreFactory = TestHelper.createTestFactory();
        repoProvider.createGeogig("testRepo", null);
        repoProvider.getTestRepository("testRepo").initializeRpository();

        MockHttpServletRequestBuilder importRequest = MockMvcRequestBuilders
                .get("/repos/testRepo/postgis/import.json");
        importRequest.param("table", "table1");

        MvcResult result = perform(importRequest).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.task").exists()).andExpect(jsonPath("$.task.id").exists())
                .andReturn();
                //.andExpect(jsonPath("$.task.status").value("RUNNING")).andReturn();

        String jsonString = result.getResponse().getContentAsString();
        JsonObject rootObject = Json.createReader(new StringReader(jsonString)).readObject();
        JsonObject taskObject = rootObject.getJsonObject("task");
        int taskId = taskObject.getInt("id");

        result = waitForTask(taskId)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.task").exists())
                .andExpect(jsonPath("$.task.id").value(taskId))
                .andExpect(jsonPath("$.task.status").value("FINISHED"))
                .andExpect(jsonPath("$.task.result.RevTree.treeId").exists()).andReturn();

        jsonString = result.getResponse().getContentAsString();
        rootObject = Json.createReader(new StringReader(jsonString)).readObject();
        String treeIdStr = rootObject.getJsonObject("task").getJsonObject("result")
                .getJsonObject("RevTree").getString("treeId");
        Repository geogig = repoProvider.getGeogig("testRepo").get();
        Ref workHead = geogig.command(RefParse.class).setName(Ref.WORK_HEAD).call().get();
        assertEquals(treeIdStr, workHead.getObjectId().toString());

        List<NodeRef> nodes = Lists.newArrayList(geogig.command(LsTreeOp.class)
                .setReference(Ref.WORK_HEAD).setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).call());
        assertEquals(2, nodes.size());

    }

    private ResultActions waitForTask(Integer taskId) throws Exception {
        MockHttpServletRequestBuilder taskRequest = MockMvcRequestBuilders
                .get(String.format("/tasks/%d.json", taskId));
        while (true) {
            Thread.sleep(100);
            try {
                return perform(taskRequest).andExpect(jsonPath("$.task.status")
                        .value(not(AsyncContext.Status.RUNNING.toString())));
            } catch (AssertionError e) {
                // ignore
            }
        }
    }
}
