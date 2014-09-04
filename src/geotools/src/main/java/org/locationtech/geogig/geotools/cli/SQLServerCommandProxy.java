/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Juan Marin (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli;

import org.locationtech.geogig.cli.CLICommandExtension;
import org.locationtech.geogig.geotools.cli.porcelain.SQLServerDescribe;
import org.locationtech.geogig.geotools.cli.porcelain.SQLServerExport;
import org.locationtech.geogig.geotools.cli.porcelain.SQLServerImport;
import org.locationtech.geogig.geotools.cli.porcelain.SQLServerList;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;

/**
 * {@link CLICommandExtension} that provides a {@link JCommander} for SQL Server specific commands.
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig sqlserver <command> <args>...}
 * </ul>
 * 
 * @see SQLServerImport
 * @see SQLServerList
 * @see SQLServerDescribe
 * @see SQLServerExport
 */
@Parameters(commandNames = "sqlserver", commandDescription = "GeoGig/SQL Server integration utilities")
public class SQLServerCommandProxy implements CLICommandExtension {

    /**
     * @return the JCommander parser for this extension
     * @see JCommander
     */
    @Override
    public JCommander getCommandParser() {
        JCommander commander = new JCommander();
        commander.setProgramName("geogig sqlserver");
        commander.addCommand("import", new SQLServerImport());
        commander.addCommand("list", new SQLServerList());
        commander.addCommand("describe", new SQLServerDescribe());
        commander.addCommand("export", new SQLServerExport());

        return commander;
    }
}
