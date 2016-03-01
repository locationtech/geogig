/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geopkg;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;

import org.geotools.data.DataStore;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.locationtech.geogig.rest.geotools.ImportWebOp;
import org.restlet.data.Form;

/**
 * Geopackage Import Web Op.
 */
public class GeoPkgImportWebOp extends ImportWebOp {

    @Override
    public DataStore getDataStore(Form options) {
        final GeoPkgDataStoreFactory factory = new GeoPkgDataStoreFactory();
        final HashMap<String, Serializable> params = new HashMap<>(3);
        // fill in DataStore parameters
        params.put(GeoPkgDataStoreFactory.DBTYPE.key, "geopkg");
        params.put(GeoPkgDataStoreFactory.DATABASE.key,
            options.getFirstValue("database", "database.geopkg"));
        params.put(GeoPkgDataStoreFactory.USER.key,
            options.getFirstValue("user", "user"));
        JDBCDataStore dataStore;
        try {
            dataStore = factory.createDataStore(params);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create GeoPkgDataStore", ioe);
        }
        if (null == dataStore) {
            throw new RuntimeException("Unable to create GeoPkgDataStore");
        }
        // get a connection to initialize the DataStore, then safely close it
        Connection con;
        try {
            con = dataStore.getDataSource().getConnection();
        } catch (SQLException sqle) {
            throw new RuntimeException("Unable to get a connection to GeoPkgDataStore", sqle);
        }
        dataStore.closeSafe(con);
        return dataStore;
    }

    @Override
    public String getCommandDescription(String table, boolean all, URI repo) {
        return String.format("geopackage import table %s into repository: %s",
            (!all & table != null) ? table : "[all tables]",
            repo);
    }
}
