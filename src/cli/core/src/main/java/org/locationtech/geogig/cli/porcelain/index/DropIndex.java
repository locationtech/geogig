/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain.index;

import java.io.IOException;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.porcelain.index.DropIndexOp;
import org.locationtech.geogig.repository.Repository;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@RequiresRepository(true)
@Parameters(commandNames = { "drop" }, commandDescription = "Remove an index from the repository.")
public class DropIndex extends AbstractCommand implements CLICommand {

    @Parameter(names = "--tree", required = true, description = "Name or path of the feature tree of the index to drop.")
    private String treeRefSpec;

    @Parameter(names = { "-a",
            "--attribute" }, description = "Indexed attribute of the index to drop.")
    private String attribute;

    @Override
    protected void runInternal(GeogigCLI cli)
            throws InvalidParameterException, CommandFailedException, IOException {

        Repository repo = cli.getGeogig().getRepository();

        try {
            repo.command(DropIndexOp.class)//
                    .setTreeRefSpec(treeRefSpec)//
                    .setAttributeName(attribute)//
                    .setProgressListener(cli.getProgressListener())//
                    .call();
        } catch (IllegalStateException e) {
            throw new CommandFailedException(e);
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException(e.getMessage(), e);
        }

        cli.getConsole().println("Index successfully dropped.");

    }
}
