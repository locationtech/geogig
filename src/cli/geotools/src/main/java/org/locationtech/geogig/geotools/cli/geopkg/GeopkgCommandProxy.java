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

import org.locationtech.geogig.cli.CLICommandExtension;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;

/**
 * {@link CLICommandExtension} that provides a {@link JCommander} for geppackage specific commands.
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig geopkg <command> <args>...}
 * </ul>
 * 
 * @see GeopkgImport
 * @see GeopkgList
 * @see GeopkgExport
 * @see GeopkgDescribe
 */
@Parameters(commandNames = "geopkg", commandDescription = "GeoGig/Geopackage integration utilities")
public class GeopkgCommandProxy implements CLICommandExtension {

    /**
     * @return the JCommander parser for this extension
     * @see JCommander
     */
    @Override
    public JCommander getCommandParser() {
        JCommander commander = new JCommander();
        commander.setProgramName("geogig geopkg");
        commander.addCommand("import", new GeopkgImport());
        commander.addCommand("list", new GeopkgList());
        commander.addCommand("describe", new GeopkgDescribe());
        commander.addCommand("export", new GeopkgExport());
        commander.addCommand("pull", new GeopkgPull());
        return commander;
    }
}
