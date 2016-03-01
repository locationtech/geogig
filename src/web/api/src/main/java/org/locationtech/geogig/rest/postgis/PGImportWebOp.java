/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rest.postgis;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.data.postgis.PostgisNGDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.locationtech.geogig.rest.geotools.ImportWebOp;
import org.restlet.data.Form;

import com.beust.jcommander.internal.Maps;

public class PGImportWebOp extends ImportWebOp {

    @Override
    public DataStore getDataStore(Form options) {
        DataStoreFactorySpi dataStoreFactory = new PostgisNGDataStoreFactory();
        final String host = options.getFirstValue("host", "localhost");
        final String port = options.getFirstValue("port", "5432");
        final String schema = options.getFirstValue("schema", "public");
        final String database = options.getFirstValue("database", "database");
        final String user = options.getFirstValue("user", "postgres");
        final String password = options.getFirstValue("password", "");

        Map<String, Serializable> params = Maps.newHashMap();
        params.put(PostgisNGDataStoreFactory.DBTYPE.key, "postgis");
        params.put(PostgisNGDataStoreFactory.HOST.key, host);
        params.put(PostgisNGDataStoreFactory.PORT.key, port);
        params.put(PostgisNGDataStoreFactory.SCHEMA.key, schema);
        params.put(PostgisNGDataStoreFactory.DATABASE.key, database);
        params.put(PostgisNGDataStoreFactory.USER.key, user);
        params.put(PostgisNGDataStoreFactory.PASSWD.key, password);
        params.put(PostgisNGDataStoreFactory.FETCHSIZE.key, 1000);
        params.put(PostgisNGDataStoreFactory.EXPOSE_PK.key, true);

        DataStore dataStore;
        try {
            dataStore = dataStoreFactory.createDataStore(params);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to connect using the specified database parameters.", e);
        }
        if (dataStore == null) {
            throw new RuntimeException(
                    "Unable to connect using the specified database parameters.");
        }
        if (dataStore instanceof JDBCDataStore) {
            Connection con = null;
            try {
                con = ((JDBCDataStore) dataStore).getDataSource().getConnection();
            } catch (SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
            ((JDBCDataStore) dataStore).closeSafe(con);
        }

        return dataStore;
    }

    @Override
    public String getCommandDescription(String table, boolean all, URI repo) {
        return String.format("postgis import table %s into repository: %s", table,
                repo);
    }
}
