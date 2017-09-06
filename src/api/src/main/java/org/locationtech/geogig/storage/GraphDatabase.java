/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.io.Closeable;
import java.util.Iterator;

import org.locationtech.geogig.model.ObjectId;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

/**
 * Provides an interface for implementations of a graph database, which keeps track of the
 * repository commit graph and performs various algorithms on it.
 * 
 * @since 1.0
 */
@Beta
public interface GraphDatabase extends Closeable {

    /**
     * Flag key that indicates if a node is sparse, or contains only partial data from the node it
     * originated from.
     */
    public static final String SPARSE_FLAG = "sparse";

    /**
     * Enumeration describing a relationship direction between two {@code GraphNode}s.
     */
    public enum Direction {
        OUT, IN, BOTH
    }

    /**
     * Represents a single connection between two {@code GraphNode}s.
     */
    public class GraphEdge {
        GraphNode from;

        GraphNode to;

        /**
         * Constructs a new {@code GraphEdge} between two {@code GraphNode}s
         * 
         * @param from the first node
         * @param to the second node
         */
        public GraphEdge(GraphNode from, GraphNode to) {
            this.from = from;
            this.to = to;
        }

        /**
         * @return the first node
         */
        public GraphNode getFromNode() {
            return from;
        }

        /**
         * @return the second node
         */
        public GraphNode getToNode() {
            return to;
        }

        /**
         * @return a readable form of this edge
         */
        @Override
        public String toString() {
            return "" + from + ":" + to;
        }
    }

    /**
     * Represents a single commit in the repository commit graph.
     */
    public abstract class GraphNode {

        /**
         * @return the {@link ObjectId} associated with this node
         */
        public abstract ObjectId getIdentifier();

        /**
         * Gets all of the edges that match the provided {@code Direction}
         * 
         * @param direction the direction
         * @return the list of edges
         */
        public abstract Iterator<GraphEdge> getEdges(final Direction direction);

        /**
         * @return {@code true} if this node represents a sparse commit
         */
        public abstract boolean isSparse();

        /**
         * Determine if this {@code GraphNode} is the same as another one.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }

            GraphNode otherNode = (GraphNode) obj;

            return otherNode.getIdentifier().equals(this.getIdentifier());
        }

        /**
         * Generate a hash code for this node.
         */
        @Override
        public int hashCode() {
            return getIdentifier().hashCode();
        }
    };

    /**
     * Initializes/opens the database. It's safe to call this method multiple times, and only the
     * first call shall take effect.
     */
    public void open();

    /**
     * @return true if the database is open, false otherwise
     */
    public boolean isOpen();

    /**
     * Closes the database.
     */
    public void close();

    /**
     * Determines if the given commit exists in the graph database.
     * 
     * @param commitId the commit id to search for
     * @return true if the commit exists, false otherwise
     */
    public boolean exists(final ObjectId commitId);

    /**
     * Retrieves all of the parents for the given commit.
     * 
     * @param commitid the commit whose parents should be returned
     * @return a list of the parents of the provided commit
     * @throws IllegalArgumentException
     */
    public ImmutableList<ObjectId> getParents(ObjectId commitId) throws IllegalArgumentException;

    /**
     * Retrieves all of the children for the given commit.
     * 
     * @param commitid the commit whose children should be returned
     * @return a list of the children of the provided commit
     * @throws IllegalArgumentException
     */
    public ImmutableList<ObjectId> getChildren(ObjectId commitId) throws IllegalArgumentException;

    /**
     * Adds a commit to the database with the given parents. If a commit with the same id already
     * exists, it will not be inserted.
     * 
     * @param commitId the commit id to insert
     * @param parentIds the commit ids of the commit's parents
     * @return true if the commit id was inserted or updated, false if it was already there
     */
    public boolean put(final ObjectId commitId, ImmutableList<ObjectId> parentIds);

    /**
     * Maps a commit to another original commit. This is used in sparse repositories.
     * 
     * @param mapped the id of the mapped commit
     * @param original the commit to map to
     */
    public void map(final ObjectId mapped, final ObjectId original);

    /**
     * Gets the id of the commit that this commit is mapped to.
     * 
     * @param commitId the commit to find the mapping of
     * @return the mapped commit id, or {@link ObjectId#NULL}
     */
    public ObjectId getMapping(final ObjectId commitId);

    /**
     * Gets the number of ancestors of the commit until it reaches one with no parents, for example
     * the root or an orphaned commit.
     * 
     * @param commitId the commit id to start from
     * @return the depth of the commit
     */
    public int getDepth(final ObjectId commitId);

    /**
     * Set a property on the provided commit node.
     * 
     * @param commitId the id of the commit
     */
    public void setProperty(ObjectId commitId, String propertyName, String propertyValue);

    /**
     * Retrieves the {@code GraphNode} that represents the provided identifier.
     * 
     * @param id the identifier
     * @return the {@code GraphNode}
     */
    public GraphNode getNode(ObjectId id);

    /**
     * Drops all data from the graph database. Usually used when rebuilding the graph.
     */
    public void truncate();
}
