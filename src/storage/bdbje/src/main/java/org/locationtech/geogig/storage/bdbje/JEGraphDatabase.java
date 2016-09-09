/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.storage.bdbje;

import static com.sleepycat.je.OperationStatus.NOTFOUND;
import static com.sleepycat.je.OperationStatus.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.SynchronizedGraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.je.CacheMode;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentLockedException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

/**
 * Implementation of {@link GraphDatabase} backed by a BerkeleyDB Java Edition database.
 * <p>
 * Implementation note: Since this is the only kind of mutable state we maintain, this
 * implementation extends {@link SynchronizedGraphDatabase} to avoid concurrent threads stepping
 * over each other's feet and overriding graph relations. An alternate solution would be to
 * serialize writes and have free threaded reads.
 * </p>
 */
abstract class JEGraphDatabase extends SynchronizedGraphDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(JEGraphDatabase.class);

    static final String ENVIRONMENT_NAME = "graph";

    public JEGraphDatabase(final ConfigDatabase config, final EnvironmentBuilder envProvider,
            final TupleBinding<NodeData> binding, final String formatVersion, final Hints hints) {
        super(new Impl(config, envProvider, binding, formatVersion, hints));
    }

    private static class Impl implements GraphDatabase {

        private final TupleBinding<NodeData> BINDING;

        private EnvironmentBuilder envProvider;

        /**
         * Lazily loaded, do not access it directly but through {@link #createEnvironment()}
         */
        protected Environment env;

        protected Database graphDb;

        private final String envName;

        private final ConfigDatabase configDb;

        private final String databaseName = "GraphDatabase";

        private final boolean readOnly;

        private final String formatVersion;

        public Impl(final ConfigDatabase config, final EnvironmentBuilder envProvider,
                final TupleBinding<NodeData> binding, final String formatVersion,
                final Hints hints) {
            this.configDb = config;
            this.envProvider = envProvider;
            this.BINDING = binding;
            this.formatVersion = formatVersion;
            this.envName = JEGraphDatabase.ENVIRONMENT_NAME;
            this.readOnly = hints.getBoolean(Hints.OBJECTS_READ_ONLY);
        }

        @Override
        public void open() {
            if (isOpen()) {
                LOGGER.trace("Environment {} already open", env.getHome());
                return;
            }
            this.graphDb = createDatabase();
            LOGGER.debug("Graph database opened at {}. Transactional: {}", env.getHome(),
                    graphDb.getConfig().getTransactional());
        }

        protected Database createDatabase() {

            Environment environment;
            try {
                environment = createEnvironment(readOnly);
            } catch (EnvironmentLockedException e) {
                throw new IllegalStateException(
                        "The repository is already open by another process for writing", e);
            }

            if (!environment.getDatabaseNames().contains(databaseName)) {
                if (readOnly) {
                    environment.close();
                    try {
                        environment = createEnvironment(false);
                    } catch (EnvironmentLockedException e) {
                        throw new IllegalStateException(String.format(
                                "Environment open readonly but database %s does not exist.",
                                databaseName));
                    }
                }
                DatabaseConfig dbConfig = new DatabaseConfig();
                dbConfig.setAllowCreate(true);
                Database openDatabase = environment.openDatabase(null, databaseName, dbConfig);
                openDatabase.close();
                environment.flushLog(true);
                environment.close();
                environment = createEnvironment(readOnly);
            }

            Database database;
            try {
                LOGGER.debug("Opening GraphDatabase at {}", environment.getHome());

                DatabaseConfig dbConfig = new DatabaseConfig();
                dbConfig.setCacheMode(CacheMode.MAKE_COLD);
                dbConfig.setKeyPrefixing(false);// can result in a slightly smaller db size

                dbConfig.setReadOnly(readOnly);
                boolean transactional = environment.getConfig().getTransactional();
                dbConfig.setTransactional(transactional);
                dbConfig.setDeferredWrite(!transactional);

                database = environment.openDatabase(null, databaseName, dbConfig);
            } catch (RuntimeException e) {
                if (environment != null) {
                    environment.close();
                }
                throw e;
            }
            this.env = environment;
            return database;

        }

        /**
         * @return creates and returns the environment
         */
        private synchronized Environment createEnvironment(boolean readOnly)
                throws com.sleepycat.je.EnvironmentLockedException {
            Environment env = envProvider.setRelativePath(this.envName).setReadOnly(readOnly).get();

            return env;
        }

        @Override
        public void configure() throws RepositoryConnectionException {
            StorageType.GRAPH.configure(configDb, "bdbje",
                    formatVersion);
        }

        @Override
        public void checkConfig() throws RepositoryConnectionException {
            StorageType.GRAPH.verify(configDb, "bdbje",
                    formatVersion);
        }

        @Override
        public boolean isOpen() {
            return graphDb != null;
        }

        @Override
        public void close() {
            if (env == null) {
                LOGGER.trace("Database already closed.");
                return;
            }
            final File envHome = env.getHome();
            try {
                LOGGER.debug("Closing graph database at {}", envHome);
                if (graphDb != null) {
                    graphDb.close();
                    graphDb = null;
                }
                LOGGER.trace("GraphDatabase closed. Closing environment...");
                if (!readOnly) {
                    env.sync();
                    env.cleanLog();
                }
            } finally {
                env.close();
                env = null;
            }
            LOGGER.debug("Database {} closed.", envHome);
        }

        @Override
        protected void finalize() {
            if (isOpen()) {
                LOGGER.warn("JEGraphDatabase {} was not closed. Forcing close at finalize()",
                        env.getHome());
                close();
            }
        }

        protected NodeData getNodeInternal(final ObjectId id, final boolean failIfNotFound) {
            Preconditions.checkNotNull(id, "id");
            DatabaseEntry key = new DatabaseEntry(id.getRawValue());
            DatabaseEntry data = new DatabaseEntry();

            final LockMode lockMode = LockMode.READ_UNCOMMITTED;
            Transaction transaction = null;
            OperationStatus operationStatus = graphDb.get(transaction, key, data, lockMode);
            if (NOTFOUND.equals(operationStatus)) {
                if (failIfNotFound) {
                    throw new IllegalArgumentException("Graph Object does not exist: "
                            + id.toString() + " at " + env.getHome().getAbsolutePath());
                }
                return null;
            }
            NodeData node = BINDING.entryToObject(data);
            return node;
        }

        private boolean putNodeInternal(final Transaction transaction, final ObjectId id,
                final NodeData node) throws IOException {

            DatabaseEntry key = new DatabaseEntry(id.getRawValue());
            DatabaseEntry data = new DatabaseEntry();
            BINDING.objectToEntry(node, data);

            final OperationStatus status = graphDb.put(transaction, key, data);

            return SUCCESS.equals(status);
        }

        private void abort(@Nullable Transaction transaction) {
            if (transaction != null) {
                try {
                    transaction.abort();
                } catch (Exception e) {
                    LOGGER.error("Error aborting transaction", e);
                }
            }
        }

        private void commit(@Nullable Transaction transaction) {
            if (transaction != null) {
                try {
                    transaction.commit();
                } catch (Exception e) {
                    LOGGER.error("Error committing transaction", e);
                }
            }
        }

        @Nullable
        private Transaction newTransaction() {
            final boolean transactional = graphDb.getConfig().getTransactional();
            if (transactional) {
                TransactionConfig txConfig = new TransactionConfig();
                txConfig.setReadUncommitted(true);
                Optional<String> durability = configDb.get("bdbje.object_durability");
                if ("safe".equals(durability.orNull())) {
                    txConfig.setDurability(Durability.COMMIT_SYNC);
                } else {
                    txConfig.setDurability(Durability.COMMIT_WRITE_NO_SYNC);
                }
                Transaction transaction = env.beginTransaction(null, txConfig);
                return transaction;
            }
            return null;
        }

        @Override
        public boolean exists(ObjectId commitId) {
            Preconditions.checkNotNull(commitId, "id");

            DatabaseEntry key = new DatabaseEntry(commitId.getRawValue());
            DatabaseEntry data = new DatabaseEntry();
            // tell db not to retrieve data
            data.setPartial(0, 0, true);

            final LockMode lockMode = LockMode.READ_UNCOMMITTED;
            Transaction transaction = null;
            OperationStatus status = graphDb.get(transaction, key, data, lockMode);
            return SUCCESS == status;
        }

        @Override
        public ImmutableList<ObjectId> getParents(ObjectId commitId)
                throws IllegalArgumentException {
            Builder<ObjectId> listBuilder = new ImmutableList.Builder<ObjectId>();
            NodeData node = getNodeInternal(commitId, false);
            if (node != null) {
                return listBuilder.addAll(node.outgoing).build();
            }
            return listBuilder.build();
        }

        @Override
        public ImmutableList<ObjectId> getChildren(ObjectId commitId)
                throws IllegalArgumentException {
            Builder<ObjectId> listBuilder = new ImmutableList.Builder<ObjectId>();
            NodeData node = getNodeInternal(commitId, false);
            if (node != null) {
                return listBuilder.addAll(node.incoming).build();
            }
            return listBuilder.build();
        }

        @Override
        public boolean put(ObjectId commitId, ImmutableList<ObjectId> parentIds) {
            NodeData node = getNodeInternal(commitId, false);
            boolean updated = false;
            final Transaction transaction = newTransaction();
            try {
                if (node == null) {
                    node = new NodeData(commitId, parentIds);
                    updated = true;
                }
                for (ObjectId parent : parentIds) {
                    if (!node.outgoing.contains(parent)) {
                        node.outgoing.add(parent);
                        updated = true;
                    }
                    NodeData parentNode = getNodeInternal(parent, false);
                    if (parentNode == null) {
                        parentNode = new NodeData(parent);
                        updated = true;
                    }
                    if (!parentNode.incoming.contains(commitId)) {
                        parentNode.incoming.add(commitId);
                        updated = true;
                    }
                    putNodeInternal(transaction, parent, parentNode);
                }
                putNodeInternal(transaction, commitId, node);
                commit(transaction);
            } catch (Exception e) {
                abort(transaction);
                throw Throwables.propagate(e);
            }
            return updated;
        }

        @Override
        public void map(ObjectId mapped, ObjectId original) {
            NodeData node = getNodeInternal(mapped, false);
            if (node == null) {
                // didn't exist
                node = new NodeData(mapped);
            }
            node.mappedTo = original;
            final Transaction transaction = newTransaction();
            try {
                putNodeInternal(transaction, mapped, node);
                commit(transaction);
            } catch (Exception e) {
                abort(transaction);
                throw Throwables.propagate(e);
            }
        }

        @Override
        public ObjectId getMapping(ObjectId commitId) {
            NodeData node = getNodeInternal(commitId, true);
            return node.mappedTo;
        }

        @Override
        public int getDepth(ObjectId commitId) {
            int depth = 0;

            Queue<ObjectId> q = Lists.newLinkedList();
            NodeData node = getNodeInternal(commitId, true);
            Iterables.addAll(q, node.outgoing);

            List<ObjectId> next = Lists.newArrayList();
            while (!q.isEmpty()) {
                depth++;
                while (!q.isEmpty()) {
                    ObjectId n = q.poll();
                    NodeData parentNode = getNodeInternal(n, true);
                    List<ObjectId> parents = Lists.newArrayList(parentNode.outgoing);
                    if (parents.size() == 0) {
                        return depth;
                    }

                    Iterables.addAll(next, parents);
                }

                q.addAll(next);
                next.clear();
            }

            return depth;
        }

        @Override
        public void setProperty(ObjectId commitId, String propertyName, String propertyValue) {
            NodeData node = getNodeInternal(commitId, true);
            node.properties.put(propertyName, propertyValue);
            final Transaction transaction = newTransaction();
            try {
                putNodeInternal(transaction, commitId, node);
                commit(transaction);
            } catch (Exception e) {
                abort(transaction);
                throw Throwables.propagate(e);
            }
        }

        private class JEGraphNode extends GraphNode {
            NodeData node;

            List<GraphEdge> edges;

            public JEGraphNode(NodeData node) {
                this.node = node;
                this.edges = null;
            }

            @Override
            public ObjectId getIdentifier() {
                return node.id;
            }

            @Override
            public Iterator<GraphEdge> getEdges(final Direction direction) {
                if (edges == null) {
                    edges = new LinkedList<GraphEdge>();
                    Iterator<ObjectId> nodeEdges = node.incoming.iterator();
                    while (nodeEdges.hasNext()) {
                        ObjectId otherNode = nodeEdges.next();
                        edges.add(new GraphEdge(new JEGraphNode(getNodeInternal(otherNode, true)),
                                this));
                    }

                    nodeEdges = node.outgoing.iterator();
                    while (nodeEdges.hasNext()) {
                        ObjectId otherNode = nodeEdges.next();
                        edges.add(new GraphEdge(this,
                                new JEGraphNode(getNodeInternal(otherNode, true))));
                    }
                }

                final GraphNode myNode = this;

                return Iterators.filter(edges.iterator(), new Predicate<GraphEdge>() {
                    @Override
                    public boolean apply(GraphEdge input) {
                        switch (direction) {
                        case OUT:
                            return input.getFromNode() == myNode;
                        case IN:
                            return input.getToNode() == myNode;
                        default:
                            break;
                        }
                        return true;
                    }
                });
            }

            @Override
            public boolean isSparse() {
                return node.isSparse();
            }

        }

        @Override
        public GraphNode getNode(ObjectId id) {
            return new JEGraphNode(getNodeInternal(id, true));
        }

        @Override
        public void truncate() {
            try {
                final Environment env = this.env;
                graphDb.close();
                env.truncateDatabase(null, databaseName, false);
                this.env = null;
                this.graphDb = null;
                open();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }
}
