/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli;

import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.cli.porcelain.Config;
import org.locationtech.geogig.cli.porcelain.Init;

import lombok.NonNull;
import picocli.CommandLine;
import picocli.CommandLine.Help;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Base interface for command executed through the command line interface.
 * <p>
 * Command classes that require a live geogig repository to exist in order to be run are encouraged
 * to be marked with the {@link RequiresRepository @RequiresRepository} annotation, to be sure
 * {@link #run(GeogigCLI)} is only going to be called with a valid repository in place.
 * <p>
 * Commands that don't necessarily require a repository to run (e.g. {@link Init init}, {@link Help
 * help}, {@link Config config}, etc} shall not be annotated with {@link RequiresRepository},
 * although they're free to check {@link GeogigCLI#getGeogig()} for nullity if they need to perform
 * one or another task depending on the presence or not of a repository.
 * 
 */
public abstract class CLISubCommand implements CLICommand {

    protected abstract @NonNull CommandSpec getSpec();

    public @Override void run() {
        CommandLine commandLine = getSpec().commandLine();
        commandLine.usage(commandLine.getOut());
    }

    public @Override void run(GeogigCLI cli)
            throws InvalidParameterException, CommandFailedException {
        // TODO Auto-generated method stub

    }
}
