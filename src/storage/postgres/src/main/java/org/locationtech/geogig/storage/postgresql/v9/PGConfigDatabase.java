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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.log;
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.rollbackAndRethrow;

import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.util.Converters;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.ConfigException.StatusCode;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.config.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

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
public class PGConfigDatabase implements ConfigDatabase {

    static final Logger LOG = LoggerFactory.getLogger(PGConfigDatabase.class);

    private final Environment config;

    private DataSource dataSource;

    private Version _serverVersion;

    @Inject
    public PGConfigDatabase(Hints hints) throws URISyntaxException {
        this(Environment.get(hints));
    }

    public PGConfigDatabase(Environment environment) {
        this.config = environment;
    }

    private Version serverVersion(Connection cx) throws SQLException {
        if (_serverVersion == null) {
            _serverVersion = PGStorage.getServerVersion(cx);
        }
        return _serverVersion;
    }

    @Override
    public Optional<String> get(String key) {
        try {
            return get(new Entry(key), String.class, local());
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        }
    }

    @Override
    public Optional<String> getGlobal(String key) {
        return get(new Entry(key), String.class, global());
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> c) {
        return get(new Entry(key), c, local());
    }

    @Override
    public <T> Optional<T> getGlobal(String key, Class<T> c) {
        return get(new Entry(key), c, global());
    }

    @Override
    public Map<String, String> getAll() {
        return all(local());
    }

    @Override
    public Map<String, String> getAllGlobal() {
        return all(global());
    }

    @Override
    public Map<String, String> getAllSection(String section) {
        return all(section, local());
    }

    @Override
    public Map<String, String> getAllSectionGlobal(String section) {
        return all(section, global());
    }

    @Override
    public List<String> getAllSubsections(String section) {
        return list(section, local());
    }

    @Override
    public List<String> getAllSubsectionsGlobal(String section) {
        return list(section, global());
    }

    /**
     * @implNote if {@code key} equals {@code repo.name} (i.e. the repository name is being
     *           changed), this method ensures no other repository in the same database is named the
     *           same as {@code value}, and throws an {@link IllegalArgumentException} if there is.
     */
    @Override
    public void put(String key, Object value) {
        put(new Entry(key), value, local());
    }

    @Override
    public void putGlobal(String key, Object value) {
        put(new Entry(key), value, global());
    }

    public @Override void putSection(final String section, final Map<String, String> kvp) {
        checkNotNull(section);
        checkNotNull(kvp);
        Map<Entry, String> entries = new HashMap<>();
        kvp.forEach((k, v) -> entries.put(new Entry(String.format("%s.%s", section, k)), v));
        put(entries, local());
    }

    @Override
    public void remove(String key) {
        remove(new Entry(key), local());
    }

    @Override
    public void removeGlobal(String key) {
        remove(new Entry(key), global());
    }

    @Override
    public void removeSection(String key) {
        removeSection(key, local());
    }

