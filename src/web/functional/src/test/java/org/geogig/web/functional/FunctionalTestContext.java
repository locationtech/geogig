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
import java.net.URI;

import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.porcelain.InitOp;
import org.locationtech.geogig.cli.CLIContextBuilder;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.web.DirectoryRepositoryProvider;
import org.locationtech.geogig.web.Main;
import org.locationtech.geogig.web.api.TestData;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

public class FunctionalTestContext extends ExternalResource {

    /**
     * A temporary folder where to store repositories
     */
    private TemporaryFolder tempFolder;

    private Main app;

    private DirectoryRepositoryProvider repoProvider;

    private Response lastResponse;

    @Override
    public void before() throws Exception {
        GlobalContextBuilder.builder = new CLIContextBuilder();
        this.tempFolder = new TemporaryFolder();
        this.tempFolder.create();

        File rootFolder = tempFolder.getRoot();
        repoProvider = new DirectoryRepositoryProvider(rootFolder);
        this.app = new Main(repoProvider, true);
        this.app.start();
    }

    @Override
    public void after() {
        try {
            // this.client.stop();
            this.app.stop();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            tempFolder.delete();
        }
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
        Hints hints = new Hints();
        hints.set(Hints.REPOSITORY_URL, repoURI);
        Context repoContext = GlobalContextBuilder.builder.build(hints);
        Repository repo = repoContext.command(InitOp.class).call();
        repo.close();
        repo = RepositoryResolver.load(repoURI);
        GeoGIG geogig = new GeoGIG(repo);
        TestData testData = new TestData(geogig);
        return testData;
    }

    public void call(final Method method, final String resourceUri) {
        Request request = new Request(method, resourceUri);
        request.setRootRef(new Reference(""));
        if (Method.PUT.equals(method) || Method.POST.equals(method)) {
            // if the request entity is empty or null, Resouce.handlePut() doesn't get thru the last
            // CommandResource at all
            request.setEntity("empty payload", MediaType.TEXT_PLAIN);
        }
        this.lastResponse = app.handle(request);
    }

    public Response getLastResponse() {
        Preconditions.checkState(lastResponse != null);
        return lastResponse;
    }
}
