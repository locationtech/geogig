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

import org.locationtech.geogig.cli.CLISubCommand;

import lombok.AccessLevel;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

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

@Command(name = "geojson", description = "GeoGig/GeoJSON integration utilities", //
        subcommands = { GeoJsonImport.class, GeoJsonExport.class })
public class GeoJsonCommandProxy extends CLISubCommand {

    private @Spec @Getter(value = AccessLevel.PROTECTED) CommandSpec spec;

}
