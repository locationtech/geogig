/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.geotools.cli.porcelain;

import java.io.IOException;
import java.util.List;

import org.geotools.data.DataStore;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException;
import org.locationtech.geogig.geotools.plumbing.ListOp;

import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;

/**
 * Lists tables from an Oracle database.
 * 
 * Oracle CLI proxy for {@link ListOp}
 * 
 * @see ListOp
 */
@ReadOnly
@Parameters(commandNames = "list", commandDescription = "List available feature types in a database")
public class OracleList extends AbstractOracleCommand implements CLICommand {

    /**
     * Executes the list command using the provided options.
     * 
     * @param cli
     * @see org.locationtech.geogig.geotools.cli.porcelain.AbstractOracleCommand#runInternal(org.locationtech.geogig.cli.GeogigCLI)
     */
    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {

        DataStore dataStore = getDataStore();

        try {
            cli.getConsole().println("Fetching feature types...");

            Optional<List<String>> features = cli.getGeogig().command(ListOp.class)
                    .setDataStore(dataStore).call();

            if (features.isPresent()) {
                for (String featureType : features.get()) {
                    cli.getConsole().println(" - " + featureType);
                }
            } else {
                throw new CommandFailedException(
                        "No features types were found in the specified database.");
            }
        } catch (GeoToolsOpException e) {
            throw new CommandFailedException("Unable to get feature types from the database.");
        } finally {
            dataStore.dispose();
            cli.getConsole().flush();
        }
    }

}
