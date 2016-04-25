/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.DefaultPlatform;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.cli.CLIContextBuilder;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.rest.TaskResultDownloadResource;
import org.locationtech.geogig.rest.TaskStatusResource;
import org.locationtech.geogig.rest.osm.OSMRouter;
import org.locationtech.geogig.rest.postgis.PGRouter;
import org.locationtech.geogig.rest.repository.CommandResource;
import org.locationtech.geogig.rest.repository.DeleteRepository;
import org.locationtech.geogig.rest.repository.FixedEncoder;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.rest.repository.RepositoryRouter;
import org.locationtech.geogig.rest.repository.SingleRepositoryProvider;
import org.locationtech.geogig.rest.repository.UploadCommandResource;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.Router;
import org.restlet.data.Protocol;
import org.restlet.data.Request;
import org.restlet.data.Response;

import com.noelios.restlet.application.Decoder;

/**
 * Both an embedded jetty launcher
 */
public class Main extends Application {

    static {
        setup();
    }

    private RepositoryProvider repoProvider;

    private final boolean multiRepo;

    public Main() {
        super();
        this.multiRepo = false;
    }

    public Main(RepositoryProvider repoProvider, boolean multiRepo) {
        super();
        this.repoProvider = repoProvider;
        this.multiRepo = multiRepo;
    }

    @Override
    public void setContext(org.restlet.Context context) {
        super.setContext(context);
        assert context != null;

        Map<String, Object> attributes = context.getAttributes();

        GeoGIG geogig;
        if (attributes.containsKey("geogig")) {
            geogig = (GeoGIG) attributes.get("geogig");
        } else {
            // revisit, not used at all
            // ServletContext sc = (ServletContext) dispatcher.getContext()
            // .getAttributes().get("org.restlet.ext.servlet.ServletContext");
            // String repo = sc.getInitParameter("repository");
            String repo = null;
            if (repo == null) {
                repo = System.getProperty("org.locationtech.geogig.web.repository");
            }
            if (repo == null) {
                return;
                // throw new IllegalStateException(
                // "Cannot launch geogig servlet without `repository` parameter");
            }
            geogig = loadGeoGIG(repo);
        }
        repoProvider = new SingleRepositoryProvider(geogig);
    }

    @Override
    public Restlet createRoot() {

        final Router router = new Router() {

            @Override
            protected synchronized void init(Request request, Response response) {
                super.init(request, response);
                if (!isStarted()) {
                    return;
                }
                request.getAttributes().put(RepositoryProvider.KEY, repoProvider);
            }
        };

        router.attach("/tasks", TaskStatusResource.class);
        router.attach("/tasks/{taskId}.{extension}", TaskStatusResource.class);
        router.attach("/tasks/{taskId}", TaskStatusResource.class);
        router.attach("/tasks/{taskId}/download", TaskResultDownloadResource.class);

        router.attach("/" + RepositoryProvider.BASE_REPOSITORY_ROUTE,
                new RepositoryFinder(repoProvider));

        final Router singleRepoRouter = new Router();
        router.attach("/" + RepositoryProvider.BASE_REPOSITORY_ROUTE + "/{repository}",
                singleRepoRouter);

        Router repo = new RepositoryRouter();
        Router osm = new OSMRouter();
        Router postgis = new PGRouter();
        singleRepoRouter.attach("", DeleteRepository.class);

        singleRepoRouter.attach("/osm", osm);
        singleRepoRouter.attach("/postgis", postgis);
        singleRepoRouter.attach("/repo", repo);
        singleRepoRouter.attach("/import.{extension}", UploadCommandResource.class);
        singleRepoRouter.attach("/import", UploadCommandResource.class);
        singleRepoRouter.attach("/{command}.{extension}", CommandResource.class);
        singleRepoRouter.attach("/{command}", CommandResource.class);

        org.restlet.Context context = getContext();
        // enable support for compressing responses if the client supports it.
        // NOTE: restlet 1.0.8 leaves a dangling thread on each request (see
        // EncodeRepresentation.getStream()
        // This problem is fixed in latest versions (2.x) of restlet. See the javadocs for
        // FixedEncoder for further detail
        // Encoder responseEncoder = new com.noelios.restlet.application.Encoder(context);
        FixedEncoder encoder = new FixedEncoder(context);
        encoder.setEncodeRequest(false);
        encoder.setEncodeResponse(true);
        encoder.setNext(router);

        Decoder decoder = new Decoder(context);
        decoder.setDecodeRequest(true);
        decoder.setDecodeResponse(false);
        decoder.setNext(encoder);

        return decoder;
    }

    static GeoGIG loadGeoGIG(String repo) {
        Platform platform = new DefaultPlatform();
        platform.setWorkingDir(new File(repo));
        Context inj = GlobalContextBuilder.builder().build(new Hints().platform(platform));
        GeoGIG geogig = new GeoGIG(inj);

        if (geogig.command(ResolveGeogigURI.class).call().isPresent()) {
            geogig.getRepository();
            return geogig;
        }

        return geogig;
    }

    static void startServer(String path, boolean multiRepo) throws Exception {
        final RepositoryProvider provider;
        if (multiRepo) {
            provider = new DirectoryRepositoryProvider(new File(path));
        } else {
            provider = new SingleRepositoryProvider(loadGeoGIG(path));
        }
        org.restlet.Context context = new org.restlet.Context();
        Application application = new Main(provider, multiRepo);
        application.setContext(context);
        Component comp = new Component();
        comp.getDefaultHost().attach(application);
        if (multiRepo) {
            System.err.printf("Starting server at port %d for multiple repositories\n", 8182);
        } else {
            System.err.printf("Starting server at port %d for repo %s\n", 8182, path);
        }
        comp.getServers().add(Protocol.HTTP, 8182);
        comp.start();
        System.err.println("started.");
    }

    static void setup() {
        GlobalContextBuilder.builder(new CLIContextBuilder());
    }

    public static void main(String[] args) throws Exception {
        LinkedList<String> argList = new LinkedList<String>(Arrays.asList(args));
        if (argList.size() == 0) {
            System.out.println("provide geogig repo path");
            System.exit(1);
        }
        String repo = argList.pop();
        // TODO: Support multiRepo from this entry point?
        startServer(repo, false);
    }

}
