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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.storage.impl.ConnectionManager;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompressionType;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

class RocksConnectionManager extends ConnectionManager<DBConfig, DBHandle> {

    private static final Logger LOG = LoggerFactory.getLogger(RocksConnectionManager.class);

    static final RocksConnectionManager INSTANCE = new RocksConnectionManager();

    /**
     * Determine if a database exists at the given path.
     * 
     * @param path the path to the database
     * @return {@code true} if the database existed, {@code false} otherwise
     */
    public boolean exists(String path) {
        return new File(path).exists();
    }

    @Override
    protected DBHandle connect(DBConfig dbconfig) {

        LOG.debug("opening {}", dbconfig);

        RocksDB.loadLibrary();

        final String os = System.getProperty("os.name");
        final boolean isWindows = os.toLowerCase().contains("windows");
        final boolean safeToUseMMappedFiles = !isWindows;

        org.rocksdb.DBOptions dbOptions = new org.rocksdb.DBOptions();
        dbOptions.setCreateIfMissing(true)//
                .setAdviseRandomOnOpen(true)//
                .setAllowMmapReads(safeToUseMMappedFiles)//
                .setAllowMmapWrites(safeToUseMMappedFiles)//
                .setBytesPerSync(64 * 1024 * 1024);

        RocksDB db;
        final String path = dbconfig.getDbPath();
        final List<String> colFamilyNames;
        try {
            Function<byte[], String> fn = new Function<byte[], String>() {
                @Override
                public String apply(byte[] ba) {
                    return new String(ba, Charsets.UTF_8);
                }
            };
            List<byte[]> columnFamilies = RocksDB.listColumnFamilies(new Options(), path);
            colFamilyNames = columnFamilies.stream().map(fn).collect(Collectors.toList());
        } catch (RocksDBException e) {
            dbOptions.close();
            throw new RuntimeException(e);
        }

        // at least the "default" column family shall exists if the db was already created
        final boolean dbExists = !colFamilyNames.isEmpty();
        final boolean metadataExists = colFamilyNames.contains("metadata");
        final boolean readOnly = dbconfig.isReadOnly();
        @Nullable
        ColumnFamilyHandle metadata = null;
        Map<String, ColumnFamilyHandle> extraColumns = new HashMap<>();
        try {
            List<ColumnFamilyDescriptor> colDescriptors = new ArrayList<>();
            for (String name : colFamilyNames) {
                byte[] colFamilyName = name.getBytes(Charsets.UTF_8);
                ColumnFamilyOptions colFamilyOptions = newColFamilyOptions();
                colDescriptors.add(new ColumnFamilyDescriptor(colFamilyName, colFamilyOptions));
            }

            DBHandle dbHandle;
            if (readOnly) {
                List<ColumnFamilyHandle> colFamiliesTarget = new ArrayList<>();
                Preconditions.checkState(dbExists, "database does not exist: %s", path);
                db = RocksDB.openReadOnly(dbOptions, path, colDescriptors, colFamiliesTarget);
                if (metadataExists) {
                    metadata = colFamiliesTarget.get(colFamilyNames.indexOf("metadata"));
                }
                for (int i = 0; i < colDescriptors.size(); i++) {
                    String name = colFamilyNames.get(i);
                    if (!"metadata".equals(name)) {
                        ColumnFamilyHandle handle = colFamiliesTarget.get(i);
                        extraColumns.put(name, handle);
                    }
                }
                dbHandle = new DBHandle(dbconfig, dbOptions, db, metadata, extraColumns);
            } else {
                if (!dbExists) {
                    colDescriptors.add(newColDescriptor("default"));
                    for (String name : dbconfig.getColumnFamilyNames()) {
                        if (colFamilyNames.indexOf(name) > -1) {
                            colDescriptors.add(newColDescriptor(name));
                        }
                    }
                }

                List<ColumnFamilyHandle> colFamiliesTarget = new ArrayList<>();
                db = RocksDB.open(dbOptions, path, colDescriptors, colFamiliesTarget);
                if (metadataExists) {
                    metadata = colFamiliesTarget.get(colFamilyNames.indexOf("metadata"));
                } else {
                    ColumnFamilyDescriptor mdd = newColDescriptor("metadata");
                    metadata = db.createColumnFamily(mdd);
                }
                // This usually happens only when creating a database -
                // Column Family "default" is not handled correctly; this
                // makes sure its put into the extraColumns so it will be correctly
                // released when the DB is closed.
                if (!dbconfig.getColumnFamilyNames().contains("default")) {
                    int index = 0;
                    for (ColumnFamilyDescriptor descriptor : colDescriptors) {
                        if (new String(descriptor.columnFamilyName()).equals("default")) {
                            extraColumns.put("default", colFamiliesTarget.get(index));
                        }
                        index++;
                    }

                }
                for (String name : dbconfig.getColumnFamilyNames()) {
                    ColumnFamilyDescriptor colDescriptor;
                    ColumnFamilyHandle colHandle;
                    if (colFamilyNames.indexOf(name) == -1) {
                        colDescriptor = newColDescriptor(name);
                        colHandle = db.createColumnFamily(colDescriptor);
                    } else {
                        int colIndex = colFamilyNames.indexOf(name);
                        colHandle = colFamiliesTarget.get(colIndex);
                    }
                    extraColumns.put(name, colHandle);
                }

                dbHandle = new DBHandle(dbconfig, dbOptions, db, metadata, extraColumns);

                // save default metadata
                if (!dbExists) {
                    ImmutableMap<String, String> defaultMetadata = dbconfig.getDefaultMetadata();
                    defaultMetadata.forEach((k, v) -> dbHandle.setMetadata(k, v));
                }
            }
            return dbHandle;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private ColumnFamilyDescriptor newColDescriptor(String name) {
        ColumnFamilyOptions options = newColFamilyOptions();
        ColumnFamilyDescriptor descriptor = new ColumnFamilyDescriptor(
                name.getBytes(Charsets.UTF_8), options);
        return descriptor;
    }

    private ColumnFamilyOptions newColFamilyOptions() {
        ColumnFamilyOptions colFamilyOptions = new ColumnFamilyOptions();

        // enable bloom filter to speed up RocksDB.get() calls
        BlockBasedTableConfig tableFormatConfig = new BlockBasedTableConfig();
        BloomFilter bloomFilter = new BloomFilter();
        tableFormatConfig.setFilter(bloomFilter);
        colFamilyOptions.setTableFormatConfig(tableFormatConfig);

        // cause the Windows jar doesn't come with
        // snappy and hence fails
        colFamilyOptions.setCompressionType(CompressionType.NO_COMPRESSION);
        return colFamilyOptions;
    }

    @Override
    protected void disconnect(DBHandle connection) {
        LOG.debug("closing {}", connection.config);
        connection.close();
    }

}
