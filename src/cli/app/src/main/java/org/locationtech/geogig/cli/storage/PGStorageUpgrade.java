/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.storage;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.repository.impl.PluginsContextBuilder;
import org.locationtech.geogig.storage.postgresql.commands.PGDatabaseUpgrade;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Stopwatch;

@RequiresRepository(false)
@Parameters(commandNames = "postgres-upgrade", commandDescription = "Upgrade the schema of a geogig PostgreSQL databse to the latest version")
public class PGStorageUpgrade extends AbstractCommand implements CLICommand {

    @Parameter(description = "<base URI> The URI without a repository name. (e.g. geogig postgres-upgrade postgresql://localhost:5432/geogig_db?user=...&password=...)", arity = 1)
    private List<URI> baseuri = new ArrayList<>();

    protected @Override void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(!baseuri.isEmpty(),
                "Usage: geogig postgres-upgrade <base URI> (e.g. geogig postgres-upgrade postgresql://localhost:5432/geogig_db?user=...&password=...)");

        URI baseURI = baseuri.get(0);
        new PGDatabaseUpgrade().setBaseURI(baseURI).setProgressListener(cli.getProgressListener())
                .call();
    }

    public static void main(String... args) {
        Stopwatch sw = Stopwatch.createStarted();
        GlobalContextBuilder.builder(new PluginsContextBuilder());
        URI base = URI.create("postgresql://localhost:5432/test?user=postgres&password=geo123");
        ProgressListener listener = new GeogigCLI(new Console()).getProgressListener();
        new PGDatabaseUpgrade().setBaseURI(base).setProgressListener(listener).call();
        System.err.println("done in " + sw.stop());
    }

}
