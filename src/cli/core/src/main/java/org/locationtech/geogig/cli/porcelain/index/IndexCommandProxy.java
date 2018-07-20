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

import org.locationtech.geogig.cli.CLICommandExtension;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;

/**
 * {@link CLICommandExtension} that provides a {@link JCommander} for Index specific commands.
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig index <command> <args>...}
 * </ul>
 */
@Parameters(commandNames = "index", commandDescription = "Indexing command utilities")
public class IndexCommandProxy implements CLICommandExtension {

    /**
     * @return the JCommander parser for this extension
     * @see JCommander
     */
    @Override
    public JCommander getCommandParser() {
        JCommander commander = new JCommander();
        commander.setProgramName("geogig index");
        commander.addCommand("create", new CreateIndex());
        commander.addCommand("update", new UpdateIndex());
        commander.addCommand("list", new ListIndexes());
        commander.addCommand("rebuild", new RebuildIndex());
        commander.addCommand("drop", new DropIndex());

        return commander;
    }
}
