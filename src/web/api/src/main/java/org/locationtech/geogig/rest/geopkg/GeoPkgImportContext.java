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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;

import org.geotools.data.DataStore;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.locationtech.geogig.rest.geotools.ImportContextService;
import org.locationtech.geogig.rest.repository.UploadCommandResource;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

import com.google.common.base.Supplier;

/**
 * Geopackage specific implementation of ImportContextService.
 */
public class GeoPkgImportContext implements ImportContextService {

    private static final String SUPPORTED_FORMAT = "gpkg";

    private DataStoreSupplier dataStoreSupplier;

    @Override
    public boolean accepts(String format) {
        return SUPPORTED_FORMAT.equals(format);
    }

    @Override
    public String getCommandDescription() {
        return "Importing Geopkg database file.";
    }

    @Override
    public Supplier<DataStore> getDataStore(ParameterSet options) {
        if (dataStoreSupplier == null) {
            dataStoreSupplier = new DataStoreSupplier(options);
        }
        return dataStoreSupplier;
    }

    private static class DataStoreSupplier implements Supplier<DataStore> {

        private JDBCDataStore dataStore;
        private final ParameterSet options;

        DataStoreSupplier(ParameterSet options) {
            super();
            this.options = options;
        }

        @Override
        public DataStore get() {
            if (null == dataStore) {
                // build one
                createDataStore();
            }
            return dataStore;
        }

        private void createDataStore() {
            final GeoPkgDataStoreFactory factory = new GeoPkgDataStoreFactory();
            final HashMap<String, Serializable> params = new HashMap<>(3);
            final File uploadedFile = options.getUploadedFile();
            if (uploadedFile == null) {
                throw new CommandSpecException("Request must specify "
                    + UploadCommandResource.UPLOAD_FILE_KEY + " in the request body");
            }
            // fill in DataStore parameters
            params.put(GeoPkgDataStoreFactory.DBTYPE.key, "geopkg");
            params.put(GeoPkgDataStoreFactory.DATABASE.key, uploadedFile.getAbsolutePath());
            params.put(GeoPkgDataStoreFactory.USER.key,
                options.getFirstValue("user", "user"));
            try {
                dataStore = factory.createDataStore(params);
            } catch (IOException ioe) {
                throw new RuntimeException("Unable to create GeoPkgDataStore", ioe);
            }
            if (null == dataStore) {
                throw new CommandSpecException(
                    "Unable to create GeoPkgDataStore from uploaded file.");
            }
            // get a connection to initialize the DataStore, then safely close it
            Connection con;
            try {
                con = dataStore.getDataSource().getConnection();
            } catch (SQLException sqle) {
                throw new RuntimeException("Unable to get a connection to GeoPkgDataStore", sqle);
            }
            dataStore.closeSafe(con);
        }

    }
}
