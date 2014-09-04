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
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;

import org.geotools.data.DataStoreFactorySpi;
import org.geotools.util.KVP;
import org.locationtech.geogig.api.ContextBuilder;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.cli.CLIContextBuilder;
import org.locationtech.geogig.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class GeoGigDataStoreFactory implements DataStoreFactorySpi {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeoGigDataStoreFactory.class);

    /** GEO_GIG */
    public static final String DISPLAY_NAME = "GeoGIG";

    static {
        if (GlobalContextBuilder.builder == null
                || GlobalContextBuilder.builder.getClass().equals(ContextBuilder.class)) {
            GlobalContextBuilder.builder = new CLIContextBuilder();
        }
    }

    public static interface RepositoryLookup {
        public File resolve(String repository);
    }

    public static class DefaultRepositoryLookup implements RepositoryLookup {
        @Override
        public File resolve(String repository) {
            return new File(repository);
        }
    }

    public static final Param RESOLVER_CLASS_NAME = new Param(
            "resolver",
            String.class,
            "Fully qualified class name for the RepositoryLookup that resolves the REPOSITORY parameter to an actual path",
            false, DefaultRepositoryLookup.class.getName(), new KVP(Param.LEVEL, "advanced"));

    public static final Param REPOSITORY = new Param("geogig_repository", String.class,
            "Root directory for the geogig repository", true, "/path/to/repository") {

        @Override
        public String lookUp(Map<String, ?> map) throws IOException {
            if (null == map.get(key)) {
                throw new IOException(String.format("Parameter %s is required: %s", key,
                        description));
            }
            String value = String.valueOf(map.get(key));
            return value;
        }
    };

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

            File repository = new File(repoParam);

            // check that repository points to a file, and either that fiel is a directory, or the
            // the create option is set
            return repository instanceof File && ((File) repository).isDirectory()
                    || Boolean.TRUE.equals(CREATE.lookUp(params));
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

        final File repositoryDirectory = resolver.resolve(repositoryLocation);

        if (create != null && create.booleanValue()) {
            if (!repositoryDirectory.exists()) {
                return createNewDataStore(params);
            }
        }

        GeoGIG geogig;
        try {
            geogig = new GeoGIG(repositoryDirectory);
        } catch (RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        }
        Repository repository = geogig.getRepository();
        if (null == repository) {
            if (create != null && create.booleanValue()) {
                return createNewDataStore(params);
            }

            throw new IOException(String.format("Directory is not a geogig repository: '%s'",
                    repositoryDirectory.getAbsolutePath()));
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

        File repositoryRoot = new File((String) REPOSITORY.lookUp(params));
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
