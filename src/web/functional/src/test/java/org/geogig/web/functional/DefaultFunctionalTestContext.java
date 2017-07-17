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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.test.TestPlatform;
import org.locationtech.geogig.web.Main;
import org.locationtech.geogig.web.MultiRepositoryProvider;
import org.restlet.Component;
import org.restlet.Server;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.InputRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * Context that uses the GeoGIG web app to run web API functional tests against.
 */
public class DefaultFunctionalTestContext extends FunctionalTestContext {

    private static int TEST_HTTP_PORT = 8182;

    private Main app;

    private MultiRepositoryProvider repoProvider;

    private Response lastResponse;

    private String lastResponseText = null;

    private Document lastResponseDocument = null;

    private Server server;

    private TestPlatform platform;

    /**
     * Set up the context for a scenario.
     */
    @Override
    protected void setUp() throws Exception {
        if (app == null) {
            File rootFolder = tempFolder.getRoot();
            this.platform = new TestPlatform(rootFolder);
            URI rootURI = TestRepoURIBuilderProvider.getURIBuilder().buildRootURI(platform);
            repoProvider = new MultiRepositoryProvider(rootURI);

            GlobalContextBuilder.builder(new FunctionalRepoContextBuilder(platform));

            this.app = new Main(repoProvider, true);
            this.app.start();
            setVariable("@systemTempPath", rootFolder.getCanonicalPath().replace("\\", "/"));
        }
    }

    /**
     * Clean up resources used in the scenario.
     */
    @Override
    protected void tearDown() throws Exception {
        repoProvider.invalidateAll();
        if (app != null) {
            try {
                this.app.stop();
            } finally {
                this.app = null;
            }
        }
        if (server != null) {
            try {
                this.server.stop();
            } finally {
                this.server = null;
            }
        }
    }

    @Override
    protected void serveHttpRepos() throws Exception {
        Component comp = new Component();
        comp.getDefaultHost().attach(this.app);
        this.server = comp.getServers().add(Protocol.HTTP, TEST_HTTP_PORT);
        this.server.start();
    }

    @Override
    public String getHttpLocation(String repoName) {
        return String.format("http://localhost:%d/repos/%s", TEST_HTTP_PORT, repoName);
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
                return repoProvider.getGeogig(name);
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

    private void setLastResponse(Response response) {
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
        try {
            Representation webForm = new MultiPartFileRepresentation(file, formFieldName);
            Request request = new Request(Method.POST, resourceUri, webForm);
            request.setRootRef(new Reference(""));

            setLastResponse(app.handle(request));
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    /**
     * Issue a POST request to the provided URL with the given content as post data.
     * 
     * @param contentType the content type of the data
     * @param url the url to issue the request to
     * @param postContent the content to post
     */
    @Override
    protected void postContentInternal(final String contentType, final String resourceUri,
            final String postContent) {
        StringRepresentation content = new StringRepresentation(postContent);
        content.setMediaType(MediaType.valueOf(contentType));
        Request request = new Request(Method.POST, resourceUri, content);
        request.setRootRef(new Reference(""));

        setLastResponse(app.handle(request));
    }

    /**
     * Issue a request with the given {@link Method} to the provided resource URI.
     * 
     * @param method the http method to use
     * @param resourceUri the uri to issue the request to
     */
    @Override
    protected void callInternal(Method method, String resourceUri) {
        Request request = new Request(method, resourceUri);
        request.setRootRef(new Reference(""));
        if (Method.PUT.equals(method) || Method.POST.equals(method)) {
            // PUT and POST requests should have an entity.
            // Since this method has no content argument, fill the entity with an empty JSON object.
            // This method is hit for setting up repositories for many tests, making PUT calls to trigger the "init"
            // command. The INIT Web API command only accepts JSON and Web Form entities, so we'll use JSON here.
            request.setEntity("{}", MediaType.APPLICATION_JSON);
        }

        setLastResponse(app.handle(request));
    }

    private InputRepresentation createEntity(final String content, final String contentType) {
        // create an InputRepresentation of the content
        final MediaType mediaType = MediaType.valueOf(contentType);
        final ByteArrayInputStream bais = new ByteArrayInputStream(content.getBytes());
        return new InputRepresentation(bais, mediaType);
    }

    @Override
    protected void callInternal(final Method method, String resourceUri, String content, String contentType) {
        Request request = new Request(method, resourceUri);
        request.setRootRef(new Reference(""));
        if (content != null && !content.isEmpty() && contentType != null) {
            // create an InputRepresentation of the content
            request.setEntity(createEntity(content, contentType));
        }
        setLastResponse(app.handle(request));
    }

    /**
     * Helper function to assert that the last response exists and returns it.
     * 
     * @return the last response
     */
    private Response getLastResponse() {
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
            lastResponseText = getLastResponse().getEntity().getText();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return lastResponseText;
    }

    /**
     * @return the content type of the last response
     */
    @Override
    public String getLastResponseContentType() {
        final String xml = getLastResponse().getEntity().getMediaType().getName();
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
                throw Throwables.propagate(e);
            }
        }
        return lastResponseDocument;
    }

    /**
     * @return the status code of the last response
     */
    @Override
    public int getLastResponseStatus() {
        return getLastResponse().getStatus().getCode();
    }

    /**
     * @return the content of the last response as an {@link InputStream}
     * @throws Exception
     */
    @Override
    public InputStream getLastResponseInputStream() throws Exception {
        return getLastResponse().getEntity().getStream();
    }

    /**
     * @return the allowed http methods of the last response
     */
    @Override
    public Set<String> getLastResponseAllowedMethods() {
        return Sets.newHashSet(
                Iterables.transform(getLastResponse().getAllowedMethods(), (s) -> s.getName()));
    }

}
