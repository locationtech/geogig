/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.AbstractObjectDatabase;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;
import org.locationtech.geogig.storage.datastream.LZFSerializationFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.inject.Inject;

/**
 * Provides an implementation of a GeoGig object database that utilizes the file system for the
 * storage of objects.
 * 
 * @see AbstractObjectDatabase
 */
public class FileObjectDatabase extends AbstractObjectDatabase implements ObjectDatabase {

    private final Platform platform;

    private final ConfigDatabase configDB;

    private final String databaseName;

    private File dataRoot;

    private String dataRootPath;

    private FileConflictsDatabase conflicts;

    private FileBlobStore blobStore;

    private Hints hints;

    /**
     * Constructs a new {@code FileObjectDatabase} using the given platform.
     * 
     * @param platform the platform to use.
     */
    @Inject
    public FileObjectDatabase(final Platform platform, final ConfigDatabase configDB,
            final Hints hints) {
        this(platform, "objects", configDB);
        this.hints = hints;
    }

    protected FileObjectDatabase(final Platform platform, final String databaseName,
            final ConfigDatabase configDB) {
        super(new LZFSerializationFactory(DataStreamSerializationFactoryV1.INSTANCE));
        checkNotNull(platform);
        checkNotNull(databaseName);
        this.platform = platform;
        this.databaseName = databaseName;
        this.configDB = configDB;
        this.blobStore = new FileBlobStore(platform);
    }

    protected File getDataRoot() {
        return dataRoot;
    }

    protected String getDataRootPath() {
        return dataRootPath;
    }

    /**
     * @return true if the database is open, false otherwise
     */
    @Override
    public boolean isOpen() {
        return dataRoot != null;
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
        if (isOpen()) {
            return;
        }
        final Optional<URI> repoUrl = new ResolveGeogigURI(platform, hints).call();
        checkState(repoUrl.isPresent(), "Can't find geogig repository home");

        final File repoDir = new File(repoUrl.get());
        this.conflicts = new FileConflictsDatabase(repoDir);

        dataRoot = new File(repoDir, databaseName);

        if (!dataRoot.exists() && !dataRoot.mkdirs()) {
            throw new IllegalStateException(
                    "Can't create environment: " + dataRoot.getAbsolutePath());
        }
        if (!dataRoot.isDirectory()) {
            throw new IllegalStateException(
                    "Environment but is not a directory: " + dataRoot.getAbsolutePath());
        }
        if (!dataRoot.canWrite()) {
            throw new IllegalStateException(
                    "Environment is not writable: " + dataRoot.getAbsolutePath());
        }
        dataRootPath = dataRoot.getAbsolutePath();
        try {
            conflicts.open();
        } finally {
            try {
                blobStore.open();
            } catch (RuntimeException e) {
                conflicts.close();
                throw e;
            }
        }
    }

    /**
     * Closes the database.
     */
    @Override
    public void close() {
        dataRoot = null;
        dataRootPath = null;
        try {
            if (conflicts != null) {
                conflicts.close();
            }
        } finally {
            blobStore.close();
        }
    }

    /**
     * Determines if the given {@link ObjectId} exists in the object database.
     * 
     * @param id the id to search for
     * @return true if the object exists, false otherwise
     */
    @Override
    public boolean exists(final ObjectId id) {
        File f = filePath(id);
        return f.exists();
    }

    @Override
    protected InputStream getRawInternal(ObjectId id, boolean failIfNotFound) {
        File f = filePath(id);
        try {
            return new FileInputStream(f);
        } catch (FileNotFoundException e) {
            if (failIfNotFound) {
                throw Throwables.propagate(e);
            }
            return null;
        }
    }

    /**
     * @see org.locationtech.geogig.storage.AbstractObjectDatabase#putInternal(org.locationtech.geogig.model.ObjectId,
     *      byte[])
     */
    @Override
    protected boolean putInternal(final ObjectId id, final byte[] rawData) {
        final File f = filePath(id);
        if (f.exists()) {
            return false;
        }

        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = new FileOutputStream(f);
        } catch (FileNotFoundException dirDoesNotExist) {
            final File parent = f.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new RuntimeException("Can't create " + parent.getAbsolutePath());
            }
            try {
                fileOutputStream = new FileOutputStream(f);
            } catch (FileNotFoundException e) {
                throw Throwables.propagate(e);
            }
        }
        try {
            fileOutputStream.write(rawData);
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return true;
    }

    /**
     * Deletes the object with the provided {@link ObjectId id} from the database.
     * 
     * @param objectId the id of the object to delete
     * @return true if the object was deleted, false if it was not found
     */
    @Override
    public void delete(ObjectId objectId) {
        File filePath = filePath(objectId);
        filePath.delete();
    }

    private File filePath(final ObjectId id) {
        final String idName = id.toString();
        return filePath(idName);
    }

    private File filePath(final String objectId) {
        checkNotNull(objectId);
        checkArgument(objectId.length() > 4, "partial object id is too short");

        final char[] path1 = new char[2];
        final char[] path2 = new char[2];
        objectId.getChars(0, 2, path1, 0);
        objectId.getChars(2, 4, path2, 0);

        StringBuilder sb = new StringBuilder(dataRootPath);
        sb.append(File.separatorChar).append(path1).append(File.separatorChar).append(path2)
                .append(File.separatorChar).append(objectId);
        String filePath = sb.toString();
        return new File(filePath);
    }

    /**
     * Searches the database for {@link ObjectId}s that match the given partial id.
     * 
     * @param partialId the partial id to search for
     * @return a list of matching results
     */
    @Override
    public List<ObjectId> lookUp(final String partialId) {
        Preconditions.checkArgument(partialId.length() > 7,
                "partial id must be at least 8 characters long: ", partialId);
        File parent = filePath(partialId).getParentFile();
        String[] list = parent.list();
        if (null == list) {
            return ImmutableList.of();
        }
        Builder<ObjectId> builder = ImmutableList.builder();
        for (String oid : list) {
            if (oid.startsWith(partialId)) {
                builder.add(ObjectId.valueOf(oid));
            }
        }
        return builder.build();
    }

    @Override
    protected List<ObjectId> lookUpInternal(byte[] raw) {
        throw new UnsupportedOperationException(
                "This method should not be called, we override lookUp(String) directly");
    }

    @Override
    public Iterator<RevObject> getAll(Iterable<ObjectId> ids, BulkOpListener listener) {
        throw new UnsupportedOperationException("This method is not yet implemented");
    }

    @Override
    public void deleteAll(Iterator<ObjectId> ids, final BulkOpListener listener) {
        throw new UnsupportedOperationException("This method is not yet implemented");
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        StorageType.OBJECT.configure(configDB, "file", "1.0");
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        StorageType.OBJECT.verify(configDB, "file", "1.0");
    }

    @Override
    public String toString() {
        return String.format("%s[dir: %s, name: %s]", getClass().getSimpleName(),
                dataRoot == null ? "<unset>" : dataRoot.getAbsolutePath(), databaseName);
    }

    @Override
    public ConflictsDatabase getConflictsDatabase() {
        return conflicts;
    }

    @Override
    public BlobStore getBlobStore() {
        return blobStore;
    }

    @Override
    public <T extends RevObject> Iterator<T> getAll(Iterable<ObjectId> ids, BulkOpListener listener,
            Class<T> type) {
        throw new UnsupportedOperationException();
    }
}
