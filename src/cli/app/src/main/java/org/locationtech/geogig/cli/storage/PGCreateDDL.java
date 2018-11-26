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
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.storage.postgresql.commands.CreateDDL;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@RequiresRepository(false)
@Parameters(commandNames = "postgres-ddl", commandDescription = "Creates a DDL script to initialize a Geogig PostgreSQL database")
public class PGCreateDDL extends AbstractCommand implements CLICommand {

    @Parameter(description = "<base URI> The URI without a repository name. (e.g. geogig postgres-ddl postgresql://localhost:5432/geogig_db?user=...&password=...)", arity = 1)
    private List<URI> baseuri = new ArrayList<>();

    protected @Override void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(!baseuri.isEmpty(),
                "Usage: geogig ls-repos <base URI> (e.g. geogig ls-repos postgresql://localhost:5432/geogig_db?user=...&password=...)");

        URI baseURI = baseuri.get(0);
        List<String> statements = new CreateDDL().setBaseURI(baseURI).call();
        for (String st : statements) {
            cli.getConsole().println(st);
        }
    }
}
