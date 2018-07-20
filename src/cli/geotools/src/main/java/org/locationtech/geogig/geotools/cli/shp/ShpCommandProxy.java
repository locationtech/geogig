/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.shp;

import org.locationtech.geogig.cli.CLICommandExtension;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;

/**
 * {@link CLICommandExtension} that provides a {@link JCommander} for shapefile specific commands.
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig shp <command> <args>...}
 * </ul>
 * 
 * @see ShpImport
 */
@Parameters(commandNames = "shp", commandDescription = "GeoGig/Shapefile integration utilities")
public class ShpCommandProxy implements CLICommandExtension {

    /**
     * @return the JCommander parser for this extension
     * @see JCommander
     */
    @Override
    public JCommander getCommandParser() {
        JCommander commander = new JCommander();
        commander.setProgramName("geogig shp");
        commander.addCommand("import", new ShpImport());
        commander.addCommand("export", new ShpExport());
        commander.addCommand("export-diff", new ShpExportDiff());
        commander.addCommand("describe", new ShpDescribe());
        return commander;
    }
}
