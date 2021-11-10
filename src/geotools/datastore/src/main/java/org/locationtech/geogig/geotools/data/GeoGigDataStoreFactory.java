/* Copyright (c) 2013-2016 Boundless and others.
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.data.Parameter;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryFinder;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeoGigDataStoreFactory implements DataStoreFactorySpi {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeoGigDataStoreFactory.class);

    /** GEO_GIG */
    public static final String DISPLAY_NAME = "GeoGIG";

    public static interface RepositoryLookup {
        public URI resolve(String repository);
    }

    public static class DefaultRepositoryLookup implements RepositoryLookup {
        public @Override URI resolve(String repository) {
            try {
                return new URI(repository);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static final Param REPOSITORY = new Param("geogig_repository", String.class,
            "Repository URI", true, "/path/to/repository") {

        public @Override String lookUp(Map<String, ?> map) throws IOException {
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

    public static final Param AUTO_INDEXING = new Param("autoIndexing", Boolean.class,
            "Let GeoServer automatically create spatial indexes and add time/elevation attributes if the layer has them",
            false/* required */, false/* default */, //
            Map.of(Parameter.LEVEL, "advanced"));

    public @Override String getDisplayName() {
        return DISPLAY_NAME;
    }

    public @Override String getDescription() {
        return "GeoGIG Versioning DataStore";
    }

    public @Override Param[] getParametersInfo() {
        return new Param[] { REPOSITORY, BRANCH, HEAD, DEFAULT_NAMESPACE, AUTO_INDEXING };
    }

    private URI resolveURI(String repoParam) {
        URI repoUri = null;
        try {
            repoUri = new URI(repoParam);
        } catch (URISyntaxException e) {
            // See if it's a valid file URI
            try {
                repoUri = new URI("file:/" + repoParam.replace("\\", "/"));
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException(e);
            }
        }
        return repoUri;
    }

    public @Override boolean canProcess(Map<String, ?> params) {
        try {
            final String repoParam = (String) REPOSITORY.lookUp(params);

            final URI repository = resolveURI(repoParam);

            try {
                RepositoryFinder.INSTANCE.lookup(repository);
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
    public @Override boolean isAvailable() {
        return true;
    }

    public @Override Map<Key, ?> getImplementationHints() {
        return Collections.emptyMap();
    }

    public @Override GeoGigDataStore createDataStore(Map<String, ?> params) throws IOException {

        final String repositoryLocation = (String) REPOSITORY.lookUp(params);

        @Nullable
        final String defaultNamespace = (String) DEFAULT_NAMESPACE.lookUp(params);

        @Nullable
        final String branch = (String) BRANCH.lookUp(params);

        @Nullable
        final String head = (String) HEAD.lookUp(params);

        @Nullable
        final String effectiveHead = (head == null) ? branch : head;

        final URI repositoryUri = resolveURI(repositoryLocation);

        Repository repo;
        try {
            repo = Geogig.open(repositoryUri).getRepository();
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

    /**
     * @see org.geotools.data.DataStoreFactorySpi#createNewDataStore(java.util.Map)
     */
    public @Override GeoGigDataStore createNewDataStore(Map<String, ?> params) throws IOException {
        String defaultNamespace = (String) DEFAULT_NAMESPACE.lookUp(params);

        URI repositoryRoot = resolveURI((String) REPOSITORY.lookUp(params));
        RepositoryResolver initializer = RepositoryFinder.INSTANCE.lookup(repositoryRoot);
        if (initializer.repoExists(repositoryRoot)) {
            throw new IOException("Repository already exists " + repositoryRoot);
        }

        Geogig geogig;
        try {
            geogig = Geogig.create(repositoryRoot);
        } catch (RepositoryConnectionException | RuntimeException e) {
            throw new IOException(e);
        }

        GeoGigDataStore store = new GeoGigDataStore(geogig.getRepository());
        if (defaultNamespace != null) {
            store.setNamespaceURI(defaultNamespace);
        }
        return store;
    }

}
