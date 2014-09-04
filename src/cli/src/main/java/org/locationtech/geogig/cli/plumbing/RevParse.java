/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.plumbing;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import jline.console.ConsoleReader;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.repository.Hints;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

/**
 * Determines if the current directory is inside a geogig repository.
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig rev-parse --resolve-geogig-dir}: check if the current directory is inside a
 * geogig repository and print out the repository location
 * <li> {@code geogig rev-parse --is-inside-work-tree}: check if the current directory is inside a
 * geogig repository and print out the repository location
 * </ul>
 */
@ReadOnly
@Parameters(commandNames = "rev-parse", commandDescription = "Resolve parameters according to the arguments")
public class RevParse extends AbstractCommand {

    @Parameter(names = "--resolve-geogig-dir", description = "Check if the current directory is inside a geogig repository and print out the repository location")
    private boolean resolve_geogig_dir;

    @Parameter(names = "--is-inside-work-tree", description = "Check if the current directory is inside a geogig repository and print out the repository location")
    private boolean is_inside_work_tree;

    @Parameter(description = "[refSpec]... where refSpec is of the form [<object id>|<ref name>][^<parent index>]+[~<ancestor index>]+")
    private List<String> refSpecs = Lists.newArrayList();

    /**
     * Executes the rev-parse command using the provided options.
     */
    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        GeoGIG geogig = cli.getGeogig();

        if (!refSpecs.isEmpty()) {
            checkParameter(!(resolve_geogig_dir || is_inside_work_tree),
                    "if refSpec is given, --resolve-geogig-dir or --is-inside-work-tree shall not be specified");
            ConsoleReader console = cli.getConsole();
            for (String refSpec : this.refSpecs) {
                Optional<ObjectId> resolved = geogig
                        .command(org.locationtech.geogig.api.plumbing.RevParse.class)
                        .setRefSpec(refSpec).call();
                checkParameter(resolved.isPresent(), "fatal: ambiguous argument '%s': "
                        + "unknown revision or path not in the working tree.", refSpec);
                console.println(resolved.get().toString());
            }
            console.flush();
            return;
        }

        boolean closeIt = false;
        if (null == geogig) {
            geogig = cli.newGeoGIG(Hints.readOnly());
            closeIt = true;
        }
        try {
            if (resolve_geogig_dir) {
                resolveGeogigDir(cli.getConsole(), geogig);
            } else if (is_inside_work_tree) {
                isInsideWorkTree(cli.getConsole(), geogig);
            }
        } finally {
            if (closeIt) {
                geogig.close();
            }
        }
    }

    private void isInsideWorkTree(ConsoleReader console, GeoGIG geogig) throws IOException {
        Optional<URL> repoUrl = geogig.command(ResolveGeogigDir.class).call();

        File pwd = geogig.getPlatform().pwd();

        if (repoUrl.isPresent()) {
            boolean insideWorkTree = !pwd.getAbsolutePath().contains(".geogig");
            console.println(String.valueOf(insideWorkTree));
        } else {
            console.println("Error: not a geogig repository (or any parent) '"
                    + pwd.getAbsolutePath() + "'");
        }
    }

    private void resolveGeogigDir(ConsoleReader console, GeoGIG geogig) throws IOException {

        URL repoUrl = geogig.command(ResolveGeogigDir.class).call().orNull();
        if (null == repoUrl) {
            File currDir = geogig.getPlatform().pwd();
            console.println("Error: not a geogig dir '"
                    + currDir.getCanonicalFile().getAbsolutePath() + "'");
        } else if ("file".equals(repoUrl.getProtocol())) {
            try {
                console.println(new File(repoUrl.toURI()).getCanonicalFile().getAbsolutePath());
            } catch (URISyntaxException e) {
                Throwables.propagate(e);
            }
        } else {
            console.println(repoUrl.toExternalForm());
        }
    }

}
