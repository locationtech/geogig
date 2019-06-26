/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless Spatial) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geopkg;

import org.locationtech.geogig.cli.CLISubCommand;

import lombok.AccessLevel;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * {@link CLICommandExtension} that provides a {@link JCommander} for geppackage specific commands.
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig geopkg <command> <args>...}
 * </ul>
 */
@Command(name = "geopkg", aliases = "gp", description = "GeoGig/Geopackage integration utilities", //
        subcommands = { GeopkgImport.class, GeopkgList.class, GeopkgDescribe.class,
                GeopkgExport.class, GeopkgPull.class })
public class GeopkgCommandProxy extends CLISubCommand {

    private @Spec @Getter(value = AccessLevel.PROTECTED) CommandSpec spec;

    @Option(names = { "--database",
            "-D" }, description = "The database to connect to.  Default: database.gpkg")
    public String database = "database.gpkg";

    @Option(names = { "--user", "-U" }, description = "User name.  Default: user")
    public String username = "user";
}
