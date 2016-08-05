/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.experimental.CanonicalTreeBuilder;
import org.locationtech.geogig.plumbing.HashObject;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

public interface RevTreeBuilder {

    ObjectId EMPTY_TREE_ID = HashObject.hashTree((ImmutableList<Node>) null,
            (ImmutableList<Node>) null, (ImmutableSortedMap<Integer, Bucket>) null);

    RevTree EMPTY = empty();

    public RevTreeBuilder put(Node node);

    public RevTreeBuilder remove(String featureId);

    public RevTree build();

    static RevTreeBuilder canonical(final ObjectStore store) {
        return RevTreeBuilder.canonical(store, EMPTY);
    }

    ////
    // this is a temporary meassure to defaulting to use the legacy tree builder but allowing to
    // specify using the new through a System property. Run CLI with -Dtreebuilder.new=true (e.g. in
    // JAVA_OPTS).
    ////
    static final AtomicBoolean notified = new AtomicBoolean();

    ////
    static RevTreeBuilder canonical(final ObjectStore store, final RevTree original) {
        checkNotNull(store);
        checkNotNull(original);
        RevTreeBuilder builder;
        final boolean USE_NEW_BUILDER = Boolean.getBoolean("treebuilder.new");
        if (USE_NEW_BUILDER) {
            if (!notified.getAndSet(true)) {
                System.err.println(
                        "Using experimental tree builder " + CanonicalTreeBuilder.class.getName());
            }
            builder = new CanonicalTreeBuilder(store, original);
        } else {
            builder = new LegacyTreeBuilder(store, original);
        }
        return builder;
    }

    /**
     * @return a new instance of a properly "named" empty tree (as in with a proper object id after
     *         applying {@link HashObject})
     */
    static RevTree empty() {
        RevTree theEmptyTree = RevTreeImpl.create(EMPTY_TREE_ID, 0L, 0, null, null, null);
        return theEmptyTree;
    }

    static RevTree build(final long size, final int childTreeCount,
            @Nullable ImmutableList<Node> trees, @Nullable ImmutableList<Node> features,
            @Nullable ImmutableSortedMap<Integer, Bucket> buckets) {

        ObjectId id = HashObject.hashTree(trees, features, buckets);
        return RevTreeImpl.create(id, size, childTreeCount, trees, features, buckets);
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
     */
    static RevTree create(final ObjectId id, final long size, final int childTreeCount,
            @Nullable ImmutableList<Node> trees, @Nullable ImmutableList<Node> features,
            @Nullable SortedMap<Integer, Bucket> buckets) {

        return RevTreeImpl.create(id, size, childTreeCount, trees, features, buckets);

    }
}