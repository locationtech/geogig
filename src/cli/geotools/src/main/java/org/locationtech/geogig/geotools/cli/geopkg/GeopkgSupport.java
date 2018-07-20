/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geopkg;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;

import com.beust.jcommander.internal.Maps;

/**
 * Support class for Geopackage commands;
 * 
 * @see CLICommand
 */
class GeopkgSupport {

    /**
     * Factory for constructing the data store.
     * 
     * @see SpatiaLiteDataStoreFactory
     */
    public DataStoreFactorySpi dataStoreFactory = new GeoPkgDataStoreFactory();

    /**
     * Constructs a new SpatiaLite data store using connection parameters from {@link SLCommonArgs}.
     * 
     * @return the constructed data store
     * @throws CommandFailedException
     * @see DataStore
     */
    public DataStore getDataStore(GeopkgCommonArgs commonArgs) {
        Map<String, Serializable> params = Maps.newHashMap();
        params.put(GeoPkgDataStoreFactory.DBTYPE.key, "geopkg");
        params.put(GeoPkgDataStoreFactory.DATABASE.key, commonArgs.database);
        params.put(GeoPkgDataStoreFactory.USER.key, commonArgs.username);

        try {
            DataStore dataStore = dataStoreFactory.createDataStore(params);

            if (dataStore == null) {
                throw new CommandFailedException(
                        "Unable to connect using the specified database parameters.");
            }

            if (dataStore instanceof JDBCDataStore) {
                try (Connection con =((JDBCDataStore) dataStore).getDataSource().getConnection()) {
                    ((JDBCDataStore) dataStore).closeSafe(con);
                }
            }
            return dataStore;
        } catch (IOException e) {
            throw new CommandFailedException(
                    "Unable to connect using the specified database parameters.", e);
        } catch (SQLException e) {
            throw new CommandFailedException("Unable to validate the database connection.", e);
        }
    }
}
