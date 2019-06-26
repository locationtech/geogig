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

import org.locationtech.geogig.cli.CLISubCommand;

import lombok.AccessLevel;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

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
@Command(name = "shp", description = "GeoGig/Shapefile integration utilities", //
        subcommands = { ShpImport.class, ShpExport.class, ShpExportDiff.class, ShpDescribe.class })
public class ShpCommandProxy extends CLISubCommand {
    private @Spec @Getter(value = AccessLevel.PROTECTED) CommandSpec spec;

}