    private int local() {
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

    private int global() {
        return Environment.GLOBAL_KEY;
    }

    @Override
    public void removeSectionGlobal(String key) {
        removeSection(key, global());
    }

    <T> Optional<T> get(Entry entry, Class<T> clazz, final int repositoryPK) {
        String raw = get(entry, repositoryPK);
        if (raw != null) {
            return Optional.of(convert(raw, clazz));
        }
        return Optional.absent();
    }

    <T> T convert(String value, Class<T> clazz) {
        Object v = Converters.convert(value, clazz);
        checkArgument(v != null, "Can't convert %s to %s", value, clazz.getName());
        return clazz.cast(v);
    }

    void put(Entry entry, Object value, final int repositoryPK) {
        put(entry, (String) (value != null ? value.toString() : null), repositoryPK);
    }

    protected static class Entry {
        final String section;

        final String key;

        public Entry(String sectionAndKey) {
            checkArgument(sectionAndKey != null, "Config key may not be null.");

            int idx = sectionAndKey.lastIndexOf('.');
            if (idx == -1) {
                throw new ConfigException(StatusCode.SECTION_OR_KEY_INVALID);
            }
            section = sectionAndKey.substring(0, idx);
            key = sectionAndKey.substring(idx + 1);
        }

        //@formatter:off
        public @Override int hashCode() {return Objects.hash(section, key);}
        public @Override boolean equals(Object o) {return (o instanceof Entry) && ((Entry)o).section.equals(section) && ((Entry)o).key.equals(key);}
        //@formatter:on
    }

    @Nullable
    protected String get(final Entry entry, final int repositoryPK) {
        checkArgument(!Strings.isNullOrEmpty(entry.section), "Section name required");
        checkArgument(!Strings.isNullOrEmpty(entry.key), "Key required");

        final String sql = format(
                "SELECT value FROM %s WHERE repository = ? AND section = ? AND key = ?",
                config.getTables().config());

        final String s = entry.section;
        final String k = entry.key;

        try (Connection cx = PGStorage.newConnection(connect(config))) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, repositoryPK, s, k))) {
                ps.setInt(1, repositoryPK);
                ps.setString(2, s);
                ps.setString(3, k);

                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected Map<String, String> all(final int repositoryPK) {

        final String sql = format("SELECT section,key,value FROM %s WHERE repository = ?",
                config.getTables().config());

        try (Connection cx = PGStorage.newConnection(connect(config))) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, repositoryPK))) {
                ps.setInt(1, repositoryPK);
                Map<String, String> all = new LinkedHashMap<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String entry = String.format("%s.%s", rs.getString(1), rs.getString(2));
                        all.put(entry, rs.getString(3));
                    }
                }
                return all;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected Map<String, String> all(final String section, final int repositoryPK) {
        final String sql = format("SELECT key,value FROM %s WHERE repository = ? AND section = ?",
                config.getTables().config());

        try (Connection cx = PGStorage.newConnection(connect(config))) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, repositoryPK, section))) {
                ps.setInt(1, repositoryPK);
                ps.setString(2, section);
                Map<String, String> all = Maps.newLinkedHashMap();

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String key = rs.getString(1);
                        String value = rs.getString(2);
                        all.put(key, value);
                    }
                }
                return all;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected List<String> list(final String section, final int repositoryPK) {
        String sql = format(
                "SELECT DISTINCT section FROM %s WHERE repository = ? AND section LIKE ?",
                config.getTables().config());

        try (Connection cx = PGStorage.newConnection(connect(config))) {
            List<String> all = new ArrayList<>(1);

            final String sectionPrefix = section + (section.endsWith(".") ? "" : ".");

            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, repositoryPK, section))) {
                ps.setInt(1, repositoryPK);
                ps.setString(2, sectionPrefix + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String sect = rs.getString(1);
                        String subsection = sect.substring(sectionPrefix.length());
                        all.add(subsection);
                    }
                }
            }
            return all;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void put(final Entry entry, final String value, final int repositoryPK) {
        put(Collections.singletonMap(entry, value), repositoryPK);
    }

    private static Entry REPO_NAME = new Entry("repo.name");

    private String _INSERT, _UPSERT;

    private String insertSql() {
        if (_INSERT == null) {
            _INSERT = format("insert into %s (repository, section, key, value) values(?, ?, ?, ?)",
                    config.getTables().config());
        }
        return _INSERT;
    }

    private String upsertSql() {
        if (_UPSERT == null) {
            _UPSERT = format(
                    "insert into %s (repository, section, key, value) values(?, ?, ?, ?) ON CONFLICT (repository, section, key) DO UPDATE SET value = ?",
                    config.getTables().config());
        }
        return _UPSERT;
    }

    private void put(final Map<Entry, String> entries, final int repositoryPK) {

        try (Connection cx = PGStorage.newConnection(connect(config))) {
            final Version version = serverVersion(cx);
            final boolean useUpsert = version.greatherOrEqualTo(Version.V9_5_0);
            final String sql = useUpsert ? upsertSql() : insertSql();

            cx.setAutoCommit(false);
            try (PreparedStatement ps = cx.prepareStatement(sql)) {

                for (Map.Entry<Entry, String> mentry : entries.entrySet()) {
                    final Entry entry = mentry.getKey();
                    final String value = mentry.getValue();
                    checkRepoNameClash(repositoryPK, entry, value);

                    if (!useUpsert) {
                        doRemove(entry, cx, repositoryPK);
                    }

                    String s = entry.section;
                    String k = entry.key;

                    ps.setInt(1, repositoryPK);
                    ps.setString(2, s);
                    ps.setString(3, k);
                    ps.setString(4, value);
                    if (useUpsert) {
                        ps.setString(5, value);
                    }
                    ps.addBatch();
                }

                ps.executeBatch();
            }
            try {
                cx.commit();
            } catch (SQLException e) {
                rollbackAndRethrow(cx, e);
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkRepoNameClash(final int repositoryPK, final Entry entry, final String value)
            throws SQLException {
        if (REPO_NAME.equals(entry)) {
            Optional<Integer> pk = resolveRepositoryPK(value);
            if (pk.isPresent() && !pk.get().equals(repositoryPK)) {
                String msg = format(
                        "A repsitory named '%s' already exists with primary key value: %d", value,
                        pk.get());
                throw new IllegalArgumentException(msg);
            }
        }
    }

    private void remove(final Entry entry, final int repositoryPK) {
        try (Connection cx = PGStorage.newConnection(connect(config))) {
            doRemove(entry, cx, repositoryPK);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void doRemove(final Entry entry, Connection cx, final int repositoryPK)
            throws SQLException {

        final String sql = format("DELETE FROM %s WHERE repository = ? AND section = ? AND key = ?",
                config.getTables().config());

        final String s = entry.section;
        final String k = entry.key;

        try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, repositoryPK, s, k))) {
            ps.setInt(1, repositoryPK);
            ps.setString(2, s);
            ps.setString(3, k);

            ps.executeUpdate();
        }

    }

    protected void removeSection(final String section, final int repositoryId) {
        final String sql = format("DELETE FROM %s WHERE repository = ? AND section = ?",
                config.getTables().config());

        try (Connection cx = PGStorage.newConnection(connect(config))) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, repositoryId, section))) {
                ps.setInt(1, repositoryId);
                ps.setString(2, section);
                int updateCount = ps.executeUpdate();
                if (0 == updateCount) {
                    throw new ConfigException(StatusCode.MISSING_SECTION);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    synchronized DataSource connect(final Environment config) {
        if (this.dataSource == null) {
            this.dataSource = PGStorage.newDataSource(config);
            if (!PGStorage.tableExists(this.dataSource, config.getTables().config())) {
                PGStorage.createTables(config);
            }
            PGStorage.verifyDatabaseCompatibility(dataSource, config);
        }
        return dataSource;
    }

    @Override
    public synchronized void close() {
        if (dataSource != null) {
            PGStorage.closeDataSource(dataSource);
            dataSource = null;
        }
    }

    /**
     * Given a repository name (as per the {@code repo.name} config property, looks up the
     * repository primary key.
     * 
     * 
     * @param repositoryName the repository name, that's stored as the {@code repo.name} config
     *        property
     * @return the repository primary key as assigned to the {@code repository} serial field in the
     *         {@code geogig_repository} table and that's a foreign key in {@code geogig_config}
     * @throws SQLException if such is thrown by the JDBC driver
     * @throws {@link IllegalStateException} if there are more than one repository with its
     *         {@code repo.name} config property set to the {@code repositoryName} value
     */
    public Optional<Integer> resolveRepositoryPK(String repositoryName) throws SQLException {
        checkNotNull(repositoryName, "provided null repository name");
        try (Connection cx = PGStorage.newConnection(connect(config))) {
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
        return Optional.fromNullable(repoPK);
    }
}
