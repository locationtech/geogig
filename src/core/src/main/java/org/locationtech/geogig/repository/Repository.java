/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.io.Closeable;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;
import org.locationtech.geogig.api.plumbing.ResolveTreeish;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.porcelain.ConfigOp;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.di.PluginDefaults;
import org.locationtech.geogig.di.Singleton;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.DeduplicationService;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectInserter;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.StagingDatabase;
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
@Singleton
public class Repository implements Context {
    private static Logger LOGGER = LoggerFactory.getLogger(Repository.class);

    public static interface RepositoryListener {
        public void opened(Repository repo);

        public void closed();
    }

    private List<RepositoryListener> listeners = Lists.newCopyOnWriteArrayList();

    private Context injector;

    private URL repositoryLocation;

    public static final String DEPTH_CONFIG_KEY = "core.depth";

    private ExecutorService executor;

    @Inject
    public Repository(Context injector, ExecutorService executor) {
        this.injector = injector;
        this.executor = executor;
    }

    public void addListener(RepositoryListener listener) {
        if (!this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    public void configure() throws RepositoryConnectionException {
        injector.refDatabase().configure();
        injector.objectDatabase().configure();
        injector.graphDatabase().configure();
        injector.stagingDatabase().configure();
    }

    public void open() throws RepositoryConnectionException {
        injector.refDatabase().checkConfig();
        injector.objectDatabase().checkConfig();
        injector.graphDatabase().checkConfig();
        injector.stagingDatabase().checkConfig();
        injector.refDatabase().create();
        injector.objectDatabase().open();
        injector.graphDatabase().open();
        injector.stagingDatabase().open();
        Optional<URL> repoUrl = command(ResolveGeogigDir.class).call();
        Preconditions.checkState(repoUrl.isPresent(), "Repository URL can't be located");
        this.repositoryLocation = repoUrl.get();
        for (RepositoryListener l : listeners) {
            l.opened(this);
        }
    }

    /**
     * Closes the repository.
     */
    public synchronized void close() {
        close(injector.refDatabase());
        close(injector.objectDatabase());
        close(injector.graphDatabase());
        close(injector.stagingDatabase());
        for (RepositoryListener l : listeners) {
            l.closed();
        }
        executor.shutdownNow();
    }

    private void close(Closeable db) {
        try {
            db.close();
        } catch (Exception e) {
            LOGGER.error("Error closing database " + db, e);
        }
    }

    public URL getLocation() {
        return repositoryLocation;
    }

    /**
     * Finds and returns an instance of a command of the specified class.
     * 
     * @param commandClass the kind of command to locate and instantiate
     * @return a new instance of the requested command class, with its dependencies resolved
     */
    public <T extends AbstractGeoGigOp<?>> T command(Class<T> commandClass) {
        return injector.command(commandClass);
    }
    
    /**
     * Test if a blob exists in the object database
     * 
     * @param id the ID of the blob in the object database
     * @return true if the blob exists with the parameter ID, false otherwise
     */
    public boolean blobExists(final ObjectId id) {
        return objectDatabase().exists(id);
    }

    /**
     * @param revStr the string to parse
     * @return the parsed {@link Ref}, or {@link Optional#absent()} if it did not parse.
     */
    public Optional<Ref> getRef(final String revStr) {
        Optional<Ref> ref = command(RefParse.class).setName(revStr).call();
        return ref;
    }

    /**
     * @return the {@link Ref} pointed to by HEAD, or {@link Optional#absent()} if it could not be
     *         resolved.
     */
    public Optional<Ref> getHead() {
        return getRef(Ref.HEAD);
    }

    /**
     * Determines if a commit with the given {@link ObjectId} exists in the object database.
     * 
     * @param id the id to look for
     * @return true if the object was found, false otherwise
     */
    public boolean commitExists(final ObjectId id) {
        try {
            RevObject revObject = objectDatabase().get(id);
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
    public RevCommit getCommit(final ObjectId commitId) {
        RevCommit commit = objectDatabase().getCommit(commitId);

        return commit;
    }

    /**
     * Test if a tree exists in the object database
     * 
     * @param id the ID of the tree in the object database
     * @return true if the tree exists with the parameter ID, false otherwise
     */
    public boolean treeExists(final ObjectId id) {
        try {
            objectDatabase().getTree(id);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return true;
    }

    /**
     * @return the {@link ObjectId} of the root tree
     */
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
     * @return an {@link ObjectInserter} to insert objects into the object database
     */
    public ObjectInserter newObjectInserter() {
        return objectDatabase().newObjectInserter();
    }

    /**
     * @param contentId the {@link ObjectId} of the feature to get
     * @return the {@link RevFeature} that was found in the object database
     */
    public RevFeature getFeature(final ObjectId contentId) {

        RevFeature revFeature = objectDatabase().getFeature(contentId);

        return revFeature;
    }

    /**
     * @return the existing {@link RevTree} pointed to by HEAD, or a new {@code RevTree} if it did
     *         not exist
     */
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
    public RevTree getTree(ObjectId treeId) {
        return command(RevObjectParse.class).setObjectId(treeId).call(RevTree.class).get();
    }

    /**
     * @param path the path to search for
     * @return an {@link Optional} of the {@link Node} for the child, or {@link Optional#absent()}
     *         if it wasn't found
     */
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
    public boolean isSparse() {
        Optional<Map<String, String>> sparseResult = command(ConfigOp.class)
                .setAction(ConfigAction.CONFIG_GET).setName("sparse.filter").call();
        return sparseResult.isPresent();
    }

    @Override
    public WorkingTree workingTree() {
        return injector.workingTree();
    }

    @Override
    public StagingArea index() {
        return injector.index();
    }

    @Override
    public RefDatabase refDatabase() {
        return injector.refDatabase();
    }

    @Override
    public Platform platform() {
        return injector.platform();
    }

    @Override
    public ObjectDatabase objectDatabase() {
        return injector.objectDatabase();
    }

    @Override
    public StagingDatabase stagingDatabase() {
        return injector.stagingDatabase();
    }

    @Override
    public ConfigDatabase configDatabase() {
        return injector.configDatabase();
    }

    @Override
    public GraphDatabase graphDatabase() {
        return injector.graphDatabase();
    }

    @Deprecated
    @Override
    public Repository repository() {
        return this;
    }

    @Override
    public DeduplicationService deduplicationService() {
        return injector.deduplicationService();
    }

    @Override
    public PluginDefaults pluginDefaults() {
        return injector.pluginDefaults();
    }

}
