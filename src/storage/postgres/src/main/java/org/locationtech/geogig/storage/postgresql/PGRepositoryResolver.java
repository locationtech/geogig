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
import java.util.List;
import java.util.Properties;

import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

public class PGRepositoryResolver extends RepositoryResolver {

    @Override
    public boolean canHandle(URI repoURI) {
        String scheme = repoURI.getScheme();
        return "postgresql".equals(scheme);
    }

    @Override
    public boolean repoExists(URI repoURI) throws IllegalArgumentException {
        Environment config = parseConfig(repoURI);
        boolean exists = PGStorage.repoExists(config);
        return exists;
    }

    @Override
    public URI buildRepoURI(URI rootRepoURI, String repoName) {
        Properties properties = EnvironmentBuilder.getRootURIProperties(rootRepoURI);

        return EnvironmentBuilder.buildRepoURI(properties, repoName);
    }

    @Override
    public List<String> listRepoNamesUnderRootURI(URI rootRepoURI) {
        Properties properties = EnvironmentBuilder.getRootURIProperties(rootRepoURI);
        EnvironmentBuilder builder = new EnvironmentBuilder(properties);
        return PGStorage.listRepos(builder.build());
    }

    @Override
    public void initialize(URI repoURI, Context repoContext) throws IllegalArgumentException {
        Environment config = parseConfig(repoURI);
        PGStorage.createNewRepo(config);
    }

    @Override
    public ConfigDatabase getConfigDatabase(URI repoURI, Context repoContext, boolean rootUri) {
        final Environment config;
        if (rootUri) {
            Properties properties = EnvironmentBuilder.getRootURIProperties(repoURI);
            EnvironmentBuilder builder = new EnvironmentBuilder(properties);
            config = builder.build();
        } else {
            config = parseConfig(repoURI);
        }
        PGConfigDatabase configDb = new PGConfigDatabase(config);
        if (config.getRepositoryName() != null) {
            Optional<String> refsFormat = configDb.get("storage.refs");
            if (!refsFormat.isPresent()) {
                configDb.put(PGStorageProvider.FORMAT_NAME + ".version", PGStorageProvider.VERSION);
                configDb.put("storage.refs", PGStorageProvider.FORMAT_NAME);
                configDb.put("storage.objects", PGStorageProvider.FORMAT_NAME);
                configDb.put("storage.graph", PGStorageProvider.FORMAT_NAME);
            }
        }
        return configDb;
    }

    private Environment parseConfig(URI repoURI) {
        Environment config;
        try {
            config = new EnvironmentBuilder(repoURI).build();
        } catch (RuntimeException e) {
            Throwables.propagateIfInstanceOf(e, IllegalArgumentException.class);
            throw new IllegalArgumentException("Error parsing URI " + repoURI, e);
        }
        Preconditions.checkArgument(config.getRepositoryName() != null,
                "No repository id provided in repo URI: '" + repoURI + "'");
        return config;
    }

    @Override
    public Repository open(URI repositoryLocation) throws RepositoryConnectionException {
        Preconditions.checkArgument(canHandle(repositoryLocation), "Not a PostgreSQL URI: %s",
                repositoryLocation);
        Hints hints = new Hints();
        hints.set(Hints.REPOSITORY_URL, repositoryLocation.toString());
        Context context = GlobalContextBuilder.builder().build(hints);
        Repository repository = new GeoGIG(context).getRepository();
        repository.open();
        return repository;
    }

    @Override
    public String getName(URI repoURI) {
        Environment env = parseConfig(repoURI);
        String repositoryId = env.getRepositoryName();
        return repositoryId;
    }

    @Override
    public boolean delete(URI repositoryLocation) throws Exception {
        Environment env = parseConfig(repositoryLocation);
        return PGStorage.deleteRepository(env);
    }

}
