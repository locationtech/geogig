/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Michael Fawcett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.cli.annotation.RequiresRepository;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * This command displays the usage for GeoGig or a specific command if provided.
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig [--]help [<command>]}
 * </ul>
 */
@ReadOnly
@RequiresRepository(false)
@Parameters(commandNames = { "--help", "help" }, commandDescription = "Print this help message, or provide a command name to get help for")
public class Help implements CLICommand {

    @Parameter
    private List<String> parameters = new ArrayList<String>();

    @Parameter(names = { "-a" }, description = "Show all commands")
    private boolean all;

    /**
     * Executes the help command.
     * 
     * @param cli
     * @see org.locationtech.geogig.cli.CLICommand#run(org.locationtech.geogig.cli.GeogigCLI)
     */
    // @Override
    public void run(GeogigCLI cli) {

        JCommander jc = cli.newCommandParser();

        if (all) {
            cli.printCommandList(jc);
        } else {
            if (parameters.isEmpty()) {
                cli.printShortCommandList(jc);
            } else {
                String command = parameters.get(0);
                jc.usage(command);
            }
        }
    }

}
