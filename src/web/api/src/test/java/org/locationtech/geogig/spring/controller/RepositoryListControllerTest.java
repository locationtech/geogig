package org.locationtech.geogig.spring.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public class RepositoryListControllerTest extends AbstractControllerTest {

    @Test
    public void testGetRepositoryList() throws Exception {
        repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();

        repoProvider.createGeogig("repo2", null);
        repoProvider.getTestRepository("repo2").initializeRpository();

        repoProvider.createGeogig("repo3", null);
        repoProvider.getTestRepository("repo3").initializeRpository();

        MockHttpServletRequestBuilder initRequest = MockMvcRequestBuilders.get("/repos.json");

        perform(initRequest).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.repos.repo").exists())
                .andExpect(jsonPath("$.repos.repo").isArray())
                .andExpect(jsonPath("$.repos.repo[?(@.name == \'repo1\')]").exists())
                .andExpect(jsonPath("$.repos.repo[?(@.name == \'repo1\')].href")
                        .value("http://localhost/repos/repo1.json"))
                .andExpect(jsonPath("$.repos.repo[?(@.name == \'repo2\')]").exists())
                .andExpect(jsonPath("$.repos.repo[?(@.name == \'repo2\')].href")
                        .value("http://localhost/repos/repo2.json"))
                .andExpect(jsonPath("$.repos.repo[?(@.name == \'repo3\')]").exists())
                .andExpect(jsonPath("$.repos.repo[?(@.name == \'repo3\')].href")
                        .value("http://localhost/repos/repo3.json"))
        ;
    }
}
