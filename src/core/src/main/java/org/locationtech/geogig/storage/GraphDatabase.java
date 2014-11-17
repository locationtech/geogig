/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage;

import java.io.Closeable;
import java.util.Iterator;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.di.Singleton;
import org.locationtech.geogig.repository.RepositoryConnectionException;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

@Beta
@Singleton
public interface GraphDatabase extends Closeable {

    public static final String SPARSE_FLAG = "sparse";

    public enum Direction {
        OUT, IN, BOTH
    }

    public enum Relationship {
        TOROOT, PARENT, MAPPED_TO
    }

    public class GraphEdge {
        GraphNode from;

        GraphNode to;

        public GraphEdge(GraphNode from, GraphNode to) {
            this.from = from;
            this.to = to;
        }

        public GraphNode getFromNode() {
            return from;
        }

        public GraphNode getToNode() {
            return to;
        }

        @Override
        public String toString() {
            return "" + from + ":" + to;
        }
    }

    public abstract class GraphNode {
        public abstract ObjectId getIdentifier();

        public abstract Iterator<GraphEdge> getEdges(final Direction direction);

        public abstract boolean isSparse();

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
     * Perform GeoGig configuration before the first connection to the database.
     */
    public void configure() throws RepositoryConnectionException;

    /**
     * Verify the configuration before opening the database
     */
    public void checkConfig() throws RepositoryConnectionException;

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
     * @return the mapped commit id
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

    public GraphNode getNode(ObjectId id);

    public void truncate();
}
