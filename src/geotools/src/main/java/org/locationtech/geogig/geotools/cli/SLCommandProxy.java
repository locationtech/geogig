/*******************************************************************************
 * Copyright (c) 2012, 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.geotools.cli;

import org.locationtech.geogig.cli.CLICommandExtension;
import org.locationtech.geogig.geotools.cli.porcelain.SLDescribe;
import org.locationtech.geogig.geotools.cli.porcelain.SLExport;
import org.locationtech.geogig.geotools.cli.porcelain.SLImport;
import org.locationtech.geogig.geotools.cli.porcelain.SLList;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;

/**
 * {@link CLICommandExtension} that provides a {@link JCommander} for SpatiaLite specific commands.
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig sl <command> <args>...}
 * </ul>
 * 
 * @see SLImport
 * @see SLList
 * @see SLDescribe
 * @see SLExport
 */
@Parameters(commandNames = "sl", commandDescription = "GeoGig/SpatiaLite integration utilities")
public class SLCommandProxy implements CLICommandExtension {

    /**
     * @return the JCommander parser for this extension
     * @see JCommander
     */
    @Override
    public JCommander getCommandParser() {
        JCommander commander = new JCommander();
        commander.setProgramName("geogig sl");
        commander.addCommand("import", new SLImport());
        commander.addCommand("list", new SLList());
        commander.addCommand("describe", new SLDescribe());
        commander.addCommand("export", new SLExport());
        return commander;
    }
}
