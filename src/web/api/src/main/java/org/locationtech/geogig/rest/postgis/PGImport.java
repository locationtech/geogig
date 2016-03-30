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
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.geotools.plumbing.ImportOp;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.TransactionalResource;
import org.locationtech.geogig.rest.Variants;
import org.locationtech.geogig.rest.geotools.ImportRepresentation;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.Variant;

import com.beust.jcommander.internal.Maps;

public class PGImport extends TransactionalResource {
    @Override
    public void init(org.restlet.Context context, Request request, Response response) {
        super.init(context, request, response);
        getVariants().add(Variants.XML);
        getVariants().add(Variants.JSON);
    }

    @Override
    public Representation getRepresentation(final Variant variant) {
        final Request request = getRequest();
        final Context context = super.getContext(request);

        Form options = getRequest().getResourceRef().getQueryAsForm();

        DataStore dataStore = getDataStore(options);

        final String table = options.getFirstValue("table");
        final boolean all = Boolean.valueOf(options.getFirstValue("all", "false"));
        final boolean add = Boolean.valueOf(options.getFirstValue("add", "false"));
        final boolean forceFeatureType = Boolean
            .valueOf(options.getFirstValue("forceFeatureType", "false"));
        final boolean alter = Boolean.valueOf(options.getFirstValue("alter", "false"));
        final String dest = options.getFirstValue("dest");
        final String fidAttrib = options.getFirstValue("fidAttrib");
        ImportOp command = context.command(ImportOp.class);
        command.setDataStore(dataStore).setTable(table).setAll(all).setOverwrite(!add)
            .setAdaptToDefaultFeatureType(!forceFeatureType).setAlter(alter)
            .setDestinationPath(dest).setFidAttribute(fidAttrib);

        AsyncContext.AsyncCommand<RevTree> asyncCommand;

        URI repo = context.repository().getLocation();
        asyncCommand = AsyncContext.get().run(command, getCommandDescription(table, all, repo));

        final String rootPath = request.getRootRef().toString();
        MediaType mediaType = variant.getMediaType();
        return new ImportRepresentation(mediaType, asyncCommand, rootPath);
    }

    private DataStore getDataStore(Form options) {
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

    private String getCommandDescription(String table, boolean all, URI repo) {
        return String.format("postgis import table %s into repository: %s", table,
                repo);
    }
}
