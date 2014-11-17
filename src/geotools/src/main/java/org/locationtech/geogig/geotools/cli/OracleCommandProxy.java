/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.geotools.cli;

import org.locationtech.geogig.cli.CLICommandExtension;
import org.locationtech.geogig.geotools.cli.porcelain.OracleDescribe;
import org.locationtech.geogig.geotools.cli.porcelain.OracleExport;
import org.locationtech.geogig.geotools.cli.porcelain.OracleImport;
import org.locationtech.geogig.geotools.cli.porcelain.OracleList;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;

/**
 * {@link CLICommandExtension} that provides a {@link JCommander} for Oracle specific commands.
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig oracle <command> <args>...}
 * </ul>
 * 
 * @see OracleImport
 * @see OracleList
 * @see OracleDescribe
 * @see OracleExport
 */

@Parameters(commandNames = "oracle", commandDescription = "GeoGig/Oracle integration utilities")
public class OracleCommandProxy implements CLICommandExtension {

    /**
     * @return the JCommander parser for this extension
     * @see JCommander
     */
    @Override
    public JCommander getCommandParser() {
        JCommander commander = new JCommander();
        commander.setProgramName("geogig oracle");
        commander.addCommand("import", new OracleImport());
        commander.addCommand("list", new OracleList());
        commander.addCommand("describe", new OracleDescribe());
        commander.addCommand("export", new OracleExport());

        return commander;
    }
}
