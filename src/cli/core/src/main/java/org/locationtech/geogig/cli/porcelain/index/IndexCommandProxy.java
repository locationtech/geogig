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

import org.locationtech.geogig.cli.CLISubCommand;

import lombok.AccessLevel;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * {@link CLICommandExtension} that provides a {@link JCommander} for Index specific commands.
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig index <command> <args>...}
 * </ul>
 */
@Command(name = "index", description = "Indexing command utilities" //
        , mixinStandardHelpOptions = true//
        , subcommands = { CreateIndex.class, UpdateIndex.class, ListIndexes.class,
                RebuildIndex.class, DropIndex.class })
public class IndexCommandProxy extends CLISubCommand {
    private @Spec @Getter(value = AccessLevel.PROTECTED) CommandSpec spec;

}
