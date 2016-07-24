/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data;

import java.awt.RenderingHints.Key;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.util.KVP;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.GeoGIG;
import org.locationtech.geogig.repository.GlobalContextBuilder;
//import org.locationtech.geogig.cli.CLIContextBuilder;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

public class GeoGigDataStoreFactory implements DataStoreFactorySpi {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeoGigDataStoreFactory.class);

    /** GEO_GIG */
    public static final String DISPLAY_NAME = "GeoGIG";

    public static interface RepositoryLookup {
        public URI resolve(String repository);
    }

    public static class DefaultRepositoryLookup implements RepositoryLookup {
        @Override
        public URI resolve(String repository) {
            try {
                return new URI(repository);
            } catch (URISyntaxException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    public static final Param RESOLVER_CLASS_NAME = new Param("resolver", String.class,
            "Fully qualified class name for the RepositoryLookup that resolves the REPOSITORY parameter to an actual path",
            false, DefaultRepositoryLookup.class.getName(), new KVP(Param.LEVEL, "advanced"));

    public static final Param REPOSITORY = new Param("geogig_repository", String.class,
            "Repository URI", true, "/path/to/repository") {

        @Override
        public String lookUp(Map<String, ?> map) throws IOException {
            if (null == map.get(key)) {
                throw new IOException(
                        String.format("Parameter %s is required: %s", key, description));
            }
            String value = String.valueOf(map.get(key));
            return value;
        }
    };

    public static final Param BRANCH = new Param("branch", String.class,
            "Optional branch name the DataStore operates against, defaults to the currently checked out branch",
            false);

    public static final Param HEAD = new Param("head", String.class,
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
        return new Param[] { RESOLVER_CLASS_NAME, REPOSITORY, BRANCH, HEAD, DEFAULT_NAMESPACE,
                CREATE };
    }

    @Override
    public boolean canProcess(Map<String, Serializable> params) {
        try {
            final String repoParam = (String) REPOSITORY.lookUp(params);
            final String resolverClass = (String) RESOLVER_CLASS_NAME.lookUp(params);
            if (resolverClass != null) {
                return true;
            }

            final URI repository = URI.create(repoParam);

            if (Boolean.TRUE.equals(CREATE.lookUp(params))) {
                return true;
            }

            try {
                RepositoryResolver.lookup(repository);
            } catch (IllegalArgumentException e) {
                return false;
            }
            return true;
        } catch (IOException ignoreMe) {
            //
        }
        if (params.containsKey(REPOSITORY.key)) {
            LOGGER.warn("Unable to process parameter %s: '%s'", REPOSITORY.key,
                    params.get(REPOSITORY.key));
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

        @Nullable
        final String lookUpClass = (String) RESOLVER_CLASS_NAME.lookUp(params);

        RepositoryLookup resolver = resolver(lookUpClass);

        final String repositoryLocation = (String) REPOSITORY.lookUp(params);

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

        final URI repositoryDirectory = resolver.resolve(repositoryLocation);

        final RepositoryResolver initializer;
        try {
            initializer = RepositoryResolver.lookup(repositoryDirectory);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        if (create != null && create.booleanValue()) {
            if (!initializer.repoExists(repositoryDirectory)) {
                return createNewDataStore(params);
            }
        }

        Repository repo;
        try {
            repo = initializer.open(repositoryDirectory);
        } catch (RepositoryConnectionException | RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        }

        GeoGigDataStore store = new GeoGigDataStore(repo);
        if (defaultNamespace != null) {
            store.setNamespaceURI(defaultNamespace);
        }
        if (effectiveHead != null) {
            store.setHead(effectiveHead);
        }
        return store;
    }

    private RepositoryLookup resolver(@Nullable String lookUpClass) throws IOException {
        if (null == lookUpClass) {
            return new DefaultRepositoryLookup();
        }
        try {
            Class<?> clazz = Class.forName(lookUpClass);
            Object instance = clazz.newInstance();
            Preconditions.checkArgument(instance instanceof RepositoryLookup);
            return RepositoryLookup.class.cast(instance);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | ClassCastException e) {
            throw new IOException(String.format(
                    "Parameter '%s' ('%s') can't be instantiated as a %s", RESOLVER_CLASS_NAME.key,
                    lookUpClass, RepositoryLookup.class.getName()));
        }
    }

    /**
     * @see org.geotools.data.DataStoreFactorySpi#createNewDataStore(java.util.Map)
     */
    @Override
    public GeoGigDataStore createNewDataStore(Map<String, Serializable> params) throws IOException {
        String defaultNamespace = (String) DEFAULT_NAMESPACE.lookUp(params);

        URI repositoryRoot = URI.create((String) REPOSITORY.lookUp(params));
        RepositoryResolver initializer = RepositoryResolver.lookup(repositoryRoot);
        if (initializer.repoExists(repositoryRoot)) {
            throw new IOException("Repository already exists " + repositoryRoot);
        }

        Hints hints = new Hints().uri(repositoryRoot);
        Context context = GlobalContextBuilder.builder().build(hints);
        GeoGIG geogig = new GeoGIG(context);

        Repository repository;
        try {
            repository = geogig.getOrCreateRepository();
            Preconditions.checkState(repository != null);
        } catch (RuntimeException e) {
            throw new IOException(e);
        }

        GeoGigDataStore store = new GeoGigDataStore(repository);
        if (defaultNamespace != null) {
            store.setNamespaceURI(defaultNamespace);
        }
        return store;
    }

}
