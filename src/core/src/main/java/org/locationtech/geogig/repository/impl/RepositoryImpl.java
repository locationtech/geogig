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

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.StagingArea;
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

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

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

    @Override
    public void addListener(RepositoryListener listener) {
        if (!this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        context.refDatabase().configure();
        context.objectDatabase().configure();
        context.indexDatabase().configure();
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void open() throws RepositoryConnectionException {

        Optional<URI> repoUrl = command(ResolveGeogigURI.class).call();
        Preconditions.checkState(repoUrl.isPresent(), "Repository URL can't be located");
        this.repositoryLocation = repoUrl.get();

        if (!context.refDatabase().checkConfig()) {
            context.refDatabase().configure();
        }
        if (!context.objectDatabase().checkConfig()) {
            context.objectDatabase().configure();
        }
        if (!context.indexDatabase().checkConfig()) {
            context.indexDatabase().configure();
        }
        context.refDatabase().create();
        context.objectDatabase().open();
        context.indexDatabase().open();
        context.conflictsDatabase().open();
        for (RepositoryListener l : listeners) {
            l.opened(this);
        }
        open = true;
    }

    /**
     * Closes the repository.
     */
    @Override
    public synchronized void close() {
        open = false;
        close(context.refDatabase());
        close(context.objectDatabase());
        close(context.indexDatabase());
        close(context.configDatabase());
        close(context.conflictsDatabase());
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

    @Override
    public URI getLocation() {
        return repositoryLocation;
    }

    /**
     * Finds and returns an instance of a command of the specified class.
     * 
     * @param commandClass the kind of command to locate and instantiate
     * @return a new instance of the requested command class, with its dependencies resolved
     */
    @Override
    public <T extends AbstractGeoGigOp<?>> T command(Class<T> commandClass) {
        return context.command(commandClass);
    }

    /**
     * Test if a blob exists in the object database
     * 
     * @param id the ID of the blob in the object database
     * @return true if the blob exists with the parameter ID, false otherwise
     */
    @Override
    public boolean blobExists(final ObjectId id) {
        return context().objectDatabase().exists(id);
    }

    /**
     * @param revStr the string to parse
     * @return the parsed {@link Ref}, or {@link Optional#absent()} if it did not parse.
     */
    @Override
    public Optional<Ref> getRef(final String revStr) {
        Optional<Ref> ref = command(RefParse.class).setName(revStr).call();
        return ref;
    }

    /**
     * @return the {@link Ref} pointed to by HEAD, or {@link Optional#absent()} if it could not be
     *         resolved.
     */
    @Override
    public Optional<Ref> getHead() {
        return getRef(Ref.HEAD);
    }

    /**
     * Determines if a commit with the given {@link ObjectId} exists in the object database.
     * 
     * @param id the id to look for
     * @return true if the object was found, false otherwise
     */
    @Override
    public boolean commitExists(final ObjectId id) {
        try {
            RevObject revObject = context().objectDatabase().get(id);
            return revObject instanceof RevCommit;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Gets the {@link RevCommit} with the given {@link ObjectId} from the object database.
     * 
     * @param commitId the {@code ObjectId} for the commit
     * @return the {@code RevCommit}
     */
    @Override
    public RevCommit getCommit(final ObjectId commitId) {
        RevCommit commit = context().objectDatabase().getCommit(commitId);

        return commit;
    }

    /**
     * Test if a tree exists in the object database
     * 
     * @param id the ID of the tree in the object database
     * @return true if the tree exists with the parameter ID, false otherwise
     */
    @Override
    public boolean treeExists(final ObjectId id) {
        try {
            context().objectDatabase().getTree(id);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return true;
    }

    /**
     * @return the {@link ObjectId} of the root tree
     */
    @Override
    public ObjectId getRootTreeId() {
        // find the root tree
        ObjectId commitId = command(RevParse.class).setRefSpec(Ref.HEAD).call().get();
        if (commitId.isNull()) {
            return commitId;
        }
        RevCommit commit = command(RevObjectParse.class).setRefSpec(commitId.toString())
                .call(RevCommit.class).get();
        ObjectId treeId = commit.getTreeId();
        return treeId;
    }

    /**
     * @param contentId the {@link ObjectId} of the feature to get
     * @return the {@link RevFeature} that was found in the object database
     */
    @Override
    public RevFeature getFeature(final ObjectId contentId) {

        RevFeature revFeature = context().objectDatabase().getFeature(contentId);

        return revFeature;
    }

    /**
     * @return the existing {@link RevTree} pointed to by HEAD, or a new {@code RevTree} if it did
     *         not exist
     */
    @Override
    public RevTree getOrCreateHeadTree() {
        Optional<ObjectId> headTreeId = command(ResolveTreeish.class).setTreeish(Ref.HEAD).call();
        if (!headTreeId.isPresent()) {
            return RevTree.EMPTY;
        }
        return getTree(headTreeId.get());
    }

    /**
     * @param treeId the tree to retrieve
     * @return the {@link RevTree} referred to by the given {@link ObjectId}
     */
    @Override
    public RevTree getTree(ObjectId treeId) {
        return command(RevObjectParse.class).setObjectId(treeId).call(RevTree.class).get();
    }

    /**
     * @param path the path to search for
     * @return an {@link Optional} of the {@link Node} for the child, or {@link Optional#absent()}
     *         if it wasn't found
     */
    @Override
    public Optional<Node> getRootTreeChild(String path) {
        Optional<NodeRef> nodeRef = command(FindTreeChild.class).setChildPath(path).call();
        if (nodeRef.isPresent()) {
            return Optional.of(nodeRef.get().getNode());
        } else {
            return Optional.absent();
        }
    }

    /**
     * Search the given tree for the child path.
     * 
     * @param tree the tree to search
     * @param childPath the path to search for
     * @return an {@link Optional} of the {@link Node} for the child path, or
     *         {@link Optional#absent()} if it wasn't found
     */
    @Override
    public Optional<Node> getTreeChild(RevTree tree, String childPath) {
        Optional<NodeRef> nodeRef = command(FindTreeChild.class).setParent(tree)
                .setChildPath(childPath).call();
        if (nodeRef.isPresent()) {
            return Optional.of(nodeRef.get().getNode());
        } else {
            return Optional.absent();
        }
    }

    /**
     * Gets the depth of the repository, or {@link Optional#absent} if this is not a shallow clone.
     * 
     * @return the depth
     */
    @Override
    public Optional<Integer> getDepth() {
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
            return Optional.absent();
        }
        return Optional.of(repoDepth);
    }

    /**
     * @return true if this is a sparse (mapped) clone.
     */
    @Override
    public boolean isSparse() {
        return blobStore().getBlob(Blobs.SPARSE_FILTER_BLOB_KEY).isPresent();
    }

    @Override
    public Context context() {
        return context;
    }

    // @Override
    @Override
    public WorkingTree workingTree() {
        return context.workingTree();
    }

    // @Override
    @Override
    public StagingArea index() {
        return context.stagingArea();
    }

    // @Override
    @Override
    public RefDatabase refDatabase() {
        return context.refDatabase();
    }

    // @Override
    @Override
    public Platform platform() {
        return context.platform();
    }

    // @Override
    @Override
    public ObjectDatabase objectDatabase() {
        return context.objectDatabase();
    }

    @Override
    public IndexDatabase indexDatabase() {
        return context.indexDatabase();
    }

    // @Override
    @Override
    public ConflictsDatabase conflictsDatabase() {
        return context.conflictsDatabase();
    }

    // @Override
    @Override
    public ConfigDatabase configDatabase() {
        return context.configDatabase();
    }

    // @Override
    @Override
    public GraphDatabase graphDatabase() {
        return context.graphDatabase();
    }

    @Override
    public BlobStore blobStore() {
        return context().blobStore();
    }

    /**
     * Returns the {@link RepositoryFilter} defined for {@code repo} as of its
     * {@link Blobs#SPARSE_FILTER_BLOB_KEY sparse_filter} blobstore's blob
     */
    public static Optional<RepositoryFilter> getFilter(Repository repo)
            throws IllegalStateException {

        BlobStore blobStore = repo.blobStore();
        Optional<byte[]> filterBlob = blobStore.getBlob(SPARSE_FILTER_BLOB_KEY);
        IniRepositoryFilter filter = null;
        if (filterBlob.isPresent()) {
            filter = new IniRepositoryFilter(blobStore, SPARSE_FILTER_BLOB_KEY);
        }
        return Optional.fromNullable(filter);
    }

}
