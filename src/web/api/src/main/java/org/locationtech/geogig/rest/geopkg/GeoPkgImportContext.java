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
import java.util.HashMap;

import org.geotools.data.DataStore;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.locationtech.geogig.geotools.plumbing.DataStoreImportOp.DataStoreSupplier;
import org.locationtech.geogig.rest.geotools.DataStoreImportContextService;
import org.locationtech.geogig.rest.repository.UploadCommandResource;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

/**
 * Geopackage specific implementation of {@link DataStoreImportContextService}.
 */
public class GeoPkgImportContext implements DataStoreImportContextService {

    private static final String SUPPORTED_FORMAT = "gpkg";

    private DataStoreSupplier dataStoreSupplier;

    @Override
    public boolean accepts(String format) {
        return SUPPORTED_FORMAT.equals(format);
    }

    @Override
    public String getCommandDescription() {
        return "Importing GeoPackage database file.";
    }

    @Override
    public DataStoreSupplier getDataStore(ParameterSet options) {
        if (dataStoreSupplier == null) {
            dataStoreSupplier = new GpkgDataStoreSupplier(options);
        }
        return dataStoreSupplier;
    }

    private static class GpkgDataStoreSupplier implements DataStoreSupplier {

        private JDBCDataStore dataStore;
        private final ParameterSet options;
        private final File uploadedFile;

        GpkgDataStoreSupplier(ParameterSet options) {
            super();
            this.options = options;
            this.uploadedFile = options.getUploadedFile();
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
            if (uploadedFile == null) {
                throw new CommandSpecException("Request must specify one and only one "
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
                throw new CommandSpecException("Unable to create GeoPkgDataStore: " + ioe
                    .getMessage());
            }
            if (null == dataStore) {
                throw new CommandSpecException(
                    "Unable to create GeoPkgDataStore from uploaded file.");
            }
        }

        @Override
        public void cleanupResources() {
            if (uploadedFile != null) {
                uploadedFile.delete();
            }
        }
    }
}
