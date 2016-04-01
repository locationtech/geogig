/* Copyright (c) 2015 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import static java.lang.String.format;
import static org.locationtech.geogig.storage.postgresql.PGStorage.log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.locationtech.geogig.storage.TransactionBlobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;

class PGBlobStore implements TransactionBlobStore {

    private static final Logger LOG = LoggerFactory.getLogger(PGBlobStore.class);

    private static final String NO_TRANSACTION = "";

    private DataSource dataSource;

    private String blobsTable;

    private String repositoryId;

    PGBlobStore(final DataSource dataSource, final String blobsTable, final String repositoryId) {
        this.dataSource = dataSource;
        this.blobsTable = blobsTable;
        this.repositoryId = repositoryId;
    }

    @Override
    public Optional<byte[]> getBlob(String path) {
        return getBlob(NO_TRANSACTION, path);
    }

    @Override
    public Optional<InputStream> getBlobAsStream(String path) {
        return getBlobAsStream(NO_TRANSACTION, path);
    }

    @Override
    public void putBlob(String path, byte[] blob) {
        putBlob(NO_TRANSACTION, path, blob);
    }

    @Override
    public void putBlob(String path, InputStream blob) {
        putBlob(NO_TRANSACTION, path, blob);
    }

    @Override
    public void removeBlob(String path) {
        removeBlob(NO_TRANSACTION, path);
    }

    @Override
    public Optional<byte[]> getBlob(final String namespace, final String path) {
        Preconditions.checkNotNull(namespace, "namespace can't be null");

        byte[] bytes = new DbOp<byte[]>() {
            @Override
            protected byte[] doRun(Connection cx) throws SQLException {
                String sql = format(
                        "SELECT blob FROM %s WHERE repository = ? AND namespace = ? AND path = ?",
                        blobsTable);
                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, repositoryId,
                        namespace, path))) {
                    ps.setString(1, repositoryId);
                    ps.setString(2, namespace);
                    ps.setString(3, path);

                    byte[] bytes = null;
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            bytes = rs.getBytes(1);
                        }
                    }
                    return bytes;
                }
            }

        }.run(dataSource);

        return Optional.fromNullable(bytes);
    }

    @Override
    public Optional<InputStream> getBlobAsStream(final String namespace, final String path) {
        Preconditions.checkNotNull(namespace, "namespace can't be null");
        Optional<byte[]> blob = getBlob(namespace, path);
        InputStream in = null;
        if (blob.isPresent()) {
            in = new ByteArrayInputStream(blob.get());
        }
        return Optional.fromNullable(in);
    }

    @Override
    public void putBlob(final String namespace, final String path, final byte[] blob) {
        Preconditions.checkNotNull(namespace, "namespace can't be null");

        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                String delete = format(
                        "DELETE FROM %s WHERE repository = ? AND namespace = ? AND path = ?",
                        blobsTable);
                String insert = format(
                        "INSERT INTO %s (repository, namespace, path, blob) VALUES (?, ?, ?, ?)",
                        blobsTable);
                cx.setAutoCommit(false);
                try {
                    try (PreparedStatement d = cx.prepareStatement(delete)) {
                        d.setString(1, repositoryId);
                        d.setString(2, namespace);
                        d.setString(3, path);
                        d.executeUpdate();
                    }
                    try (PreparedStatement i = cx.prepareStatement(insert)) {
                        i.setString(1, repositoryId);
                        i.setString(2, namespace);
                        i.setString(3, path);
                        i.setBytes(4, blob);
                        i.executeUpdate();
                    }
                    cx.commit();
                } catch (SQLException e) {
                    cx.rollback();
                    throw e;
                }
                return null;
            }
        }.run(dataSource);
    }

    @Override
    public void putBlob(String namespace, String path, InputStream blob) {
        Preconditions.checkNotNull(namespace, "namespace can't be null");
        byte[] bytes;
        try {
            bytes = ByteStreams.toByteArray(blob);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        putBlob(namespace, path, bytes);
    }

    @Override
    public void removeBlob(final String namespace, final String path) {
        Preconditions.checkNotNull(namespace, "namespace can't be null");
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                String delete = format("LOCK TABLE %s IN SHARE ROW EXCLUSIVE MODE;"
                        + "DELETE FROM %s WHERE repository = ? AND namespace = ? AND path = ?",
                        blobsTable, blobsTable);
                cx.setAutoCommit(false);
                try {
                    try (PreparedStatement d = cx.prepareStatement(delete)) {
                        d.setString(1, repositoryId);
                        d.setString(2, namespace);
                        d.setString(3, path);
                        d.executeUpdate();
                    }
                    cx.commit();
                } catch (SQLException e) {
                    cx.rollback();
                    throw e;
                }
                return null;
            }
        }.run(dataSource);

    }

    @Override
    public void removeBlobs(final String namespace) {
        Preconditions.checkNotNull(namespace, "namespace can't be null");
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                String delete = format("DELETE FROM %s WHERE repository = ? AND namespace = ?",
                        blobsTable);
                cx.setAutoCommit(false);
                try {
                    try (PreparedStatement d = cx.prepareStatement(delete)) {
                        d.setString(1, repositoryId);
                        d.setString(2, namespace);
                        d.executeUpdate();
                    }
                    cx.commit();
                } catch (SQLException e) {
                    cx.rollback();
                    throw e;
                }
                return null;
            }
        }.run(dataSource);
    }

}
