/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.gui.cli;

import java.io.IOException;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.gui.internal.MapPane;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeoGIG;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.Lists;

@RequiresRepository(true)
@Parameters(commandNames = "map", commandDescription = "Opens a map")
public class Map extends AbstractCommand {

    @Parameter(description = "<layer names>,...")
    private List<String> layerNames = Lists.newArrayList();

    @Override
    protected void runInternal(GeogigCLI cli) {
        GeoGIG geogig = cli.newGeoGIG(Hints.readOnly());
        MapPane mapPane;
        try {
            Repository repository = geogig.getRepository();
            mapPane = new MapPane(repository, layerNames);
            mapPane.show();
            cli.setExitOnFinish(false);
        } catch (IOException e) {
            throw new CommandFailedException(e);
        }
    }

}
