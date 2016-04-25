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
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.api.plumbing.LsTreeOp;
import org.locationtech.geogig.api.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.web.DirectoryRepositoryProvider;
import org.locationtech.geogig.web.Main;
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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

public class FunctionalTestContext extends ExternalResource {

    /**
     * A temporary folder where to store repositories
     */
    private TemporaryFolder tempFolder;

    private Main app;

    private DirectoryRepositoryProvider repoProvider;

    private Response lastResponse;

    private Map<String, String> variables = new HashMap<>();

    @Override
    public synchronized void before() throws Exception {
        if (app == null) {
            this.tempFolder = new TemporaryFolder();
            this.tempFolder.create();

            File rootFolder = tempFolder.getRoot();
            repoProvider = new DirectoryRepositoryProvider(rootFolder);

            TestPlatform platform = new TestPlatform(rootFolder);
            GlobalContextBuilder.builder(new FunctionalRepoContextBuilder(platform));

            this.app = new Main(repoProvider, true);
            this.app.start();
        }
    }

    @Override
    public synchronized void after() {
        if (app != null) {
            try {
                // this.client.stop();
                this.app.stop();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            } finally {
                this.app = null;
                tempFolder.delete();
            }
        }
    }

    public File getTempFolder() {
        return tempFolder.getRoot();
    }

    public GeoGIG getRepo(String name) {
        return repoProvider.getGeogig(name);
    }

    public SetMultimap<String, String> listRepo(final String repoName, final String headRef) {
        GeoGIG repo = getRepo(repoName);
        Iterator<NodeRef> featureRefs = repo.command(LsTreeOp.class).setReference(headRef)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).call();

        SetMultimap<String, String> features = HashMultimap.create();
        while (featureRefs.hasNext()) {
            NodeRef ref = featureRefs.next();
            features.put(ref.getParentPath(), ref.name());
        }
        return features;
    }

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

    private TestData createRepo(final String name) throws Exception {
        URI repoURI = new File(tempFolder.getRoot(), name).toURI();
        Hints hints = new Hints().uri(repoURI);
        Context repoContext = GlobalContextBuilder.builder().build(hints);
        GeoGIG geogig = new GeoGIG(repoContext);
        TestData testData = new TestData(geogig);
        return testData;
    }

    public void call(final Method method, String resourceUri) {

        this.lastResponse = callInternal(method, resourceUri);
    }

    public Response callDontSaveResponse(final Method method, String resourceUri) {
        return callInternal(method, resourceUri);
    }

    public Response postFile(final String url, final String formFieldName, final File file)
            throws IOException {

        String resourceUri = replaceVariables(url);

        Representation webForm = new MultiPartFileRepresentation(file, formFieldName);
        //
        // FileRepresentation fileEntity = new FileRepresentation(file,
        // MediaType.MULTIPART_FORM_DATA,
        // 30);
        //
        Request request = new Request(Method.POST, resourceUri, webForm);
        request.setRootRef(new Reference(""));

        Response response = app.handle(request);
        this.lastResponse = response;
        return response;
    }

    private Response callInternal(final Method method, String resourceUri) {

        resourceUri = replaceVariables(resourceUri);

        Request request = new Request(method, resourceUri);
        request.setRootRef(new Reference(""));
        if (Method.PUT.equals(method) || Method.POST.equals(method)) {
            // if the request entity is empty or null, Resouce.handlePut() doesn't get thru the last
            // CommandResource at all
            request.setEntity("empty payload", MediaType.TEXT_PLAIN);
        }
        Response response = app.handle(request);
        return response;
    }

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

    public Response getLastResponse() {
        Preconditions.checkState(lastResponse != null, "there is no last reponse");
        return lastResponse;
    }

    public String getLastResponseText() {
        String xml;
        try {
            xml = getLastResponse().getEntity().getText();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return xml;
    }

    public String getLastResponseContentType() {
        final String xml = getLastResponse().getEntity().getMediaType().getName();
        return xml;
    }

    public Document getLastResponseAsDom() {
        try {
            return getLastResponse().getEntityAsDom().getDocument();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

}
