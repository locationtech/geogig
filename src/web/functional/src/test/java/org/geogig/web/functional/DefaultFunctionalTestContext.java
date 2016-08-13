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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Set;

import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.GeoGIG;
import org.locationtech.geogig.repository.GlobalContextBuilder;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.test.TestPlatform;
import org.locationtech.geogig.web.Main;
import org.locationtech.geogig.web.MultiRepositoryProvider;
import org.locationtech.geogig.web.api.TestData;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.w3c.dom.Document;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * Context that uses the GeoGIG web app to run web API functional tests against.
 */
public class DefaultFunctionalTestContext extends FunctionalTestContext {

    private Main app;

    private MultiRepositoryProvider repoProvider;

    private Response lastResponse;

    /**
     * Set up the context for a scenario.
     */
    @Override
    protected void setUp() throws Exception {
        if (app == null) {
            File rootFolder = tempFolder.getRoot();
            repoProvider = new MultiRepositoryProvider(rootFolder.toURI());

            TestPlatform platform = new TestPlatform(rootFolder);
            GlobalContextBuilder.builder(new FunctionalRepoContextBuilder(platform));

            this.app = new Main(repoProvider, true);
            this.app.start();
        }
    }

    /**
     * Clean up resources used in the scenario.
     */
    @Override
    protected void tearDown() throws Exception {
        if (app != null) {
            try {
                this.app.stop();
            } finally {
                this.app = null;
            }
        }
    }

    /**
     * Return the {@link Repository} that corresponds to the given repository name.
     * 
     * @param name the repository to get
     * @return the repository
     */
    @Override
    public Repository getRepo(String name) {
        return repoProvider.getGeogig(name);
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
        URI repoURI = new File(tempFolder.getRoot(), name).toURI();
        Hints hints = new Hints().uri(repoURI);
        Context repoContext = GlobalContextBuilder.builder().build(hints);
        GeoGIG geogig = new GeoGIG(repoContext);
        TestData testData = new TestData(geogig);
        return testData;
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

            this.lastResponse = app.handle(request);
        } catch (IOException e) {
            Throwables.propagate(e);
        }
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
            // if the request entity is empty or null, Resouce.handlePut() doesn't get thru the last
            // CommandResource at all
            request.setEntity("empty payload", MediaType.TEXT_PLAIN);
        }
        this.lastResponse = app.handle(request);
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
        String xml;
        try {
            xml = getLastResponse().getEntity().getText();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return xml;
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
        try {
            return getLastResponse().getEntityAsDom().getDocument();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
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
