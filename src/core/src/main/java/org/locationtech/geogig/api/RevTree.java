/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import java.util.Iterator;

import org.locationtech.geogig.storage.NodeStorageOrder;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

/**
 * Provides an interface for accessing and managing GeoGig revision trees.
 * 
 * @see Node
 */
public interface RevTree extends RevObject {

    public static final RevTree EMPTY = RevTreeBuilder.empty();

    public static final ObjectId EMPTY_TREE_ID = EMPTY.getId();

    /**
     * @return total number of features, including size nested trees
     */
    public long size();

    /**
     * @return number of direct child trees
     */
    public int numTrees();

    public boolean isEmpty();

    public Optional<ImmutableList<Node>> trees();

    public Optional<ImmutableList<Node>> features();

    public Optional<ImmutableSortedMap<Integer, Bucket>> buckets();

    /**
     * Precondition: {@code !buckets().isPresent()}
     * 
     * @return an iterator over the trees and feature children collections, in the prescribed node
     *         storage {@link NodeStorageOrder order}
     */
    public Iterator<Node> children();
}
