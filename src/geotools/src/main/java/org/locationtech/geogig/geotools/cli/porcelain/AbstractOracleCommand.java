/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.geotools.cli.porcelain;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.util.Map;

import org.geotools.data.AbstractDataStoreFactory;
import org.geotools.data.DataStore;
import org.geotools.data.oracle.OracleNGDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;

import com.beust.jcommander.ParametersDelegate;
import com.google.common.collect.Maps;

/**
 * A template for Oracle commands; provides out of the box support for the --help argument so far.
 * 
 * @see CLICommand
 */
public abstract class AbstractOracleCommand extends AbstractCommand implements CLICommand {

    /**
     * Common arguments for Oracle commands.
     * 
     * @see OracleCommonArgs
     */
    @ParametersDelegate
    public OracleCommonArgs commonArgs = new OracleCommonArgs();

    /**
     * Factory for constructing the data store.
     * 
     * @see OracleNGDataStoreFactory
     */
    public AbstractDataStoreFactory dataStoreFactory = new OracleNGDataStoreFactory();

    /**
     * Constructs a new Oracle data store using connection parameters from {@link OracleCommonArgs}.
     * 
     * @return the constructed data store
     * @throws Exception
     * @see DataStore
     */
    protected DataStore getDataStore() {
        Map<String, Serializable> params = Maps.newHashMap();
        params.put(OracleNGDataStoreFactory.DBTYPE.key, "oracle");
        params.put(OracleNGDataStoreFactory.HOST.key, commonArgs.host);
        params.put(OracleNGDataStoreFactory.PORT.key, commonArgs.port.toString());
        params.put(OracleNGDataStoreFactory.SCHEMA.key, commonArgs.schema);
        params.put(OracleNGDataStoreFactory.DATABASE.key, commonArgs.database);
        params.put(OracleNGDataStoreFactory.USER.key, commonArgs.username);
        params.put(OracleNGDataStoreFactory.PASSWD.key, commonArgs.password);
        // params.put(OracleNGDataStoreFactory.ESTIMATED_EXTENTS.key, commonArgs.estimatedExtent);
        // params.put(OracleNGDataStoreFactory.LOOSEBBOX.key, commonArgs.looseBbox);
        // if (!commonArgs.geometryMetadataTable.equals(""))
        // params.put(OracleNGDataStoreFactory.GEOMETRY_METADATA_TABLE.key,
        // commonArgs.geometryMetadataTable);
        // params.put(OracleNGDataStoreFactory.FETCHSIZE.key, 1000);

        DataStore dataStore;
        try {
            dataStore = dataStoreFactory.createDataStore(params);
        } catch (IOException e) {
            throw new CommandFailedException(
                    "Unable to connect using the specified database parameters.", e);
        }
        if (dataStore == null) {
            throw new CommandFailedException(
                    "No suitable data store found for the provided parameters");
        }

        if (dataStore instanceof JDBCDataStore) {
            Connection con = null;
            try {
                con = ((JDBCDataStore) dataStore).getDataSource().getConnection();
            } catch (Exception e) {
                throw new CommandFailedException("Error validating the database connection", e);
            }
            ((JDBCDataStore) dataStore).closeSafe(con);
        }

        return dataStore;
    }

}
