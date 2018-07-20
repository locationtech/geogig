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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.geotools.data.DataStore;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.FeatureCollection;
import org.geotools.geojson.feature.FeatureJSON;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;

public abstract class AbstractGeoJsonCommand extends AbstractCommand implements CLICommand {

    protected DataStore getDataStore(String geoJSON) throws FileNotFoundException, IOException {
        try {
            File geoJsonfile = new File(geoJSON);
            checkParameter(geoJsonfile.exists(), "File does not exist '%s'", geoJsonfile);
            InputStream in = new FileInputStream(geoJsonfile);
            FeatureJSON fjson = new FeatureJSON();
            @SuppressWarnings("rawtypes")
            FeatureCollection fc = fjson.readFeatureCollection(in);
            MemoryDataStore dataStore = new MemoryDataStore();
            dataStore.addFeatures((SimpleFeatureIterator) fc.features());
            return dataStore;
        } catch (IOException ioe) {
            throw new CommandFailedException("Error opening GeoJSON: " + ioe.getMessage(), ioe);
        }
    }

}
