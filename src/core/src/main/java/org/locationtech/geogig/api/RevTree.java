/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api;

import java.util.Iterator;

import org.locationtech.geogig.storage.NodeStorageOrder;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

/**
 * Provides an interface for accessing and managing GeoGig revision trees.
 * 
 * @see Node
 */
public interface RevTree extends RevObject {

    /**
     * Maximum number of buckets a tree is split into when its size exceeds the
     * {@link #NORMALIZED_SIZE_LIMIT}
     */
    public static final int MAX_BUCKETS = 32;

    /**
     * The canonical max size of a tree, hard limit, can't be changed or would affect the hash of
     * trees
     * 
     * @todo evaluate what a good compromise would be re memory usage/speed. So far 512 seems like a
     *       good compromise with an iteration throughput of 300K/s and random lookup of 50K/s on an
     *       Asus Zenbook UX31A. A value of 256 shields significantly lower throughput and a higher
     *       one (like 4096) no significant improvement
     */
    public static final int NORMALIZED_SIZE_LIMIT = 512;

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

    public RevTreeBuilder builder(ObjectDatabase target);

    /**
     * Precondition: {@code !buckets().isPresent()}
     * 
     * @return an iterator over the trees and feature children collections, in the prescribed node
     *         storage {@link NodeStorageOrder order}
     */
    public Iterator<Node> children();
}
