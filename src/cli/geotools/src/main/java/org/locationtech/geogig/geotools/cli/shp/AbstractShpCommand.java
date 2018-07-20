/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.shp;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;

import com.beust.jcommander.internal.Maps;

/**
 * A template for shapefile commands; provides out of the box support for the --help argument so
 * far.
 * 
 * @see CLICommand
 */
public abstract class AbstractShpCommand extends AbstractCommand implements CLICommand {

    /**
     * Factory for constructing the data store.
     * 
     * @see ShapefileDataStoreFactory
     */
    public DataStoreFactorySpi dataStoreFactory = new ShapefileDataStoreFactory();

    /**
     * Constructs a new shapefile data store using the specified shapefile.
     * 
     * @param shapefile the filepath of the shapefile to use in creating the data store
     * @return the constructed data store
     * @throws IllegalArgumentException if the datastore cannot be acquired
     * @see DataStore
     */
    protected DataStore getDataStore(String shapefile, String charset) {
        File file = new File(shapefile);
        checkParameter(file.exists(), "File does not exist '%s'", shapefile);

        try {
            Map<String, Serializable> params = Maps.newHashMap();
            params.put(ShapefileDataStoreFactory.URLP.key, new File(shapefile).toURI().toURL());
            params.put(ShapefileDataStoreFactory.NAMESPACEP.key, "http://www.opengis.net/gml");
            params.put(ShapefileDataStoreFactory.CREATE_SPATIAL_INDEX.key, Boolean.FALSE);
            params.put(ShapefileDataStoreFactory.ENABLE_SPATIAL_INDEX.key, Boolean.FALSE);
            params.put(ShapefileDataStoreFactory.MEMORY_MAPPED.key, Boolean.FALSE);
            params.put(ShapefileDataStoreFactory.DBFCHARSET.key, charset);

            DataStore dataStore = dataStoreFactory.createDataStore(params);
            checkParameter(dataStore != null, "Unable to open '%s' as a shapefile", shapefile);

            return dataStore;
        } catch (IOException e) {
            throw new CommandFailedException("Error opening shapefile: " + e.getMessage(), e);
        }
    }
}