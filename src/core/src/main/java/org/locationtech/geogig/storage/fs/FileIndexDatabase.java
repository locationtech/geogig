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

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.impl.IndexInfoSerializer;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hasher;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

public class FileIndexDatabase extends FileObjectStore implements IndexDatabase {

    private final ConfigDatabase configDB;

    private final Repository repository;

    /**
     * Constructs a new {@code FileIndexDatabase} using the given platform.
     * 
     * @param platform the platform to use.
     */
    @Inject
    public FileIndexDatabase(final Platform platform, final ConfigDatabase configDB,
            final Hints hints, final Repository repository) {
        super(platform, "index", configDB, hints);
        this.configDB = configDB;
        this.repository = repository;
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
    public boolean checkConfig() throws RepositoryConnectionException {
        return StorageType.INDEX.verify(configDB, "file", "1.0");
    }

    @Override
    public IndexInfo createIndex(String treeName, String attributeName, IndexType strategy,
            @Nullable Map<String, Object> metadata) {
        IndexInfo index = new IndexInfo(treeName, attributeName, strategy, metadata);
        try (ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
            DataOutput out = ByteStreams.newDataOutput(outStream);
            IndexInfoSerializer.serialize(index, out);
            this.putInternal(index.getId(), outStream.toByteArray());
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return index;
    }

    @Override
    public Optional<IndexInfo> getIndex(String treeName, String attributeName) {
        ObjectId indexId = IndexInfo.getIndexId(treeName, attributeName);
        try (InputStream inputStream = this.getRawInternal(indexId, false)) {
            if (inputStream != null) {
                DataInput in = new DataInputStream(inputStream);
                IndexInfo index = IndexInfoSerializer.deserialize(in);
                return Optional.of(index);
            }
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return Optional.absent();
    }

    @Override
    public List<IndexInfo> getIndexes(String treeName) {
        Optional<RevFeatureType> featureTypeOpt = repository.command(ResolveFeatureType.class)
                .setRefSpec(Ref.HEAD + ":" + treeName).call();

        List<IndexInfo> indexes = Lists.newArrayList();
        if (featureTypeOpt.isPresent()) {
            RevFeatureType treeType = featureTypeOpt.get();
            for (PropertyDescriptor descriptor : treeType.descriptors()) {
                Optional<IndexInfo> index = getIndex(treeName, descriptor.getName().toString());
                if (index.isPresent()) {
                    indexes.add(index.get());
                }
            }
        }
        return indexes;
    }

    @Override
    public void addIndexedTree(IndexInfo index, ObjectId originalTree, ObjectId indexedTree) {
        ObjectId indexTreeLookupId = computeIndexTreeLookupId(index.getId(), originalTree);
        this.putInternal(indexTreeLookupId, indexedTree.getRawValue());
    }

    @Override
    public Optional<ObjectId> resolveIndexedTree(IndexInfo index, ObjectId treeId) {
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
