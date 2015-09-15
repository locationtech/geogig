/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.porcelain.InitOp;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryInitializer;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.Maps;

/**
 * This command creates an empty geogig repository - basically a .geogig directory with
 * subdirectories for the object, refs, index, and config databases. An initial HEAD that references
 * the HEAD of the master branch is also created.
 * <p>
 * CLI proxy for {@link InitOp}
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig init [<directory>]}
 * </ul>
 * 
 * @see InitOp
 */
@RequiresRepository(false)
@Parameters(commandNames = "init", commandDescription = "Create an empty geogig repository or reinitialize an existing one")
public class Init extends AbstractCommand implements CLICommand {

    @Parameter(description = "Repository location (directory).", required = false, arity = 1)
    private List<String> location;

    @Parameter(names = { "--config" }, description = "Extra configuration options to set while preparing repository. Separate names from values with an equals sign and delimit configuration options with a colon. Example: storage.objects=bdbje,storage.graph=bdbje,bdbje.version=0.1")
    private String config;

    /**
     * Executes the init command.
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        // argument location if provided, or current directory otherwise
        final File targetDirectory;
        {
            File currDir = cli.getPlatform().pwd();
            if (location != null && location.size() == 1) {
                String target = location.get(0);
                File f = new File(target);
                if (!f.isAbsolute()) {
                    f = new File(currDir, target).getCanonicalFile();
                }
                targetDirectory = f;
            } else {
                targetDirectory = currDir;
            }
        }

        URI uri;
        try {
            uri = super.repo == null ? targetDirectory.toURI() : new URI(super.repo);
        } catch (URISyntaxException e) {
            throw new CommandFailedException("--repo argument can't be parsed to a URI", e);
        }
        final boolean repoExisted = RepositoryInitializer.lookup(uri).repoExists(uri);

        final Repository repository;
        {
            final Map<String, String> suppliedConfiguration = splitConfig(config);

            GeoGIG geogig = cli.getGeogig();
            if (geogig == null) {
                Context geogigInjector = cli.getGeogigInjector();
                geogig = new GeoGIG(geogigInjector);
            }

            try {
                repository = geogig.command(InitOp.class).setConfig(suppliedConfiguration)
                        .setTarget(targetDirectory).call();
                repository.close();
            } catch (IllegalArgumentException e) {
                throw new CommandFailedException(e.getMessage(), e);
            } finally {
                geogig.close();
            }
        }

        final URI repoURI = repository.getLocation();

        String message;
        String locationStr = "file".equals(repoURI.getScheme()) ? new File(repoURI)
                .getAbsolutePath() : repoURI.toString();
        message = (repoExisted ? "Reinitialized existing" : "Initialized empty")
                + " Geogig repository in " + locationStr;

        cli.getConsole().println(message);
    }

    public static Map<String, String> splitConfig(final String configArg) {
        Map<String, String> configProps = Maps.newTreeMap();
        if (configArg != null) {
            String[] options = configArg.split(",");
            for (String option : options) {
                String[] kv = option.split("=", 2);
                if (kv.length < 2)
                    continue;
                configProps.put(kv[0], kv[1]);
            }
        }
        return configProps;
    }
}
