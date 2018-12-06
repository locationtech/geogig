/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import static java.lang.String.format;
import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.FORMAT_NAME;
import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.VERSION;
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.log;
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.rollbackAndRethrow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.IndexDuplicator;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.datastream.DataStreamValueSerializerV2;
import org.locationtech.geogig.storage.datastream.ValueSerializer;
import org.locationtech.geogig.storage.postgresql.config.ConnectionConfig;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGId;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.config.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

/**
 * PostgreSQL implementation for {@link IndexDatabase}.
 */
public class PGIndexDatabase extends PGObjectStore implements IndexDatabase {

    private static final Logger log = LoggerFactory.getLogger(PGIndexDatabase.class);

    private int repositoryId;

    private final ValueSerializer valueEncoder = DataStreamValueSerializerV2.INSTANCE;

    @Inject
    public PGIndexDatabase(final ConfigDatabase configdb, final Hints hints)
            throws URISyntaxException {
        this(configdb, Environment.get(hints), readOnly(hints));
    }

    protected @Override String getCacheIdentifier(ConnectionConfig connectionConfig) {
        final String cacheIdentifier = connectionConfig.toURI().toString() + "#index";
        return cacheIdentifier;
    }

    private static boolean readOnly(Hints hints) {
        return hints == null ? false : hints.getBoolean(Hints.OBJECTS_READ_ONLY);
    }

    public PGIndexDatabase(final ConfigDatabase configdb, final Environment config,
            final boolean readOnly) {
        super(configdb, config, readOnly);
    }

    @Override
    public void open() {
        super.open();
        repositoryId = config.getRepositoryId();
        if (this.dataSource != null) {
            if (!PGStorage.tableExists(dataSource, config.getTables().index())) {
                PGStorage.createTables(config);
            }
        }
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        StorageType.INDEX.configure(configdb, FORMAT_NAME, VERSION);
    }

    @Override
    public boolean checkConfig() throws RepositoryConnectionException {
        return StorageType.INDEX.verify(configdb, FORMAT_NAME, VERSION);
    }

    @Override
    public void checkWritable() {
        checkOpen();
        if (readOnly) {
            throw new IllegalStateException("db is read only.");
        }
    }

    @Override
    protected String objectsTable() {
        return config.getTables().indexObjects();
    }

    @Override
    protected String tableNameForType(RevObject.TYPE type, PGId pgid) {
        final String tableName;
        if (type == null) {
            tableName = objectsTable();
        } else {
            switch (type) {
            case COMMIT:
            case FEATURE:
            case FEATURETYPE:
            case TAG:
            case TREE:
                tableName = objectsTable();
                break;
            default:
                throw new IllegalArgumentException();
            }
        }
        return tableName;
    }

