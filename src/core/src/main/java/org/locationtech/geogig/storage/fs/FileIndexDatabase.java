/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage.fs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Index;
import org.locationtech.geogig.repository.Index.IndexType;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.StorageType;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hasher;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.inject.Inject;

public class FileIndexDatabase extends FileObjectStore implements IndexDatabase {

    private final ConfigDatabase configDB;

    private Map<String, List<Index>> indexes;

    /**
     * Constructs a new {@code FileIndexDatabase} using the given platform.
     * 
     * @param platform the platform to use.
     */
    @Inject
    public FileIndexDatabase(final Platform platform, final ConfigDatabase configDB,
            final Hints hints) {
        super(platform, "index", configDB, hints);
        this.configDB = configDB;
        this.indexes = new HashMap<String, List<Index>>();
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Opens the database for use by GeoGig.
     */
    @Override
    public void open() {
        super.open();

        for (final File indexFile : getDataRoot().listFiles()) {
            if (indexFile.getAbsolutePath().endsWith(".index")) {
                addIndex(readIndex(indexFile));
            }
        }
    }

    private void addIndex(Index index) {
        String treeName = index.getTreeName();
        if (indexes.containsKey(treeName)) {
            indexes.get(treeName).add(index);
        } else {
            indexes.put(treeName, Lists.newArrayList(index));
        }
    }

    /**
     * Closes the database.
     */
    @Override
    public void close() {
        super.close();
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        StorageType.INDEX.configure(configDB, "file", "1.0");
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        StorageType.INDEX.verify(configDB, "file", "1.0");
    }

    @Override
    public Index createIndex(String treeName, String attributeName, IndexType strategy) {
        Index index = new Index(treeName, attributeName, strategy);
        addIndex(index);
        UUID id = UUID.randomUUID();
        File indexFile = new File(getDataRoot(), id.toString() + ".index");
        writeIndex(indexFile, index);
        return index;
    }

    private void writeIndex(File indexFile, Index index) {
        BufferedWriter writer;
        try {
            writer = Files.newWriter(indexFile, Charsets.UTF_8);
            writer.write(index.getTreeName());
            writer.newLine();
            writer.write(index.getAttributeName());
            writer.newLine();
            writer.write(index.getIndexType().toString());
            writer.newLine();
            writer.close();
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    private Index readIndex(File indexFile) {
        String treeName, attributeName;
        IndexType indexType;
        FileInputStream stream = null;
        BufferedReader reader = null;
        Index index = null;
        try {
            stream = new FileInputStream(indexFile);
            reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            treeName = reader.readLine();
            attributeName = reader.readLine();
            indexType = IndexType.valueOf(reader.readLine());
            index = new Index(treeName, attributeName, indexType);
        } catch (IOException e) {
            Throwables.propagate(e);
        } finally {
            Closeables.closeQuietly(reader);
            Closeables.closeQuietly(stream);
        }
        return index;
    }

    @Override
    public Optional<Index> getIndex(String treeName, String attributeName) {
        if (indexes.containsKey(treeName)) {
            for (Index index : indexes.get(treeName)) {
                if (index.getAttributeName().equals(attributeName)) {
                    return Optional.of(index);
                }
            }
        }
        return Optional.absent();
    }

    @Override
    public void updateIndex(Index index, ObjectId originalTree, ObjectId indexedTree) {
        ObjectId indexTreeLookupId = computeIndexTreeLookupId(index.getId(), originalTree);
        this.putInternal(indexTreeLookupId, indexedTree.getRawValue());
    }

    @Override
    public Optional<ObjectId> resolveTreeId(Index index, ObjectId treeId) {
        ObjectId indexTreeLookupId = computeIndexTreeLookupId(index.getId(), treeId);
        InputStream indexTreeStream = this.getRawInternal(indexTreeLookupId, false);
        if (indexTreeStream != null) {
            try {
                byte[] indexTreeBytes = ByteStreams.toByteArray(indexTreeStream);
                indexTreeStream.close();
                return Optional.of(ObjectId.createNoClone(indexTreeBytes));
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }
        return Optional.absent();
    }

    private ObjectId computeIndexTreeLookupId(ObjectId indexId, ObjectId treeId) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        hasher.putBytes(indexId.getRawValue());
        hasher.putBytes(treeId.getRawValue());
        return ObjectId.createNoClone(hasher.hash().asBytes());
    }
}
