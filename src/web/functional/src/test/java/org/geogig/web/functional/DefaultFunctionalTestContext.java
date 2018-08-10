/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.geogig.web.functional;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jetty.server.ServerConnector;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.rest.repository.MultiRepositoryProvider;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.config.GeoGigWebAPISpringConfig;
import org.locationtech.geogig.spring.main.JettyServer;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.test.TestPlatform;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;

/**
 * Context that uses the GeoGIG web app to run web API functional tests against.
 */
public class DefaultFunctionalTestContext extends FunctionalTestContext {

    private int serverPort;

    private MultiRepositoryProvider repoProvider;

    private MvcResult lastResponse;

    private String lastResponseText = null;

    private Document lastResponseDocument = null;

    private TestPlatform platform;

    private JettyServer server = null;

    protected WebApplicationContext wac;

    /**
     * Set up the context for a scenario.
     */
    @Override
    protected void setUp() throws Exception {
        File rootFolder = tempFolder.getRoot();
        this.platform = new TestPlatform(rootFolder);
        URI rootURI = TestRepoURIBuilderProvider.getURIBuilder().buildRootURI(platform);
        repoProvider = new MultiRepositoryProvider(rootURI);

        GlobalContextBuilder.builder(new FunctionalRepoContextBuilder(platform));

        setVariable("@systemTempPath", rootFolder.getCanonicalPath().replace("\\", "/"));
        setupSpringContext();
    }

    private void setupSpringContext() {
        GenericWebApplicationContext context = new GenericWebApplicationContext();
        new AnnotatedBeanDefinitionReader(context).register(GeoGigWebAPISpringConfig.class);
        MockServletContext msc = new MockServletContext("");
        context.setServletContext(msc);
        context.refresh();
        wac = context;
    }

