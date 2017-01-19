/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Index;
import org.locationtech.geogig.repository.Index.IndexType;
import org.locationtech.geogig.repository.RepositoryConnectionException;

import com.google.common.base.Optional;

/**
 * The {@code IndexDatabase} keeps track of spatial and attribute indexes of user-specified data
 * sets.
 * 
 * @since 1.1
 */
public interface IndexDatabase extends ObjectStore {

    /**
     * Performs any setup required before first open, including setting default configuration.
     */
    public void configure() throws RepositoryConnectionException;

    /**
     * @return {@code true} if the {@code IndexDatabase} is read-only
     */
    public boolean isReadOnly();

    /**
     * Verify the configuration before opening the database.
     */
    public void checkConfig() throws RepositoryConnectionException;

    /**
     * Create an index on the given feature type tree using the provided attribute name and indexing
     * strategy.
     * 
     * @param treeName the feature type name to index
     * @param attributeName the attribute to index
     * @param strategy the indexing strategy
     * @param metadata extra properties to be used by the index
     * @return the new index
     */
    public Index createIndex(String treeName, String attributeName, IndexType strategy,
            @Nullable Map<String, Object> metadata);

    /**
     * Gets the index for the given tree and attribute if it exists.
     * 
     * @param treeName the name of the tree
     * @param attributeName the indexed attribute
     * @return an {@link Optional} with the index, or {@link Optional#absent()} if there wasn't an
     *         index
     */
    public Optional<Index> getIndex(String treeName, String attributeName);

    /**
     * Associates an indexed tree with a tree from the {@link ObjectDatabase}.
     * 
     * @param index the index that the indexed tree belongs to
     * @param originalTree the {@link ObjectId} of the canonical tree
     * @param indexedTree the {@link ObjectId} of the indexed tree
     */
    public void addIndexedTree(Index index, ObjectId originalTree, ObjectId indexedTree);

    /**
     * Resolves a given tree id to the indexed version of the tree, if one exists.
     * 
     * @param index the index
     * @param treeId the {@link ObjectId} of the canonical tree
     */
    public Optional<ObjectId> resolveIndexedTree(Index index, ObjectId treeId);
}
