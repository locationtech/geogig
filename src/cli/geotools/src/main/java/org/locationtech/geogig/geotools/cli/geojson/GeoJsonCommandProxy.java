/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Juan Marin (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geojson;

import org.locationtech.geogig.cli.CLICommandExtension;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;

/**
 * {@link CLICommandExtension} that provides a {@link JCommander} for GeoJSON specific commands.
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig geojson <command> <args>...}
 * </ul>
 * 
 * @see GeoJsonImport
 */

@Parameters(commandNames = "geojson", commandDescription = "GeoGig/GeoJSON integration utilities")
public class GeoJsonCommandProxy implements CLICommandExtension {

    /**
     * @return the JCommander parser for this extension
     * @see JCommander
     */
    @Override
    public JCommander getCommandParser() {
        JCommander commander = new JCommander();
        commander.setProgramName("geogig geojson");
        commander.addCommand("import", new GeoJsonImport());
        commander.addCommand("export", new GeoJsonExport());
        return commander;
    }

}