    /**
     * Clean up resources used in the scenario.
     */
    @Override
    protected void tearDown() throws Exception {
        repoProvider.invalidateAll();
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Override
    protected void serveHttpRepos() throws Exception {
        final int requestRandomPort = 0;
        server = new JettyServer(requestRandomPort, repoProvider);
        server.start(true);
        serverPort = ((ServerConnector) server.getServer().getConnectors()[0]).getLocalPort();
        System.err.println("Server running on port " + serverPort);
    }

    @Override
    public String getHttpLocation(String repoName) {
        return String.format("http://localhost:%d/repos/%s", serverPort, repoName);
    }

    /**
     * Return the {@link Repository} that corresponds to the given repository name.
     * 
     * @param name the repository to get
     * @return the repository
     */
    @Override
    public Repository getRepo(String name) {
        // don't call getGeogig(name) on a repo that doesn't exist or it will be created
        final Iterator<String> repos = repoProvider.findRepositories();
        while (repos.hasNext()) {
            if (name.equals(repos.next())) {
                return repoProvider.getGeogigByName(name);
            }
        }
        return null;
    }

    /**
     * Create a repository with the given name for testing.
     * 
     * @param name the repository name
     * @return a newly created {@link TestData} for the repository.
     * @throws Exception
     */
    @Override
    protected TestData createRepo(final String name) throws Exception {
        URI repoURI = TestRepoURIBuilderProvider.getURIBuilder().newRepositoryURI(name, platform);
        Hints hints = new Hints().uri(repoURI);
        Context repoContext = GlobalContextBuilder.builder().build(hints);
        GeoGIG geogig = new GeoGIG(repoContext);
        TestData testData = new TestData(geogig);
        return testData;
    }

    private void setLastResponse(MvcResult response) {
        this.lastResponse = response;
        this.lastResponseText = null;
        this.lastResponseDocument = null;
    }

    /**
     * Issue a POST request to the provided URL with the given file passed as form data.
     * 
     * @param resourceUri the url to issue the request to
     * @param formFieldName the form field name for the file to be posted
     * @param file the file to post
     */
    @Override
    protected void postFileInternal(String resourceUri, String formFieldName, File file) {
        if (resourceUri.endsWith("/")) {
            resourceUri = resourceUri.substring(0, resourceUri.length() - 1);
        }
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(wac).build();
        try (FileInputStream fis = new FileInputStream(file)) {
            MockMultipartFile mFile = new MockMultipartFile(formFieldName, fis);
            MockMultipartHttpServletRequestBuilder request = MockMvcRequestBuilders
                    .fileUpload(new URI(resourceUri)).file(mFile);
            request.requestAttr(RepositoryProvider.KEY, repoProvider);
            setLastResponse(mvc.perform(request).andReturn());
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Issue a POST request to the provided URL with the given content as post data.
     * 
     * @param contentType the content type of the data
     * @param resourceUri the url to issue the request to
     * @param postContent the content to post
     */
    @Override
    protected void postContentInternal(final String contentType, String resourceUri,
            final String postContent) {
        if (resourceUri.endsWith("/")) {
            resourceUri = resourceUri.substring(0, resourceUri.length() - 1);
        }
        try {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(wac).build();
            MockHttpServletRequestBuilder request = MockMvcRequestBuilders
                    .post(new URI(resourceUri)).content(postContent);
            request.requestAttr(RepositoryProvider.KEY, repoProvider);
            setLastResponse(mvc.perform(request).andReturn());
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Issue a request with the given {@link HttpMethod} to the provided resource URI.
     * 
     * @param method the http method to use
     * @param resourceUri the uri to issue the request to
     */
    @Override
    protected void callInternal(HttpMethod method, String resourceUri) {
        try {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(wac).build();
            MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method,
                    new URI(resourceUri));
            request.requestAttr(RepositoryProvider.KEY, repoProvider);
            if (HttpMethod.PUT.equals(method) || HttpMethod.POST.equals(method)) {
                // PUT and POST requests should have an entity.
                // Since this method has no content argument, fill the entity with an empty JSON
                // object.
                // This method is hit for setting up repositories for many tests, making PUT calls
                // to trigger the "init"
                // command. The INIT Web API command only accepts JSON and Web Form entities, so
                // we'll use JSON here.
                request.content("{}")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
            }
            setLastResponse(mvc.perform(request).andReturn());
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void callInternal(final HttpMethod method, String resourceUri, String content,
            String contentType) {
        if (resourceUri.endsWith("/")) {
            resourceUri = resourceUri.substring(0, resourceUri.length() - 1);
        }

        try {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(wac).build();
            MockHttpServletRequestBuilder request = MockMvcRequestBuilders
                    .request(method, new URI(resourceUri)).content(content)
                    .contentType(contentType);
            request.requestAttr(RepositoryProvider.KEY, repoProvider);
            setLastResponse(mvc.perform(request).andReturn());
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper function to assert that the last response exists and returns it.
     * 
     * @return the last response
     */
    private MvcResult getLastResponse() {
        Preconditions.checkState(lastResponse != null, "there is no last reponse");
        return lastResponse;
    }

    /**
     * @return the content of the last response as text
     */
    @Override
    public String getLastResponseText() {
        if (lastResponseText != null) {
            return lastResponseText;
        }
        try {
            lastResponseText = getLastResponse().getResponse().getContentAsString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return lastResponseText;
    }

    /**
     * @return the content type of the last response
     */
    @Override
    public String getLastResponseContentType() {
        final String xml = getLastResponse().getResponse().getContentType();
        return xml;
    }

    /**
     * @return the content of the last response as a {@link Document}
     */
    @Override
    public Document getLastResponseAsDom() {
        if (lastResponseDocument == null) {
            try {
                String text = getLastResponseText();
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();

                lastResponseDocument = builder.parse(new ByteArrayInputStream(text.getBytes()));
            } catch (IOException | SAXException | ParserConfigurationException e) {
                throw new RuntimeException(e);
            }
        }
        return lastResponseDocument;
    }

    /**
     * @return the status code of the last response
     */
    @Override
    public int getLastResponseStatus() {
        return getLastResponse().getResponse().getStatus();
    }

    /**
     * @return the content of the last response as an {@link InputStream}
     * @throws Exception
     */
    @Override
    public InputStream getLastResponseInputStream() throws Exception {
        return new ByteArrayInputStream(getLastResponse().getResponse().getContentAsByteArray());
    }

    /**
     * @return the allowed http methods of the last response
     */
    @Override
    public Set<String> getLastResponseAllowedMethods() {
        // HttpHeaders for ALLOW comes back as a comma separated list in a single String, not
        Object headerValues = getLastResponse().getResponse().getHeaderValue(HttpHeaders.ALLOW);
        return Sets.newHashSet(headerValues.toString().split(","));
    }

}
