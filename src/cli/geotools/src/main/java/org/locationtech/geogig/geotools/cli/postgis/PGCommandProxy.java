/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.postgis;

import org.locationtech.geogig.cli.CLISubCommand;

import lombok.AccessLevel;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * {@link CLICommandExtension} that provides a {@link JCommander} for PostGIS specific commands.
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig pg <command> <args>...}
 * </ul>
 * 
 * @see PGImport
 * @see PGList
 * @see PGDescribe
 * @see PGExport
 */
@Command(name = "pg", description = "GeoGig/PostGIS integration utilities", //
        subcommands = { PGImport.class, PGList.class, PGDescribe.class, PGExport.class })
public class PGCommandProxy extends CLISubCommand {
    private @Spec @Getter(value = AccessLevel.PROTECTED) CommandSpec spec;

    /**
     * Machine name or IP address to connect to. Default: localhost
     */
    @Option(names = { "--host",
            "-H" }, description = "Machine name or IP address to connect to.", defaultValue = "localhost", showDefaultValue = Visibility.ALWAYS)
    public String host = "localhost";

    /**
     * Port number to connect to. Default: 5432
     */
    @Option(names = { "--port",
            "-P" }, description = "Port number to connect to.", defaultValue = "5432", showDefaultValue = Visibility.ALWAYS)
    public Integer port = 5432;

    /**
     * The database schema to access. Default: public
     */
    @Option(names = { "--schema",
            "-S" }, description = "The database schema to access.", defaultValue = "public", showDefaultValue = Visibility.ALWAYS)
    public String schema = "public";

    /**
     * The database to connect to. Default: database
     */
    @Option(names = { "--database",
            "-D" }, description = "The database to connect to.", defaultValue = "database", showDefaultValue = Visibility.ALWAYS)
    public String database = "database";

    /**
     * User name. Default: postgres
     */
    @Option(names = { "--user",
            "-U" }, description = "User name.", defaultValue = "postgres", showDefaultValue = Visibility.ALWAYS)
    public String username = "postgres";

    /**
     * Password. Default: <no password>
     */
    @Option(names = { "--password", "-W" }, description = "Password.")
    public String password = "";

}
