/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.spring.controller;

import static org.locationtech.geogig.rest.repository.RepositoryProvider.BASE_REPOSITORY_ROUTE;
import static org.locationtech.geogig.rest.repository.RepositoryProvider.GEOGIG_ROUTE_PREFIX;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.data.postgis.PostgisNGDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.locationtech.geogig.geotools.plumbing.ImportOp;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.TransactionResolve;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.rest.AsyncCommandRepresentation;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.Representations;
import org.locationtech.geogig.rest.repository.ParameterSetFactory;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;

/**
 * Controller for postgis commands.
 */
@RestController
@RequestMapping(path = GEOGIG_ROUTE_PREFIX + "/" + BASE_REPOSITORY_ROUTE
        + "/{repoName}/postgis", produces = {
        APPLICATION_XML_VALUE, APPLICATION_JSON_VALUE })
public class PostgisController extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgisController.class);

    @VisibleForTesting
    static DataStoreFactorySpi dataStoreFactory = new PostgisNGDataStoreFactory();

    @GetMapping(value = "/import")
    public void pgImport(@PathVariable String repoName,
            @RequestParam MultiValueMap<String, String> params, HttpServletRequest request,
            HttpServletResponse response, RequestEntity<String> entity) {
        ParameterSet options = ParameterSet.concat(getParamsFromEntity(entity),
                ParameterSetFactory.buildParameterSet(params));
        DataStore dataStore = getDataStore(options);

        final String table = options.getFirstValue("table");
        final boolean all = Boolean.valueOf(options.getFirstValue("all", "false"));
        final boolean add = Boolean.valueOf(options.getFirstValue("add", "false"));
        final boolean forceFeatureType = Boolean
                .valueOf(options.getFirstValue("forceFeatureType", "false"));
        final boolean alter = Boolean.valueOf(options.getFirstValue("alter", "false"));
        final String dest = options.getFirstValue("dest");
        final String fidAttrib = options.getFirstValue("fidAttrib");
        final String txId = options.getFirstValue("transactionId");

        Context context = getContext(request, repoName, txId);

        ImportOp command = context.command(ImportOp.class);
        command.setDataStore(dataStore).setTable(table).setAll(all).setOverwrite(!add)
                .setAdaptToDefaultFeatureType(!forceFeatureType).setAlter(alter)
                .setDestinationPath(dest).setFidAttribute(fidAttrib);

        AsyncContext.AsyncCommand<RevTree> asyncCommand;

        URI repo = context.repository().getLocation();
        asyncCommand = AsyncContext.get().run(command, getCommandDescription(table, all, repo));

        AsyncCommandRepresentation<?> rep = Representations.newRepresentation(asyncCommand, false);
        encode(rep, request, response);
    }

    private DataStore getDataStore(ParameterSet options) {
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
            throw new CommandSpecException(
                    "Unable to connect using the specified database parameters.",
                    HttpStatus.BAD_REQUEST, e);
        }
        if (dataStore == null) {
            throw new CommandSpecException(
                    "Unable to connect using the specified database parameters.",
                    HttpStatus.BAD_REQUEST);
        }
        if (dataStore instanceof JDBCDataStore) {
            Connection con = null;
            try {
                con = ((JDBCDataStore) dataStore).getDataSource().getConnection();
            } catch (SQLException e) {
                throw new CommandSpecException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
            }
            ((JDBCDataStore) dataStore).closeSafe(con);
        }

        return dataStore;
    }

    private String getCommandDescription(String table, boolean all, URI repo) {
        return String.format("postgis import table %s into repository: %s", table, repo);
    }

    private Context getContext(HttpServletRequest request, String repoName, String txId) {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        RepositoryProvider provider = null;
        Context context = null;
        if (repoProvider.isPresent()) {
            provider = repoProvider.get();
            Optional<Repository> repo = provider.getGeogig(repoName);
            if (repo.isPresent() && repo.get().isOpen()) {
                context = repo.get().context();
            } else {
                throw new CommandSpecException("Repository not found.", HttpStatus.NOT_FOUND);
            }
        } else {
            throw new CommandSpecException("RepositoryProvider not specified in request",
                    HttpStatus.BAD_REQUEST);
        }
        if (txId != null) {
            Optional<GeogigTransaction> transaction = context.command(TransactionResolve.class)
                    .setId(UUID.fromString(txId)).call();
            if (transaction.isPresent()) {
                context = transaction.get();
            } else {
                throw new CommandSpecException(
                        "A transaction with the provided ID could not be found.",
                        HttpStatus.BAD_REQUEST);
            }
        } else {
            throw new CommandSpecException(
                    "No transaction was specified, this command requires a transaction to preserve the stability of the repository.");
        }
        return context;
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