    @Override
    public IndexInfo createIndexInfo(String treeName, String attributeName, IndexType strategy,
            @Nullable Map<String, Object> metadata) {
        IndexInfo index = new IndexInfo(treeName, attributeName, strategy, metadata);
        final String sql = format(
                "INSERT INTO %s (repository, treeName, attributeName, strategy, metadata) VALUES(?, ?, ?, ?, ?)",
                config.getTables().index());

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try (PreparedStatement ps = cx.prepareStatement(
                    log(sql, log, repositoryId, treeName, attributeName, strategy, metadata));
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
                ps.setInt(1, repositoryId);
                ps.setString(2, treeName);
                ps.setString(3, attributeName);
                ps.setString(4, strategy.toString());
                final Map<String, Object> indexMetadata = index.getMetadata();
                if (indexMetadata.isEmpty()) {
                    ps.setNull(5, java.sql.Types.OTHER, "bytea");
                } else {
                    DataOutput out = ByteStreams.newDataOutput(outStream);
                    valueEncoder.writeMap(indexMetadata, out);
                    ps.setBytes(5, outStream.toByteArray());
                }
                ps.executeUpdate();
                cx.commit();
            } catch (SQLException | IOException e) {
                rollbackAndRethrow(cx, e);
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return index;
    }

    @Override
    public IndexInfo updateIndexInfo(String treeName, String attributeName, IndexType strategy,
            Map<String, Object> metadata) {
        IndexInfo index = new IndexInfo(treeName, attributeName, strategy, metadata);
        final String deleteSql = format(
                "DELETE FROM %s WHERE repository = ? AND treeName = ? AND attributeName = ?",
                config.getTables().index());
        final String insertSql = format(
                "INSERT INTO %s (repository, treeName, attributeName, strategy, metadata) VALUES(?, ?, ?, ?, ?)",
                config.getTables().index());

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try {
                try (PreparedStatement ps = cx.prepareStatement(
                        log(deleteSql, log, repositoryId, treeName, attributeName))) {
                    ps.setInt(1, repositoryId);
                    ps.setString(2, treeName);
                    ps.setString(3, attributeName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cx.prepareStatement(log(insertSql, log, repositoryId,
                        treeName, attributeName, strategy, metadata));
                        ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
                    ps.setInt(1, repositoryId);
                    ps.setString(2, treeName);
                    ps.setString(3, attributeName);
                    ps.setString(4, strategy.toString());
                    final Map<String, Object> indexMetadata = index.getMetadata();
                    if (indexMetadata.isEmpty()) {
                        ps.setNull(5, java.sql.Types.OTHER, "bytea");
                    } else {
                        DataOutput out = ByteStreams.newDataOutput(outStream);
                        valueEncoder.writeMap(indexMetadata, out);
                        ps.setBytes(5, outStream.toByteArray());
                    }
                    ps.executeUpdate();
                } catch (IOException e) {
                    rollbackAndRethrow(cx, e);
                }
                cx.commit();
            } catch (SQLException e) {
                cx.rollback();
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return index;
    }

    @Override
    public Optional<IndexInfo> getIndexInfo(String treeName, String attributeName) {
        final String sql = format(
                "SELECT strategy, metadata FROM %s WHERE repository = ? AND treeName = ? AND attributeName = ?",
                config.getTables().index());

        IndexInfo index = null;

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx
                    .prepareStatement(log(sql, log, repositoryId, treeName, attributeName))) {
                ps.setInt(1, repositoryId);
                ps.setString(2, treeName);
                ps.setString(3, attributeName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        IndexType strategy = IndexType.valueOf(rs.getString(1));
                        byte[] metadataBytes = rs.getBytes(2);
                        Map<String, Object> metadata = null;
                        if (metadataBytes != null) {
                            try (ByteArrayInputStream metadataStream = new ByteArrayInputStream(
                                    metadataBytes)) {
                                DataInput in = new DataInputStream(metadataStream);
                                metadata = valueEncoder.readMap(in);
                            }
                        }
                        index = new IndexInfo(treeName, attributeName, strategy, metadata);
                    }
                }
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }

        return Optional.fromNullable(index);
    }

    @Override
    public List<IndexInfo> getIndexInfos(String treeName) {
        final String sql = format(
                "SELECT attributeName, strategy, metadata FROM %s WHERE repository = ? AND treeName = ?",
                config.getTables().index());

        List<IndexInfo> indexes = Lists.newArrayList();

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx
                    .prepareStatement(log(sql, log, repositoryId, treeName))) {
                ps.setInt(1, repositoryId);
                ps.setString(2, treeName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String attributeName = rs.getString(1);
                        IndexType strategy = IndexType.valueOf(rs.getString(2));
                        byte[] metadataBytes = rs.getBytes(3);
                        Map<String, Object> metadata = null;
                        if (metadataBytes != null) {
                            try (ByteArrayInputStream metadataStream = new ByteArrayInputStream(
                                    metadataBytes)) {
                                DataInput in = new DataInputStream(metadataStream);
                                metadata = valueEncoder.readMap(in);
                            }
                        }
                        indexes.add(new IndexInfo(treeName, attributeName, strategy, metadata));
                    }
                }
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }

