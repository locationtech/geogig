/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.geotools.data;

import java.awt.RenderingHints.Key;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;

import org.geotools.data.DataStoreFactorySpi;
import org.locationtech.geogig.api.ContextBuilder;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.cli.CLIContextBuilder;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Preconditions;

public class GeoGigDataStoreFactory implements DataStoreFactorySpi {

    /** GEO_GIG */
    public static final String DISPLAY_NAME = "GeoGIG";

    static {
        if (GlobalContextBuilder.builder == null
                || GlobalContextBuilder.builder.getClass().equals(ContextBuilder.class)) {
            GlobalContextBuilder.builder = new CLIContextBuilder();
        }
    }

    public static final Param REPOSITORY = new Param("geogig_repository", File.class,
            "Root directory for the geogig repository", true, "/path/to/repository");

    public static final Param BRANCH = new Param(
            "branch",
            String.class,
            "Optional branch name the DataStore operates against, defaults to the currently checked out branch",
            false);

    public static final Param HEAD = new Param(
            "head",
            String.class,
            "Optional refspec (branch name, commit id, etc.) the DataStore operates against, defaults to the currently checked out branch",
            false);

    public static final Param DEFAULT_NAMESPACE = new Param("namespace", String.class,
            "Optional namespace for feature types that do not declare a Namespace themselves",
            false);

    public static final Param CREATE = new Param("create", Boolean.class,
            "Optional flag to enable creation of a new repository if it does not exist", false);

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public String getDescription() {
        return "GeoGIG Versioning DataStore";
    }

    @Override
    public Param[] getParametersInfo() {
        return new Param[] { REPOSITORY, BRANCH, HEAD, DEFAULT_NAMESPACE, CREATE };
    }

    @Override
    public boolean canProcess(Map<String, Serializable> params) {
        try {
            Object repository = REPOSITORY.lookUp(params);

            // check that repository points to a file, and either that fiel is a directory, or the
            // the create option is set
            return repository instanceof File && ((File) repository).isDirectory()
                    || Boolean.TRUE.equals(CREATE.lookUp(params));
        } catch (IOException e) {
            //
        }
        return false;
    }

    /**
     * @see org.geotools.data.DataAccessFactory#isAvailable()
     */
    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Map<Key, ?> getImplementationHints() {
        return Collections.emptyMap();
    }

    @Override
    public GeoGigDataStore createDataStore(Map<String, Serializable> params) throws IOException {

        final File repositoryRoot = (File) REPOSITORY.lookUp(params);

        @Nullable
        final String defaultNamespace = (String) DEFAULT_NAMESPACE.lookUp(params);

        @Nullable
        final String branch = (String) BRANCH.lookUp(params);

        @Nullable 
        final String head = (String) HEAD.lookUp(params);

        @Nullable
        final String effectiveHead = (head == null) ? branch : head;

        @Nullable
        final Boolean create = (Boolean) CREATE.lookUp(params);

        if (create != null && create.booleanValue()) {
            if (!repositoryRoot.exists()) {
                return createNewDataStore(params);
            }
        }

        GeoGIG geogig;
        try {
            geogig = new GeoGIG(repositoryRoot);
        } catch (RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        }
        Repository repository = geogig.getRepository();
        if (null == repository) {
            if (create != null && create.booleanValue()) {
                return createNewDataStore(params);
            }

            throw new IOException(String.format("Directory is not a geogig repository: '%s'",
                    repositoryRoot.getAbsolutePath()));
        }

        GeoGigDataStore store = new GeoGigDataStore(geogig);
        if (defaultNamespace != null) {
            store.setNamespaceURI(defaultNamespace);
        }
        if (effectiveHead != null) {
            store.setHead(effectiveHead);
        }
        return store;
    }

    /**
     * @see org.geotools.data.DataStoreFactorySpi#createNewDataStore(java.util.Map)
     */
    @Override
    public GeoGigDataStore createNewDataStore(Map<String, Serializable> params) throws IOException {
        String defaultNamespace = (String) DEFAULT_NAMESPACE.lookUp(params);

        File repositoryRoot = (File) REPOSITORY.lookUp(params);
        if (!repositoryRoot.isDirectory()) {
            if (repositoryRoot.exists()) {
                throw new IOException(repositoryRoot.getAbsolutePath() + " is not a directory");
            }
            repositoryRoot.mkdirs();
        }

        GeoGIG geogig = new GeoGIG(repositoryRoot);

        try {
            Repository repository = geogig.getOrCreateRepository();
            Preconditions.checkState(repository != null);
        } catch (RuntimeException e) {
            throw new IOException(e);
        }

        GeoGigDataStore store = new GeoGigDataStore(geogig);
        if (defaultNamespace != null) {
            store.setNamespaceURI(defaultNamespace);
        }
        return store;
    }

}
