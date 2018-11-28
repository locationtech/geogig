/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.function.BooleanSupplier;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.HashObject;
import org.locationtech.geogig.storage.ObjectStore;

/**
 * A builder for {@link RevTree} instances whose {@link Node nodes} are arranged following a
 * specific "clustering strategy" (e.g. to create a tree with the {@link CanonicalNodeNameOrder
 * canonical} structure, or some other node arrangement like an index on a specific attribute, etc).
 *
 * <p>
 * Since {@code RevTree} is an immutable data structure, a {@code RevTreeBuilder} must be used to
 * {@link #put(Node) add} or {@link #remove(String) remove} nodes until the tree can be
 * {@link #build() built}.
 * 
 * <p>
 * A {@code RevTreeBuilder} operates against an {@link ObjectStore}, onto which it'll save both the
 * resulting {@code RevTree} and any internal {@code RevTree} the built tree is to be split into. So
 * when {@link #build()} returns, the resulting {@code RevTree} is guaranteed to be fully stored on
 * the provided {@code ObjectStore}.
 * 
 * @since 1.0
 */
public interface RevTreeBuilder {

    /**
     * Add a node to the mutable tree representation.
     */
    public boolean put(Node node);

    /**
     * Removes a node to the mutable tree representation.
     */
    public boolean remove(Node node);

    /**
     * Replace {@code oldNode} by {@code newNode} in the mutable tree representation.
     * <p>
     * This method is an opportunity for RevTreeBuilder implementations to optimize for the case
     * when a node is being replaced instead of just added.
     * <p>
     * In any case the result must be the same as calling {@code remove(oldNode)} followed by
     * {@code put(newNode)}, and both {@code oldNode} and {@code newNode} must refer to the same
     * node name.
     * 
     */
    public boolean update(Node oldNode, Node newNode);

    /**
     * Builds a final immutable tree out of the current state of this tree builder.
     * <p>
     * The builder is disposed after this method is called, so calling any of the mutator methods
     * after calling build leads to an unspecified behavior, most probably throwing an unchecked
     * exception.
     * 
     * @return the created tree
     */
    public RevTree build();

    /**
     * Builds a final immutable tree out of the current state of this tree builder.
     * <p>
     * The builder is disposed after this method is called, so calling any of the mutator methods
     * after calling build leads to an unspecified behavior, most probably throwing an unchecked
     * exception.
     * 
     * @since 1.1
     * @return the created tree, or {@code null} if {@link BooleanSupplier#getAsBoolean()
     *         abortFlag.getAsBoolean() == true}
     */
    public @Nullable RevTree build(BooleanSupplier abortFlag);

    public void dispose();

    static RevTree build(final long size, final int childTreeCount, @Nullable List<Node> trees,
            @Nullable List<Node> features, @Nullable SortedMap<Integer, Bucket> buckets) {

        ObjectId id = HashObject.hashTree(trees, features, buckets);
        trees = trees == null ? Collections.emptyList() : trees;
        features = features == null ? Collections.emptyList() : features;
        buckets = buckets == null ? Collections.emptySortedMap() : buckets;

        if (buckets.isEmpty()) {
            return RevObjectFactory.defaultInstance().createTree(id, size, trees, features);
        }
        return RevObjectFactory.defaultInstance().createTree(id, size, childTreeCount, buckets);
    }

    /**
     * Creates a tree with the given id and contents, no questions asked.
     * <p>
     * Be careful when using this method instead of {@link #build()}. {@link #build()} will compute
     * the appropriate id for the tree given its contents as mandated by {@link HashObject}, whilst
     * this method will create the tree as given, even if the id is not the one that would result
     * from properly computing it.
     * 
     * @param id
     * @param size
     * @param childTreeCount
     * @param trees
     * @param features
     * @param buckets
     * @return
     * @deprecated use {@link RevObjectFactory#createTree} (
     *             {@link RevObjectFactory#defaultInstance()})
     */
    static RevTree create(final ObjectId id, final long size, final int childTreeCount,
            @Nullable List<Node> trees, @Nullable List<Node> features,
            @Nullable SortedMap<Integer, Bucket> buckets) {

        trees = trees == null ? Collections.emptyList() : trees;
        features = features == null ? Collections.emptyList() : features;
        buckets = buckets == null ? Collections.emptySortedMap() : buckets;

        if (buckets.isEmpty()) {
            return RevObjectFactory.defaultInstance().createTree(id, size, trees, features);
        }
        return RevObjectFactory.defaultInstance().createTree(id, size, childTreeCount, buckets);
    }

    public int getDepth();
}