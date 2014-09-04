/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Justin Deoliveira (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.cli.commands;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.util.List;

import org.locationtech.geogig.api.DefaultPlatform;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.web.Main;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.data.Protocol;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * This command starts an embedded server to serve up a repository.
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig serve [-p <port>] [<directory>]}
 * </ul>
 * </p>
 * 
 * @see Main
 */
@RequiresRepository(false)
@Parameters(commandNames = "serve", commandDescription = "Serves a repository through the web api")
public class Serve extends AbstractCommand {

    @Parameter(description = "Repository location (directory).", required = false, arity = 1)
    private List<String> repo;

    @Parameter(names = { "--port", "-p" }, description = "Port to run server on")
    private int port = 8182;

    @Override
    protected void runInternal(GeogigCLI cli) throws InvalidParameterException,
            CommandFailedException, IOException {

        String loc = repo != null && repo.size() > 0 ? repo.get(0) : ".";

        GeoGIG geogig = loadGeoGIG(loc, cli);
        Application application = new Main(geogig);

        Component comp = new Component();

        comp.getDefaultHost().attach(application);
        comp.getServers().add(Protocol.HTTP, port);

        cli.getConsole().println(
                String.format("Starting server on port %d, use CTRL+C to exit.", port));

        try {
            comp.start();
            cli.setExitOnFinish(false);
        } catch (BindException e) {
            String msg = String.format(
                    "Port %d already in use, use the --port parameter to specify a different port",
                    port);
            throw new CommandFailedException(msg, e);
        } catch (Exception e) {
            throw new CommandFailedException("Unable to start server", e);
        }
    }

    GeoGIG loadGeoGIG(String repo, GeogigCLI cli) {
        Platform platform = new DefaultPlatform();
        platform.setWorkingDir(new File(repo));

        GeoGIG geogig = new GeoGIG(cli.getGeogigInjector(), platform.pwd());
        if (geogig.command(ResolveGeogigDir.class).call().isPresent()) {
            geogig.getRepository();
        }

        return geogig;
    }
}
