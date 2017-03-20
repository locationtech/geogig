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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.impl.RevFeatureTypeBuilder;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.TransactionResolve;
import org.locationtech.geogig.porcelain.BranchDeleteOp;
import org.locationtech.geogig.porcelain.CloneOp;
import org.locationtech.geogig.porcelain.RemoteAddOp;
import org.locationtech.geogig.porcelain.ResetOp;
import org.locationtech.geogig.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.web.api.TestData;
import org.restlet.data.Method;
import org.w3c.dom.Document;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
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
    public TemporaryFolder tempFolder = null;

    private Map<String, String> variables = new HashMap<>();

    @Override
    public synchronized void before() throws Exception {
        if (tempFolder == null) {
            this.tempFolder = new TemporaryFolder();
            this.tempFolder.create();
        }
        setUp();

        RevFeatureType rft = RevFeatureTypeBuilder.build(TestData.pointsType);
        setVariable("@PointsTypeID", rft.getId().toString());

        rft = RevFeatureTypeBuilder.build(TestData.linesType);
        setVariable("@LinesTypeID", rft.getId().toString());

        rft = RevFeatureTypeBuilder.build(TestData.polysType);
        setVariable("@PolysTypeID", rft.getId().toString());
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
     * @param txId the transaction that the tree is on
     * @param index {@code true} if the {@code headRef} parameter refers to an index tree
     * @return a multimap that contains all of the feature types and their features
     */
    public SetMultimap<String, String> listRepo(final String repoName, final String headRef,
            final String txId, final boolean index) {
        Context context = getRepo(repoName).context();
        if (txId != null) {
            context = context.command(TransactionResolve.class)
                    .setId(UUID.fromString(getVariable(txId))).call().get();
        }

        ObjectStore source = index ? context.indexDatabase() : context.objectDatabase();

        Iterator<NodeRef> featureRefs = context.command(LsTreeOp.class).setReference(headRef)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).setSource(source).call();

        SetMultimap<String, String> features = HashMultimap.create();
        while (featureRefs.hasNext()) {
            NodeRef ref = featureRefs.next();
            features.put(index ? "index" : ref.getParentPath(), ref.name());
        }
        return features;
    }

    /**
     * Set up multiple repositories for testing.
     * 
     * @return Collection of repository names created.
     *
     * @throws Exception
     */
    public Collection<String> setUpDefaultMultiRepoServer() throws Exception {
        createRepo("repo1")//
                .init("geogigUser", "repo1_Owner@geogig.org")//
                .loadDefaultData()//
                .getRepo().close();

        createRepo("repo2")//
                .init("geogigUser", "repo2_Owner@geogig.org")//
                .loadDefaultData()//
                .getRepo().close();
        return Arrays.asList("repo1", "repo2");
    }

    protected abstract void serveHttpRepos() throws Exception;

    public abstract String getHttpLocation(String repoName);

    /**
     * Set up multiple repositories with remotes for testing.
     * 
     * @return Collection of repository names created.
     *
     * @throws Exception
     */
    public Collection<String> setUpDefaultMultiRepoServerWithRemotes(boolean http) throws Exception {
        if (http) {
            serveHttpRepos();
        }
        Repository repo1 = createRepo("repo1")//
                .init("geogigUser", "repo1_Owner@geogig.org")//
                .loadDefaultData()//
                .getRepo();

        String repo1Url = http ? getHttpLocation("repo1") : repo1.getLocation().toString();

        Repository repo2 = createRepo("repo2")//
                .init("geogigUser", "repo2_Owner@geogig.org").getRepo();

        repo2.command(CloneOp.class).setRepositoryURL(repo1Url).call();

        String repo2Url = http ? getHttpLocation("repo2") : repo2.getLocation().toString();
        
        repo2.close();

        if (http) {
            // The http clone triggers repo1 to be opened by the repository provider. We'll close
            // ours and use that one instead.
            repo1.close();
            repo1 = getRepo("repo1");
        }

        repo1.command(BranchDeleteOp.class).setName("branch2").call();

        Repository repo3 = createRepo("repo3")
                .init("geogigUser", "repoWithRemotes_Owner@geogig.org").getRepo();

        repo3.command(RemoteAddOp.class).setName("repo1").setURL(repo1Url).call();
        repo3.command(RemoteAddOp.class).setName("repo2").setURL(repo2Url).call();
        
        repo3.close();

        Repository repo4 = createRepo("repo4")//
                .init("geogigUser", "repo4_Owner@geogig.org").getRepo();

        repo4.command(CloneOp.class).setRepositoryURL(repo1Url).call();

        Optional<RevObject> masterOriginal = repo4.command(RevObjectParse.class)
                .setRefSpec("master~2").call();
        repo4.command(ResetOp.class).setCommit(Suppliers.ofInstance(masterOriginal.get().getId()))
                .setMode(ResetMode.HARD).call();

        String repo4Url = http ? getHttpLocation("repo4") : repo4.getLocation().toString();
        
        repo4.close();

        repo1.command(RemoteAddOp.class).setName("repo4").setURL(repo4Url).call();

        if (!http) {
            repo1.close();
        }
        return Arrays.asList("repo1", "repo2", "repo3", "repo4");
    }

    /**
     * Set up multiple repositories with a shallow clone for testing.
     * 
     * @return Collection of repository names created.
     *
     * @throws Exception
     */
    public Collection<String> setUpDefaultMultiRepoServerWithShallowClone() throws Exception {
        Repository repo1 = createRepo("full")//
                .init("geogigUser", "full_Owner@geogig.org")//
                .loadDefaultData()//
                .getRepo();

        String repo1Url = repo1.getLocation().toString();

        Repository repo2 = createRepo("shallow")//
                .init("geogigUser", "shallow_Owner@geogig.org").getRepo();

        repo2.command(CloneOp.class).setRepositoryURL(repo1Url).setDepth(1).call();

        repo1.close();
        repo2.close();
        return Arrays.asList("full", "shallow");
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
     * Issue a request with the given {@link Method} to the provided resource URI and payload.
     *
     * @param method the http method to use
     * @param resourceUri the uri to issue the request to
     * @param content the payload to encode into the request body
     * @param contentType the MediaType of the payload
     */
    public void call(final Method method, String resourceUri, String content, String contentType) {
        callInternal(method, replaceVariables(resourceUri), content, contentType);
    }

    /**
     * Issue a request with the given {@link Method} to the provided resource URI and payload.
     *
     * @param method the http method to use
     * @param resourceUri the uri to issue the request to
     * @param content the payload to encode into the request body
     * @param contentType the MediaType of the payload
     */
    protected abstract void callInternal(final Method method, String resourceUri, String content, String contentType);

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
     * Issue a POST request to the provided URL with the given text as post data.
     * 
     * @param url the url to issue the request to
     * @param postText the text to post
     */
    public void postText(final String url, final String postText) {
        postTextInternal(replaceVariables(url), replaceVariables(postText));
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
     * Issue a POST request to the provided URL with the given text as post data.
     * 
     * @param url the url to issue the request to
     * @param postText the text to post
     */
    protected abstract void postTextInternal(final String resourceUri, final String postText);

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
        return replaceVariables(text, this.variables, this);
    }

    static String replaceVariables(final String text, Map<String, String> variables,
            FunctionalTestContext context) {
        String resource = text;
        int varIndex = -1;
        while ((varIndex = resource.indexOf("{@")) > -1) {
            for (int i = varIndex + 1; i < resource.length(); i++) {
                char c = resource.charAt(i);
                if (c == '}') {
                    String varName = resource.substring(varIndex + 1, i);
                    String varValue;
                    if (context != null && varName.startsWith("@ObjectId|")) {
                        String[] parts = varName.split("\\|");
                        String repoName = parts[1];
                        Repository repo = context.getRepo(repoName);
                        String ref;
                        Optional<RevObject> object;
                        if (parts.length == 3) {
                            ref = parts[2];
                            object = repo.command(RevObjectParse.class)
                                    .setRefSpec(ref).call();

                        } else {
                            ref = parts[3];
                            String txVar = getVariable(parts[2], variables);
                            GeogigTransaction transaction = repo.command(TransactionResolve.class)
                                    .setId(UUID.fromString(txVar)).call().get();
                            object = transaction.command(RevObjectParse.class)
                                    .setRefSpec(ref).call();
                        }
                        if (object.isPresent()) {
                            varValue = object.get().getId().toString();
                        } else {
                            varValue = "";
                        }
                    } else {
                        varValue = getVariable(varName, variables);
                    }
                    String tmp = resource.replace("{" + varName + "}", varValue);
                    resource = tmp;
                    break;
                }
            }
        }
        return resource;
    }

    static String replaceVariables(final String text, Map<String, String> variables) {
        return replaceVariables(text, variables, null);
    }

}
