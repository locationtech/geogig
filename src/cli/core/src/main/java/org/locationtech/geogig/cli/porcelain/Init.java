/* Copyright (c) 2012-2016 Boundless and others.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.impl.GeoGIG;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
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
 * <li>{@code geogig init [<directory>]}
 * </ul>
 * 
 * @see InitOp
 */
@RequiresRepository(false)
@Parameters(commandNames = "init", commandDescription = "Create an empty geogig repository or reinitialize an existing one")
public class Init extends AbstractCommand implements CLICommand {

    @Parameter(description = "Repository location (directory).", required = false, arity = 1)
    private List<String> location = new ArrayList<>(1);

    @Parameter(names = {
            "--config" }, description = "Extra configuration options to set while preparing repository. Separate names from values with an equals sign and delimit configuration options with a colon. Example: storage.objects=rocksdb,storage.graph=rocksdb,rocksdb.version=1")
    private String config;

    /**
     * Executes the init command.
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        final String providedUri = location.isEmpty() ? null : location.get(0);

        final URI targetURI;

        // argument location if provided, or current directory otherwise
        if (providedUri == null) {
            if (cli.getRepositoryURI() == null) {
                // current dir
                File currDir = cli.getPlatform().pwd();
                targetURI = currDir.getAbsoluteFile().getCanonicalFile().toURI();
            } else {
                targetURI = URI.create(cli.getRepositoryURI());
            }
        } else {
            Preconditions.checkArgument(cli.getRepositoryURI() == null,
                    "--repo <uri> argument and <location> argument are mutually exclusive. Provide only one");

            URI uri;
            try {
                uri = new URI(providedUri);
            } catch (URISyntaxException e) {
                File f = new File(providedUri);
                if (!f.isAbsolute()) {
                    f = new File(cli.getPlatform().pwd(), providedUri);
                }
                uri = f.toURI();
            }
            if (Strings.isNullOrEmpty(uri.getScheme()) || "file".equals(uri.getScheme())) {
                File file = Strings.isNullOrEmpty(uri.getScheme()) ? new File(providedUri)
                        : new File(uri);
                if (!file.isAbsolute()) {
                    File currDir = cli.getPlatform().pwd();
                    file = new File(currDir, providedUri).getCanonicalFile();
                }
                targetURI = file.getAbsoluteFile().toURI();
            } else {
                targetURI = uri;
            }
        }

        final boolean repoExisted = RepositoryResolver.lookup(targetURI).repoExists(targetURI);

        // let cli set up Hints with the appropriate URI
        Hints hints = new Hints();
        hints.set(Hints.REPOSITORY_URL, targetURI);

        final Repository repository;
        {
            final Map<String, String> suppliedConfiguration = splitConfig(config);
            GeoGIG geogig = cli.newGeoGIG(hints);
            try {
                repository = geogig.command(InitOp.class).setConfig(suppliedConfiguration).call();
                repository.close();
            } catch (IllegalArgumentException e) {
                throw new InvalidParameterException(e.getMessage(), e);
            } finally {
                geogig.close();
            }
        }

        final URI repoURI = repository.getLocation();

        String message;
        String locationStr = "file".equals(repoURI.getScheme())
                ? new File(repoURI).getAbsolutePath() : repoURI.toString();
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
