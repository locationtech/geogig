/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import static java.lang.String.format;

import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.ConfigStore;
import org.locationtech.geogig.storage.internal.AbstractConfigDatabase;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;

import com.google.common.collect.Lists;

import lombok.NonNull;

/**
 * PostgreSQL based config database.
 * <p>
 * Operates over the {@code geogig_config} table, which has the following DDL:
 * 
 * <pre>
 * <code>
 *  CREATE TABLE IF NOT EXISTS geogig_config (repository INTEGER, section TEXT, key TEXT, value TEXT,
 *  PRIMARY KEY (repository, section, key) 
 *  FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)
 * </code>
 * </pre>
 * <p>
 * The {@code repository} column holds the value given by {@link Environment#getRepositoryId()},
 * which is the repository's primary key in the {@code geogig_repository} table. The mapping from
 * repository id and name is given by the config property {@code repo.name}, and matches the {@code 
 * <repositoryName>} in a postgresql repository URI (i.e. {@code postgresql:// <host>[:port]/
 * <database>/<repositoryName>}).
 * 
 * @implNote this {@link ConfigDatabase} implementation enforces uniqueness of the {@code repo.name}
 *           config property, and it is assigned when a repository is first created.
 * 
 * @implNote {@link #putGlobal(String, Object) global} values are stored under the {@code -1} key
 *           for the {@code repository} column.
 */
public class PGConfigDatabase extends AbstractConfigDatabase {
    private final Environment config;

    private final PGConfigStore globalConfigStore;

    private final PGConfigStore localConfigStore;

    public PGConfigDatabase(Hints hints) throws URISyntaxException {
        this(Environment.get(hints));
    }

    public PGConfigDatabase(Environment environment) {
        super(false);
        this.config = environment;
        this.globalConfigStore = new PGConfigStore(environment, this::globalRepoPK);
        this.localConfigStore = new PGConfigStore(environment, this::localRepoPK);
    }

    public @Override void close() {
        localConfigStore.close();
        globalConfigStore.close();
    }

    protected @Override ConfigStore local() {
        return localConfigStore;
    }

    protected @Override ConfigStore global() {
        return globalConfigStore;
    }

    private int localRepoPK() {
        if (!config.isRepositorySet()) {
            String repositoryName = config.getRepositoryName();
            if (null == repositoryName) {
                throw new ConfigException(ConfigException.StatusCode.INVALID_LOCATION);
            }
            Optional<Integer> pk;
            try {
                pk = resolveRepositoryPK(repositoryName);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            if (!pk.isPresent()) {
                throw new ConfigException(ConfigException.StatusCode.INVALID_LOCATION);
            }
            config.setRepositoryId(pk.get());
        }
        return config.getRepositoryId();
    }

    private int globalRepoPK() {
        return Environment.GLOBAL_KEY;
    }

    public Optional<Integer> resolveRepositoryPK(@NonNull String repositoryName)
            throws SQLException {
        try (Connection cx = PGStorage.newConnection(localConfigStore.connect(config))) {
            return resolveRepositoryPK(repositoryName, cx);
        }
    }

    private Optional<Integer> resolveRepositoryPK(String repositoryName, Connection cx)
            throws SQLException {

        final String configTable = config.getTables().repositoryNamesView();
        final String sql = format("SELECT repository FROM %s WHERE name = ?", configTable);

        Integer repoPK = null;
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, repositoryName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    repoPK = rs.getInt(1);
                    List<Integer> all = Lists.newArrayList(repoPK);
                    while (rs.next()) {
                        all.add(rs.getInt(1));
                    }
                    if (all.size() > 1) {
                        throw new IllegalStateException(format(
                                "There're more than one repository named '%s'. "
                                        + "Check the repo.name config property for the following repository ids: %s",
                                repositoryName, all.toString()));
                    }
                }
            }
        }
        return Optional.ofNullable(repoPK);
    }
}
