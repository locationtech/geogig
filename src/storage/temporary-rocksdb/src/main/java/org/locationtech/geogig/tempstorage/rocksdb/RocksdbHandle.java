/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.tempstorage.rocksdb;

import java.io.File;
import java.nio.file.Path;

import org.eclipse.jdt.annotation.Nullable;
import org.rocksdb.CompressionType;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RocksdbHandle {

    private static final Logger LOG = LoggerFactory.getLogger(RocksdbHandle.class);

    final Options options;

    final RocksDB db;

    private Path dbpath;

    public RocksdbHandle(final Path dbpath, final Options options, final RocksDB db) {
        this.dbpath = dbpath;
        this.options = options;
        this.db = db;
    }

    /**
     * Closes the rocksdb database and deletes its directory
     */
    public synchronized void dispose() {
        LOG.debug("Closing temporary rocksdb {}", dbpath);
        close(db);
        close(options);
        delete(dbpath.toFile());
    }

    private void close(AutoCloseable nativeObject) {
        try {
            nativeObject.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void delete(@Nullable File file) {
        if (null == file) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File f : children) {
                    delete(f);
                }
            }
        }
        file.delete();
    }

    public static RocksdbHandle create(Path targetDir) {
        RocksDB.loadLibrary();

        final String os = System.getProperty("os.name");
        final boolean isWindows = os.toLowerCase().contains("windows");
        final boolean safeToUseMMappedFiles = !isWindows;

        Options options = new Options();
        options.setCreateIfMissing(true)//
                .setAdviseRandomOnOpen(true)//
                .setAllowMmapReads(safeToUseMMappedFiles)//
                .setAllowMmapWrites(safeToUseMMappedFiles)//
                // .setWriteBufferSize(8 * 1024 * 1024)//
                // .setMaxWriteBufferNumber(8)//
                // .setMaxBackgroundCompactions(1)//
                // .setMinWriteBufferNumberToMerge(4)//
                // .setBytesPerSync(32 * 1024 * 1024)//
                // ;// no compression due to a limitation on Windows where the compression library
                // doesn't come
                // embedded in rocksdb fat jar
                // .setCompactionStyle(CompactionStyle.LEVEL)
                .setCompressionType(CompressionType.NO_COMPRESSION);

        RocksDB db;
        final String path = targetDir.toAbsolutePath().toString();
        try {
            db = RocksDB.open(options, path);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }

        RocksdbHandle dbHandle = new RocksdbHandle(targetDir, options, db);
        LOG.debug("Created rocksdb {}", targetDir);
        return dbHandle;
    }
}
