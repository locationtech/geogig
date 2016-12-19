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

import org.locationtech.geogig.cli.CLICommandExtension;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;

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
@Parameters(commandNames = "pg", commandDescription = "GeoGig/PostGIS integration utilities")
public class PGCommandProxy implements CLICommandExtension {

    /**
     * @return the JCommander parser for this extension
     * @see JCommander
     */
    @Override
    public JCommander getCommandParser() {
        JCommander commander = new JCommander();
        commander.setProgramName("geogig pg");
        commander.addCommand("import", new PGImport());
        commander.addCommand("list", new PGList());
        commander.addCommand("describe", new PGDescribe());
        commander.addCommand("export", new PGExport());

        return commander;
    }
}
