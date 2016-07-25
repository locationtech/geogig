/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import org.locationtech.geogig.storage.ConnectionManager;
import org.rocksdb.CompactionStyle;
import org.rocksdb.CompressionType;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

class RocksConnectionManager extends ConnectionManager<DBOptions, DBHandle> {

    private static final Logger LOG = LoggerFactory.getLogger(RocksConnectionManager.class);

    static final RocksConnectionManager INSTANCE = new RocksConnectionManager();

    @Override
    protected DBHandle connect(DBOptions dbopts) {

        LOG.debug("opening {}", dbopts);

        RocksDB.loadLibrary();

        Options options = new Options();
        options.setCreateIfMissing(true)//
                .setAdviseRandomOnOpen(true)//
                .setAllowMmapReads(true)//
                .setAllowMmapWrites(true)//
                .setAllowOsBuffer(true)//
                .setBytesPerSync(64 * 1024 * 1024)//
                .setCompactionStyle(CompactionStyle.LEVEL)
                .setCompressionType(CompressionType.SNAPPY_COMPRESSION);

        RocksDB db;
        final String path = dbopts.getDbPath();
        final boolean readOnly = dbopts.isReadOnly();
        try {
            if (readOnly) {
                db = RocksDB.openReadOnly(options, path);
            } else {
                db = RocksDB.open(options, path);
            }
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }

        return new DBHandle(dbopts, options, db);
    }

    @Override
    protected void disconnect(DBHandle connection) {
        LOG.debug("closing {}", connection.config);
        connection.close();
    }

}
