/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.function.BooleanSupplier;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.model.impl.QuadTreeBuilder;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;

import lombok.NonNull;

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

    public RevTreeBuilder original(RevTree original);

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
            @Nullable List<Node> features, @Nullable SortedSet<Bucket> buckets) {

        trees = trees == null ? Collections.emptyList() : trees;
        features = features == null ? Collections.emptyList() : features;
        buckets = buckets == null ? Collections.emptySortedSet() : buckets;
        ObjectId id = HashObjectFunnels.hashTree(trees, features, buckets);

        if (buckets.isEmpty()) {
            return RevObjectFactory.defaultInstance().createTree(id, size, trees, features);
        }
        return RevObjectFactory.defaultInstance().createTree(id, size, childTreeCount, buckets);
    }

    public static RevTreeBuilder builder(@NonNull ObjectStore store) {
        return CanonicalTreeBuilder.create(store);
    }

    public static RevTreeBuilder builder(@NonNull ObjectStore store, @NonNull RevTree original) {
        return CanonicalTreeBuilder.create(store, original);
    }

    public static RevTreeBuilder quadBuilder(@NonNull ObjectStore source,
            @NonNull ObjectStore target, @NonNull Envelope maxBounds) {
        return QuadTreeBuilder.create(source, target, RevTree.EMPTY, maxBounds);
    }

    public static RevTreeBuilder quadBuilder(@NonNull ObjectStore source,
            @NonNull ObjectStore target, @NonNull RevTree original, @NonNull Envelope maxBounds) {
        return QuadTreeBuilder.create(source, target, original, maxBounds);
    }

}