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

import org.locationtech.geogig.cli.CLISubCommand;

import lombok.AccessLevel;
import lombok.Getter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * {@link CLICommandExtension} that provides {@link CommandLine} {@link Command}s for remote
 * specific commands.
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig remote <command> <args>...}
 * </ul>
 * 
 * @see RemoteAdd
 * @see RemoteRemove
 * @see RemoteList
 */
@Command(name = "remote", description = "remote utilities", //
        subcommands = { RemoteAdd.class, RemoteRemove.class, RemoteList.class })
public class RemoteExtension extends CLISubCommand {
    private @Spec @Getter(value = AccessLevel.PROTECTED) CommandSpec spec;

}