        return indexes;
    }

    @Override
    public List<IndexInfo> getIndexInfos() {
        final String sql = format(
                "SELECT treeName, attributeName, strategy, metadata FROM %s WHERE repository = ?",
                config.getTables().index());

        List<IndexInfo> indexes = Lists.newArrayList();

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, log, repositoryId))) {
                ps.setInt(1, repositoryId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String treeName = rs.getString(1);
                        String attributeName = rs.getString(2);
                        IndexType strategy = IndexType.valueOf(rs.getString(3));
                        byte[] metadataBytes = rs.getBytes(4);
                        Map<String, Object> metadata = null;
                        if (metadataBytes != null) {
                            try (ByteArrayInputStream metadataStream = new ByteArrayInputStream(
                                    metadataBytes)) {
                                DataInput in = new DataInputStream(metadataStream);
                                metadata = valueEncoder.readMap(in);
                            }
                        }
                        indexes.add(new IndexInfo(treeName, attributeName, strategy, metadata));
                    }
                }
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }

        return indexes;
    }

    @Override
    public boolean dropIndex(IndexInfo index) {
        final String deleteSql = format(
                "DELETE FROM %s WHERE repository = ? AND treeName = ? AND attributeName = ?",
                config.getTables().index());
        int deletedRows = 0;
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try (PreparedStatement ps = cx.prepareStatement(log(deleteSql, log, repositoryId,
                    index.getTreeName(), index.getAttributeName()))) {
                ps.setInt(1, repositoryId);
                ps.setString(2, index.getTreeName());
                ps.setString(3, index.getAttributeName());
                deletedRows = ps.executeUpdate();
                cx.commit();
            } catch (SQLException e) {
                cx.rollback();
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (deletedRows > 0) {
            clearIndex(index);
            return true;
        }
        return false;
    }

    @Override
    public void clearIndex(IndexInfo index) {
        PGId pgIndexId = PGId.valueOf(index.getId());
        final String deleteSql = format(
                "DELETE FROM %s WHERE repository = ? AND ((indexId).h1) = ? AND indexId = CAST(ROW(?,?,?) AS OBJECTID)",
                config.getTables().indexMappings());
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try (PreparedStatement ps = cx
                    .prepareStatement(log(deleteSql, log, repositoryId, pgIndexId))) {
                ps.setInt(1, repositoryId);
                ps.setInt(2, pgIndexId.hash1());
                pgIndexId.setArgs(ps, 3);
                ps.executeUpdate();
                cx.commit();
            } catch (SQLException e) {
                cx.rollback();
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addIndexedTree(IndexInfo index, ObjectId originalTree, ObjectId indexedTree) {
        PGId pgIndexId = PGId.valueOf(index.getId());
        PGId pgTreeId = PGId.valueOf(originalTree);
        PGId pgIndexedTreeId = PGId.valueOf(indexedTree);
        final String deleteSql = format(
                "DELETE FROM %s WHERE repository = ? AND ((indexId).h1) = ? AND indexId = CAST(ROW(?,?,?) AS OBJECTID)"
                        + " AND ((treeId).h1) = ? AND treeId = CAST(ROW(?,?,?) AS OBJECTID)",
                config.getTables().indexMappings());
        final String insertSql = format(
                "INSERT INTO %s (repository, indexId, treeId, indexTreeId) VALUES(?, ROW(?,?,?), ROW(?,?,?), ROW(?,?,?))",
                config.getTables().indexMappings());
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try {
                try (PreparedStatement ps = cx
                        .prepareStatement(log(deleteSql, log, repositoryId, pgIndexId, pgTreeId))) {
                    ps.setInt(1, repositoryId);
                    ps.setInt(2, pgIndexId.hash1());
                    pgIndexId.setArgs(ps, 3);
                    ps.setInt(6, pgTreeId.hash1());
                    pgTreeId.setArgs(ps, 7);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = cx.prepareStatement(
                        log(insertSql, log, repositoryId, pgIndexId, pgTreeId, pgIndexedTreeId))) {
                    ps.setInt(1, repositoryId);
                    pgIndexId.setArgs(ps, 2);
                    pgTreeId.setArgs(ps, 5);
                    pgIndexedTreeId.setArgs(ps, 8);
                    ps.executeUpdate();
                }
                cx.commit();
            } catch (SQLException e) {
                cx.rollback();
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<ObjectId> resolveIndexedTree(IndexInfo index, ObjectId treeId) {
        final PGId pgIndexId = PGId.valueOf(index.getId());
        final PGId pgTreeId = PGId.valueOf(treeId);
        final String sql = format(
                "SELECT ((indexTreeId).h1), ((indexTreeId).h2), ((indexTreeId).h3) FROM %s"
                        + " WHERE repository = ? AND ((indexId).h1) = ? AND indexId = CAST(ROW(?,?,?) AS OBJECTID)"
                        + " AND ((treeId).h1) = ? AND treeId = CAST(ROW(?,?,?) AS OBJECTID) ",
                config.getTables().indexMappings());

        ObjectId indexedTreeId = null;

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx
                    .prepareStatement(log(sql, log, repositoryId, pgIndexId, pgTreeId))) {
                ps.setInt(1, repositoryId);
                ps.setInt(2, pgIndexId.hash1());
                pgIndexId.setArgs(ps, 3);
                ps.setInt(6, pgTreeId.hash1());
                pgTreeId.setArgs(ps, 7);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        indexedTreeId = PGId.valueOf(rs, 1).toObjectId();
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return Optional.fromNullable(indexedTreeId);
    }

    public @Override AutoCloseableIterator<IndexTreeMapping> resolveIndexedTrees(IndexInfo index) {
        final PGId pgIndexId = PGId.valueOf(index.getId());

        final String sql = format("SELECT "//
                + "((treeid).h1), ((treeid).h2), ((treeid).h3), "//
                + "((indexTreeId).h1), ((indexTreeId).h2), ((indexTreeId).h3) FROM %s"//
                + " WHERE repository = ? AND ((indexId).h1) = ? AND indexId = CAST(ROW(?,?,?) AS OBJECTID)",
                config.getTables().indexMappings());

        List<IndexTreeMapping> mappings = new ArrayList<>();
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx
                    .prepareStatement(log(sql, log, repositoryId, pgIndexId))) {
                ps.setInt(1, repositoryId);
                ps.setInt(2, pgIndexId.hash1());
                pgIndexId.setArgs(ps, 3);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ObjectId treeId = PGId.valueOf(rs, 1).toObjectId();
                        ObjectId indexedTreeId = PGId.valueOf(rs, 4).toObjectId();
                        mappings.add(new IndexTreeMapping(treeId, indexedTreeId));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return AutoCloseableIterator.fromIterator(mappings.iterator());
    }

//    public @Override void copyIndexTo(final IndexInfo index, final IndexDatabase target) {
//    }

    public @Override void copyIndexesTo(final IndexDatabase target)
            throws UnsupportedOperationException {
        if (target instanceof PGIndexDatabase) {
            PGIndexDatabase pgtarget = (PGIndexDatabase) target;
            ConnectionConfig cfg = this.config.connectionConfig;
            final boolean samedb = cfg.isSameDatabase(pgtarget.config.connectionConfig);
            if (samedb) {
                final Version serverVersion = PGStorage.getServerVersion(config);
                if (serverVersion.greatherOrEqualTo(Version.V9_5_0)) {
                    fastCopyIndexesWithUpsert(pgtarget);
                } else {
                    fastCopyIndexesPG94(pgtarget);
                }
                return;
            }
        }
        new IndexDuplicator(this, target).run();
    }

    private void fastCopyIndexesPG94(PGIndexDatabase pgtarget) {
        final int srcRepoId = this.config.getRepositoryId();
        final int targetRepoId = pgtarget.config.getRepositoryId();

        final String indexSql;
        final String mappingsSql;
        {
            final String srcIndexTable = this.config.getTables().index();
            final String targetIndexTable = pgtarget.config.getTables().index();
            indexSql = "insert into " + targetIndexTable//
                    + " select " + targetRepoId + ", treename, attributename, strategy, metadata "
                    + " from " + srcIndexTable + " src where "//
                    + " repository = " + srcRepoId//
                    + " and not exists(select treename, attributename "//
                    + " from " + targetIndexTable + " target "//
                    + " where target.repository = " + targetRepoId
                    + " and target.treename = src.treename and target.attributename = src.attributename)";
        }
        {
            final String srcIndexMappingsTable = this.config.getTables().indexMappings();
            final String targetIndexMappingsTable = pgtarget.config.getTables().indexMappings();

            mappingsSql = "insert into " + targetIndexMappingsTable//
                    + " select " + targetRepoId + ", indexid, treeid, indextreeid "//
                    + " from " + srcIndexMappingsTable + " src where "//
                    + " repository = " + srcRepoId //
                    + " and not exists(select indexid, treeid "//
                    + " from " + targetIndexMappingsTable + " target "//
                    + " where target.repository = " + targetRepoId
                    + " and target.indexid = src.indexid and target.treeid = src.treeid)";
        }
        fastCopyIndexes(pgtarget, indexSql, mappingsSql);
    }

    private void fastCopyIndexesWithUpsert(PGIndexDatabase pgtarget) {
        final String indexSql;
        final String mappingsSql;
        final int srcRepoId = this.config.getRepositoryId();
        final int targetRepoId = pgtarget.config.getRepositoryId();
        {
            final String srcIndexTable = this.config.getTables().index();
            final String targetIndexTable = pgtarget.config.getTables().index();
            indexSql = String.format("insert into %s "//
                    + " select %d, treename, attributename, strategy, metadata from"
                    + " %s where repository = %d on conflict do nothing", //
                    targetIndexTable, targetRepoId, srcIndexTable, srcRepoId);
        }
        {
            final String srcIndexMappingsTable = this.config.getTables().indexMappings();
            final String targetIndexMappingsTable = pgtarget.config.getTables().indexMappings();
            mappingsSql = String.format("insert into %s "//
                    + " select %d, indexid, treeid, indextreeid "
                    + " from %s where repository = %d on conflict do nothing", //
                    targetIndexMappingsTable, targetRepoId, srcIndexMappingsTable, srcRepoId);
        }
        fastCopyIndexes(pgtarget, indexSql, mappingsSql);
    }

    private void fastCopyIndexes(PGIndexDatabase pgtarget, String indexSql, String mappingsSql) {
        final boolean sameSchema = this.config.connectionConfig
                .isSameDatabaseAndSchema(pgtarget.config.connectionConfig);

        final String copyTrees;
        if (sameSchema) {
            copyTrees = "select 1";
        } else {
            String srcObjects = this.config.getTables().indexObjects();
            String targetObjects = pgtarget.config.getTables().indexObjects();
            copyTrees = "insert into " + targetObjects + " select * from " + srcObjects;
        }
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try {
                execute(copyTrees, cx);
                execute(indexSql, cx);
                execute(mappingsSql, cx);
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

    private void execute(final String statement, Connection cx) throws SQLException {
        try (Statement st = cx.createStatement()) {
            log(statement, log);
            st.execute(statement);
        } catch (SQLException e) {
            log.warn("Error running query '{}'", statement, e);
            throw e;
        }
    }

}