/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import static org.locationtech.geogig.storage.impl.Blobs.SPARSE_FILTER_BLOB_KEY;

import java.io.Closeable;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.repository.Command;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.impl.Blobs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import lombok.experimental.Accessors;

/**
 * A repository is a collection of commits, each of which is an archive of what the project's
 * working tree looked like at a past date, whether on your machine or someone else's.
 * <p>
 * It also defines HEAD (see below), which identifies the branch or commit the current working tree
 * stemmed from. Lastly, it contains a set of branches and tags, to identify certain commits by
 * name.
 * </p>
 * 
 * @see WorkingTree
 */
@Accessors(fluent = true)
public class RepositoryImpl implements Repository {
    private static Logger LOGGER = LoggerFactory.getLogger(RepositoryImpl.class);

    private List<RepositoryListener> listeners = Lists.newCopyOnWriteArrayList();

    private Context context;

    private URI repositoryLocation;

    private volatile boolean open;

    @Inject
    public RepositoryImpl(Context context) {
        this.context = context;
    }

    public @Override void addListener(RepositoryListener listener) {
        if (!this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    public @Override boolean isOpen() {
        return open;
    }

    public @Override void open() throws RepositoryConnectionException {
        if (isOpen()) {
            return;
        }
        Optional<URI> repoUrl = command(ResolveGeogigURI.class).call();
        Preconditions.checkState(repoUrl.isPresent(), "Repository URL can't be located");
        this.repositoryLocation = repoUrl.get();
        ConfigDatabase configDatabase = context.configDatabase();
        RefDatabase refDatabase = context.refDatabase();
        ObjectDatabase objectDatabase = context.objectDatabase();
        IndexDatabase indexDatabase = context.indexDatabase();
        ConflictsDatabase conflictsDatabase = context.conflictsDatabase();

        configDatabase.open();
        refDatabase.open();
        objectDatabase.open();
        indexDatabase.open();
        conflictsDatabase.open();

        GraphDatabase graphDatabase = objectDatabase.getGraphDatabase();
        graphDatabase.open();

        for (RepositoryListener l : listeners) {
            l.opened(this);
        }
        open = true;
    }

    /**
     * Closes the repository.
     */
    public @Override synchronized void close() {
        if (!isOpen()) {
            return;
        }
        open = false;
        close(context.refDatabase());
        close(context.objectDatabase());
        close(context.indexDatabase());
        close(context.conflictsDatabase());
        close(context.configDatabase());
        for (RepositoryListener l : listeners) {
            try {// don't let a broken listener mess us up
                l.closed();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    protected @Override void finalize() {
        if (open) {
            LOGGER.warn("Repository instance being finalized without having been closed: "
                    + repositoryLocation);
            close();
        }
    }

    private void close(Closeable db) {
        try {
            db.close();
        } catch (Exception e) {
            LOGGER.error("Error closing database " + db, e);
        }
    }

    public @Override URI getLocation() {
        return repositoryLocation;
    }

    /**
     * Finds and returns an instance of a command of the specified class.
     * 
     * @param commandClass the kind of command to locate and instantiate
     * @return a new instance of the requested command class, with its dependencies resolved
     */
    public @Override <T extends Command<?>> T command(Class<T> commandClass) {
        return context.command(commandClass);
    }

    /**
     * Gets the depth of the repository, or {@link Optional#absent} if this is not a shallow clone.
     * 
     * @return the depth
     */
    public @Override Optional<Integer> getDepth() {
        int repoDepth = 0;
        Optional<Map<String, String>> depthResult = command(ConfigOp.class)
                .setAction(ConfigAction.CONFIG_GET).setName(DEPTH_CONFIG_KEY).call();
        if (depthResult.isPresent()) {
            String depthString = depthResult.get().get(DEPTH_CONFIG_KEY);
            if (depthString != null) {
                repoDepth = Integer.parseInt(depthString);
            }
        }

        if (repoDepth == 0) {
            return Optional.empty();
        }
        return Optional.of(repoDepth);
    }

    /**
     * @return true if this is a sparse (mapped) clone.
     */
    public @Override boolean isSparse() {
        return context().blobStore().getBlob(Blobs.SPARSE_FILTER_BLOB_KEY).isPresent();
    }

    public @Override Context context() {
        return context;
    }

    /**
     * Returns the {@link RepositoryFilter} defined for {@code repo} as of its
     * {@link Blobs#SPARSE_FILTER_BLOB_KEY sparse_filter} blobstore's blob
     */
    public static Optional<RepositoryFilter> getFilter(Repository repo)
            throws IllegalStateException {

        BlobStore blobStore = repo.context().blobStore();
        Optional<byte[]> filterBlob = blobStore.getBlob(SPARSE_FILTER_BLOB_KEY);
        IniRepositoryFilter filter = null;
        if (filterBlob.isPresent()) {
            filter = new IniRepositoryFilter(blobStore, SPARSE_FILTER_BLOB_KEY);
        }
        return Optional.ofNullable(filter);
    }

}
