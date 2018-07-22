/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli.remoting;

import org.locationtech.geogig.cli.CLICommandExtension;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;

/**
 * {@link CLICommandExtension} that provides a {@link JCommander} for remote specific commands.
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig remote <command> <args>...}
 * </ul>
 * 
 * @see RemoteAdd
 * @see RemoteRemove
 * @see RemoteList
 */
@Parameters(commandNames = "remote", commandDescription = "remote utilities")
public class RemoteExtension implements CLICommandExtension {

    /**
     * @return the JCommander parser for this extension
     * @see JCommander
     */
    @Override
    public JCommander getCommandParser() {
        JCommander commander = new JCommander(this);
        commander.setProgramName("geogig remote");
        commander.addCommand("add", new RemoteAdd());
        commander.addCommand("rm", new RemoteRemove());
        commander.addCommand("list", new RemoteList());
        return commander;
    }
}
