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
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.AbstractObjectDatabase;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;

import com.google.common.base.Optional;
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

    /**
     * Constructs a new {@code FileObjectDatabase} using the given platform.
     * 
     * @param platform the platform to use.
     */
    @Inject
    public FileObjectDatabase(final Platform platform, final ConfigDatabase configDB) {
        this(platform, "objects", configDB);
    }

    protected FileObjectDatabase(final Platform platform, final String databaseName,
            final ConfigDatabase configDB) {
        super(DataStreamSerializationFactoryV1.INSTANCE);
        checkNotNull(platform);
        checkNotNull(databaseName);
        this.platform = platform;
        this.databaseName = databaseName;
        this.configDB = configDB;
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

    /**
     * Opens the database for use by GeoGig.
     */
    @Override
    public void open() {
        if (isOpen()) {
            return;
        }
        final Optional<URL> repoUrl = new ResolveGeogigDir(platform).call();
        checkState(repoUrl.isPresent(), "Can't find geogig repository home");

        try {
            dataRoot = new File(new File(repoUrl.get().toURI()), databaseName);
        } catch (URISyntaxException e) {
            throw Throwables.propagate(e);
        }

        if (!dataRoot.exists() && !dataRoot.mkdirs()) {
            throw new IllegalStateException("Can't create environment: "
                    + dataRoot.getAbsolutePath());
        }
        if (!dataRoot.isDirectory()) {
            throw new IllegalStateException("Environment but is not a directory: "
                    + dataRoot.getAbsolutePath());
        }
        if (!dataRoot.canWrite()) {
            throw new IllegalStateException("Environment is not writable: "
                    + dataRoot.getAbsolutePath());
        }
        dataRootPath = dataRoot.getAbsolutePath();
    }

    /**
     * Closes the database.
     */
    @Override
    public void close() {
        dataRoot = null;
        dataRootPath = null;
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
     * @see org.locationtech.geogig.storage.AbstractObjectDatabase#putInternal(org.locationtech.geogig.api.ObjectId, byte[])
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
    public boolean delete(ObjectId objectId) {
        File filePath = filePath(objectId);
        boolean delete = filePath.delete();
        return delete;
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
    public long deleteAll(Iterator<ObjectId> ids, final BulkOpListener listener) {
        throw new UnsupportedOperationException("This method is not yet implemented");
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.OBJECT.configure(configDB, "file", "1.0");
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.OBJECT.verify(configDB, "file", "1.0");
    }

    @Override
    public String toString() {
        return String.format("%s[dir: %s, name: %s]", getClass().getSimpleName(),
                dataRoot == null ? "<unset>" : dataRoot.getAbsolutePath(), databaseName);
    }
}
