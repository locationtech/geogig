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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.geotools.geopkg.GeopkgDataStoreExportOp;
import org.locationtech.geogig.geotools.plumbing.DataStoreExportOp;
import org.locationtech.geogig.rest.Variants;
import org.locationtech.geogig.rest.geotools.ExportWebOp;
import org.restlet.data.Form;
import org.restlet.resource.Variant;

import com.google.common.base.Suppliers;

/**
 * Export Web Op for GeoPackage.
 */
public class GeoPkgExportWebOp extends ExportWebOp {

    public static final String INTERCHANGE_PARAM = "interchange";

    @Override
    public DataStoreWrapper getDataStoreWrapper(Form options) throws IOException {
        // get a datastore
        final GeoPkgDataStoreFactory factory = new GeoPkgDataStoreFactory();
        final HashMap<String, Serializable> params = new HashMap<>(3);
        // fill in DataStore parameters
        params.put(GeoPkgDataStoreFactory.DBTYPE.key, "geopkg");
        // generate a temp file for the output database
        final Path databasePath = Files.createTempFile(UUID.randomUUID().toString(), ".gpkg");

        final File databaseFile = databasePath.toFile();

        params.put(GeoPkgDataStoreFactory.DATABASE.key, databaseFile.getAbsolutePath());
        params.put(GeoPkgDataStoreFactory.USER.key, options.getFirstValue("user", "user"));
        JDBCDataStore dataStore;
        try {
            dataStore = factory.createDataStore(params);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create GeoPkgDataStore", ioe);
        }
        if (null == dataStore) {
            throw new RuntimeException("Unable to create GeoPkgDataStore");
        }

        DataStoreWrapper wrapper = new DataStoreWrapper(Suppliers.ofInstance(dataStore)) {

            @Override
            public DataStoreExportOp createCommand(final Context context, final Form options) {

                String interchange = options.getFirstValue(INTERCHANGE_PARAM, true, "false");
                boolean enableInterchangeFormat = Boolean.parseBoolean(interchange);
                return context.command(GeopkgDataStoreExportOp.class)
                        .setInterchangeFormat(enableInterchangeFormat)
                        .setDatabaseFile(databaseFile);
            }

        };
        wrapper.setBinary(databaseFile);

        // // handle GeoPackage interchange format
        // final String interchangeArg = options.getFirstValue(INTERCHANGE_PARAM);
        // if (Boolean.parseBoolean(interchangeArg)) {
        // // need the geogig context
        // final Request request = getRequest();
        // final Context context = super.getContext(request);
        // GeopkgAuditExport auditExport = context.command(GeopkgAuditExport.class);
        // // get the DB file
        // auditExport.setDatabase(databaseFile).setSourceTreeish(srcPath)
        // .setTargetTableName(targetTable).call();
        // }
        return wrapper;
    }

    @Override
    public String getCommandDescription(Form options) {
        return "Description coming, check back soon!";
    }

    @Override
    protected void addSupportedVariants(List<Variant> variants) {
        variants.add(Variants.GEOPKG);
    }
}
