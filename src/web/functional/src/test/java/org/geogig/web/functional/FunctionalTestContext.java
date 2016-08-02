/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.geogig.web.functional;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.repository.GeoGIG;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.web.api.TestData;
import org.restlet.data.Method;
import org.w3c.dom.Document;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

/**
 * Context that functional tests can use to issue requests to and get responses from the web API.
 */
public abstract class FunctionalTestContext extends ExternalResource {

    /**
     * A temporary folder to use for tests
     */
    protected TemporaryFolder tempFolder = null;

    private Map<String, String> variables = new HashMap<>();

    @Override
    public synchronized void before() throws Exception {
        if (tempFolder == null) {
            this.tempFolder = new TemporaryFolder();
            this.tempFolder.create();
        }
        setUp();
    }

    /**
     * Set up the context for a scenario.
     */
    protected abstract void setUp() throws Exception;

    @Override
    public synchronized void after() {
        try {
            tearDown();
        } catch (Exception e) {
            Throwables.propagate(e);
        } finally {
            if (tempFolder != null) {
                this.tempFolder.delete();
                this.tempFolder = null;
            }
        }
    }

    /**
     * Clean up resources used in the scenario.
     */
    protected abstract void tearDown() throws Exception;

    /**
     * Return the {@link GeoGIG} that corresponds to the given repository name.
     * 
     * @param name the repository to get
     * @return the repository
     */
    public abstract Repository getRepo(String name);

    public File getTempFolder() {
        return tempFolder.getRoot();
    }

    /**
     * List all features in the repository with the given name at the provided ref.
     * 
     * @param repoName the repository to list the features for
     * @param headRef the ref from which to list the features
     * @return a multimap that contains all of the feature types and their features
     */
    public SetMultimap<String, String> listRepo(final String repoName, final String headRef) {
        Repository repo = getRepo(repoName);
        Iterator<NodeRef> featureRefs = repo.command(LsTreeOp.class).setReference(headRef)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).call();

        SetMultimap<String, String> features = HashMultimap.create();
        while (featureRefs.hasNext()) {
            NodeRef ref = featureRefs.next();
            features.put(ref.getParentPath(), ref.name());
        }
        return features;
    }

    /**
     * Set up multiple repositories for testing.
     * 
     * @throws Exception
     */
    public void setUpDefaultMultiRepoServer() throws Exception {
        createRepo("repo1")//
                .init("geogigUser", "repo1_Owner@geogig.org")//
                .loadDefaultData()//
                .getRepo().close();

        createRepo("repo2")//
                .init("geogigUser", "repo2_Owner@geogig.org")//
                .loadDefaultData()//
                .getRepo().close();
    }

    /**
     * Create a repository with the given name for testing.
     * 
     * @param name the repository name
     * @return a newly created {@link TestData} for the repository.
     * @throws Exception
     */
    protected abstract TestData createRepo(final String name) throws Exception;

    /**
     * Issue a request with the given {@link Method} to the provided resource URI.
     * 
     * @param method the http method to use
     * @param resourceUri the uri to issue the request to
     */
    public void call(final Method method, String resourceUri) {
        callInternal(method, replaceVariables(resourceUri));
    }

    /**
     * Issue a request with the given {@link Method} to the provided resource URI.
     * 
     * @param method the http method to use
     * @param resourceUri the uri to issue the request to
     */
    protected abstract void callInternal(final Method method, String resourceUri);

    /**
     * Issue a POST request to the provided URL with the given file passed as form data.
     * 
     * @param url the url to issue the request to
     * @param formFieldName the form field name for the file to be posted
     * @param file the file to post
     */
    public void postFile(final String url, final String formFieldName, final File file) {
        postFileInternal(replaceVariables(url), formFieldName, file);
    }

    /**
     * Issue a POST request to the provided URL with the given file passed as form data.
     * 
     * @param resourceUri the url to issue the request to
     * @param formFieldName the form field name for the file to be posted
     * @param file the file to post
     */
    protected abstract void postFileInternal(final String resourceUri,
            final String formFieldName, final File file);

    /**
     * @return the content of the last response as text
     */
    public abstract String getLastResponseText();

    /**
     * @return the content type of the last response
     */
    public abstract String getLastResponseContentType();

    /**
     * @return the content of the last response as a {@link Document}
     */
    public abstract Document getLastResponseAsDom();

    /**
     * @return the status code of the last response
     */
    public abstract int getLastResponseStatus();

    /**
     * @return the content of the last response as an {@link InputStream}
     * @throws Exception
     */
    public abstract InputStream getLastResponseInputStream() throws Exception;

    /**
     * @return the allowed http methods of the last response
     */
    public abstract Set<String> getLastResponseAllowedMethods();

    public void setVariable(String name, String value) {
        this.variables.put(name, value);
    }

    public String getVariable(String name) {
        return getVariable(name, this.variables);
    }

    static public String getVariable(String varName, Map<String, String> variables) {
        String varValue = variables.get(varName);
        Preconditions.checkState(varValue != null, "Variable " + varName + " does not exist");
        return varValue;
    }

    public String replaceVariables(final String text) {
        return replaceVariables(text, this.variables);
    }

    static String replaceVariables(final String text, Map<String, String> variables) {
        String resource = text;
        int varIndex = -1;
        while ((varIndex = resource.indexOf("{@")) > -1) {
            for (int i = varIndex + 1; i < resource.length(); i++) {
                char c = resource.charAt(i);
                if (c == '}') {
                    String varName = resource.substring(varIndex + 1, i);
                    String varValue = getVariable(varName, variables);
                    String tmp = resource.replace("{" + varName + "}", varValue);
                    resource = tmp;
                    break;
                }
            }
        }
        return resource;
    }

}
