/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.postgresql.config.ConnectionConfig;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.EnvironmentBuilder;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.v9.PGConfigDatabase;
import org.locationtech.geogig.storage.postgresql.v9.PGConflictsDatabase;
import org.locationtech.geogig.storage.postgresql.v9.PGIndexDatabase;
import org.locationtech.geogig.storage.postgresql.v9.PGObjectDatabase;
import org.locationtech.geogig.storage.postgresql.v9.PGRefDatabase;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import lombok.NonNull;

public class PGRepositoryResolver implements RepositoryResolver {
    /**
     * Format name used for configuration.
     */
    public static final String FORMAT_NAME = "postgres";

    /**
     * Implementation version.
     */
    public static final String VERSION = "1";

    public @Override boolean canHandle(URI repoURI) {
        String scheme = repoURI.getScheme();
        return canHandleURIScheme(scheme);
    }

    public @Override boolean canHandleURIScheme(String scheme) {
        return "postgresql".equals(scheme);
    }

    public @Override boolean repoExists(URI repoURI) throws IllegalArgumentException {
        Environment config = parseConfig(repoURI);
        boolean exists = PGStorage.repoExists(config);
        return exists;
    }

    public @Override URI buildRepoURI(URI rootRepoURI, String repoName) {
        Properties properties = EnvironmentBuilder.getRootURIProperties(rootRepoURI);

        return EnvironmentBuilder.buildRepoURI(properties, repoName);
    }

    public @Override URI getRootURI(@NonNull URI repoURI) {
        Preconditions.checkArgument(canHandle(repoURI));
        Properties properties = EnvironmentBuilder.getRootURIProperties(repoURI);
        URI rootURI = new EnvironmentBuilder(properties).build().toURI();
        return rootURI;
    }

    public @Override List<String> listRepoNamesUnderRootURI(URI rootRepoURI) {
        Properties properties = EnvironmentBuilder.getRootURIProperties(rootRepoURI);
        EnvironmentBuilder builder = new EnvironmentBuilder(properties);
        return PGStorage.listRepos(builder.build());
    }

    public @Override void initialize(URI repoURI, Context repoContext)
            throws IllegalArgumentException {
        Environment config = parseConfig(repoURI);
        PGStorage.createNewRepo(config);
    }

    private PGConfigDatabase resolvedConfigDb;

    public @Override ConfigDatabase resolveConfigDatabase(URI repoURI,
            /* unused */Context repoContext, boolean rootUri) {
        if (resolvedConfigDb != null) {
            return resolvedConfigDb;
        }
        final Environment config;
        if (rootUri) {
            Properties properties = EnvironmentBuilder.getRootURIProperties(repoURI);
            EnvironmentBuilder builder = new EnvironmentBuilder(properties);
            config = builder.build();
        } else {
            config = parseConfig(repoURI);
        }
        PGConfigDatabase configDb = new PGConfigDatabase(config);
        if (config.getRepositoryName() != null && PGStorage.repoExists(config)) {
            Optional<String> configEntry = configDb.get(FORMAT_NAME + ".version");
            if (!configEntry.isPresent()) {
                configDb.put(FORMAT_NAME + ".version", VERSION);
            }
            configEntry = configDb.get("storage.refs");
            if (!configEntry.isPresent()) {
                configDb.put("storage.refs", FORMAT_NAME);
            }
            configEntry = configDb.get("storage.objects");
            if (!configEntry.isPresent()) {
                configDb.put("storage.objects", FORMAT_NAME);
            }
            configEntry = configDb.get("storage.index");
            if (!configEntry.isPresent()) {
                configDb.put("storage.index", FORMAT_NAME);
            }
            configEntry = configDb.get("storage.graph");
            if (!configEntry.isPresent()) {
                configDb.put("storage.graph", FORMAT_NAME);
            }
        }
        this.resolvedConfigDb = configDb;
        return configDb;
    }

    private Environment parseConfig(URI repoURI) {
        Environment config;
        try {
            config = new EnvironmentBuilder(repoURI).build();
        } catch (RuntimeException e) {
            Throwables.throwIfInstanceOf(e, IllegalArgumentException.class);
            throw new IllegalArgumentException("Error parsing URI", e);
        }
        Preconditions.checkArgument(config.getRepositoryName() != null,
                "No repository id provided in repo URI: '" + maskPassword(repoURI) + "'");
        return config;
    }

    public @Override Repository open(@NonNull URI repositoryURI)
            throws RepositoryConnectionException {
        return open(repositoryURI, Hints.readWrite());
    }

    public @Override Repository open(@NonNull URI repositoryLocation, @NonNull Hints hints)
            throws RepositoryConnectionException {
        Preconditions.checkArgument(canHandle(repositoryLocation), "Not a PostgreSQL URI: %s",
                maskPassword(repositoryLocation));

        hints = hints.uri(repositoryLocation);
        Context context = GlobalContextBuilder.builder().build(hints);
        Repository repository = new GeoGIG(context).getRepository();
        // Ensure the repository exists. If it's null, we might have a non-existing repo URI
        // location
        if (repository == null) {
            throw new RepositoryConnectionException(
                    "Could not connect to repository. Check that the URI is valid.");
        }
        repository.open();
        return repository;
    }

    public @Override String getName(URI repoURI) {
        Environment env = parseConfig(repoURI);
        String repositoryId = env.getRepositoryName();
        return repositoryId;
    }

    public @Override boolean delete(URI repositoryLocation) throws Exception {
        Environment env = parseConfig(repositoryLocation);
        return PGStorage.deleteRepository(env);
    }

    private static String maskPassword(@NonNull URI repoURI) {
        try {
            ConnectionConfig cc = EnvironmentBuilder.parse(repoURI);
            return cc.toURIMaskPassword().toString();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    public @Override ObjectDatabase resolveObjectDatabase(@NonNull URI repoURI, Hints hints) {
        ConfigDatabase configDatabase = resolveConfigDatabase(repoURI, null);
        try {
            return new PGObjectDatabase(configDatabase, hints);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public @Override IndexDatabase resolveIndexDatabase(@NonNull URI repoURI, Hints hints) {
        ConfigDatabase configDatabase = resolveConfigDatabase(repoURI, null);
        try {
            return new PGIndexDatabase(configDatabase, hints);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public @Override RefDatabase resolveRefDatabase(@NonNull URI repoURI, Hints hints) {
        try {
            return new PGRefDatabase(hints);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public @Override ConflictsDatabase resolveConflictsDatabase(@NonNull URI repoURI, Hints hints) {
        try {
            return new PGConflictsDatabase(hints);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
