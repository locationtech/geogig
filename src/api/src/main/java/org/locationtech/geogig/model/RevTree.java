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
import java.util.Comparator;
import java.util.Optional;
import java.util.SortedSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;

/**
 * A GeoGig revision tree is an immutable data structure that represents a
 * <a href="https://en.wikipedia.org/wiki/Merkle_tree">Merkle Tree</a> (or Hash Tree), holding
 * {@link TYPE#FEATURE feature} or {@link TYPE#TREE tree} {@link Node node} pointers to children
 * {@link RevTree} or {@link RevFeature} objects in an {@link ObjectStore}, and {@link Bucket}
 * pointers to point to subtrees it has been split into when a certain threshold on the allowed max
 * capacity of it's feature or tree lists has been surpased.
 * <p>
 * The {@link #getId() id} of a {@code RevTree} is computed out of its directly contained
 * {@link RevTree#trees() tree}, {@link RevTree#features() feature}, and {@link RevTree#getBuckets()
 * bucket} pointers as mandated by {@link HashObjectFunnels}.
 * <p>
 * Two trees that contain the same values hash out to the same {@link ObjectId}, as long as they
 * have been built using the same "clustering strategy". That is, the same limits to split a tree
 * into subtrees (buckets) and the same ordering of it's nodes have been applied. The two trees,
 * hence, will only differ in what have actually changed, as only the changed buckets, or the tree
 * itself if it holds nodes, will hash out to a different value, conforming a DAG (Directed Acyclic
 * Graph).
 * <p>
 * This data structure does not impose a split threshold nor a default ordering for nodes, allowing
 * the builder to create trees based on different criteria, depending on whether it'll be used to
 * represent a tree's canonical form, or an index of some form.
 * <p>
 * The <b>Canonical </b> structure of a {@code RevTree} is mandated by the
 * {@link CanonicalNodeNameOrder} {@link Comparator}, which also defines the per depth level
 * {@link CanonicalNodeNameOrder#normalizedSizeLimit(int) split threshold}.
 * <p>
 * The Canonical representation of a tree is the one that is used to store {@link RevTree} instances
 * in the repository, and is optimized for fast computation of differences between any two given
 * trees, as such is perhaps the most important operation in geogig.
 * <p>
 * Every geogig {@link RevCommit commit} points to a root tree, which in turn points to feature
 * trees, which in turn point to features and/or other, nested feature trees.
 * <p>
 * A "feature tree" node will have a {@link Node#getMetadataId() metadata id} that's a pointer to
 * the layer's default {@link RevFeatureType Feature Type}. Feature nodes that are children of such
 * trees that have a {@link ObjectId#NULL} metadata id are deemed to pertain the their parent tree's
 * feature type. The feature nodes that have a non {@link ObjectId#NULL} metadata id are deemed
 * pointers to {@link RevFeature features} that comply to a different feature type than the tree's
 * default.
 * <p>
 * That is, the object model allows for nested feature trees (a.k.a layers, feature classes), and
 * for a single feature tree to contain features of mixed feature types.
 * 
 * @see Node
 * @see Bucket
 * @see CanonicalNodeOrder
 * @see CanonicalNodeNameOrder
 * 
 * @since 1.0
 */
public interface RevTree extends RevObject {

    /**
     * The empty tree object id, as results from calling {@link HashObjectFunnels#hashTree
     * HashObjectFunnels.hashTree} with empty arguments.
     */
    ObjectId EMPTY_TREE_ID = HashObjectFunnels.hashTree(null, null, (Iterable<Bucket>) null);

    /**
     * A "null object" to represent the empty tree
     */
    RevTree EMPTY = new RevTree() {

        @Override
        public ObjectId getId() {
            return EMPTY_TREE_ID;
        }

        @Override
        public ImmutableList<Node> trees() {
            return ImmutableList.of();
        }

        @Override
        public long size() {
            return 0L;
        }

        @Override
        public int numTrees() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public ImmutableList<Node> features() {
            return ImmutableList.of();
        }

        @Override
        public ImmutableSortedMap<Integer, Bucket> buckets() {
            return ImmutableSortedMap.of();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RevObject)) {
                return false;
            }
            return getId().equals(((RevObject) o).getId());
        }

        @Override
        public String toString() {
            return "EMPTY TREE[" + getId() + "]";
        }

        @Override
        public SortedSet<Bucket> getBuckets() {
            return Collections.emptySortedSet();
        }
    };

    /**
     * @return {@link TYPE#TREE}
     */
    @Override
    public default TYPE getType() {
        return TYPE.TREE;
    }

    /**
     * @return total number of features, including the size of nested trees
     */
    public long size();

    /**
     * <p>
     * A {@code RevTree} with an empty {@link #trees() tree list} may still have direct children
     * trees if it has been split into bucket trees, hence {@code numTrees()} may be greater than
     * zero while the trees list is empty.
     * 
     * @return number of direct child trees
     */
    public int numTrees();

    /**
     * Convenience method to figure out if a tree is completely empty.
     * <p>
     * A tree is completely empty not only if {@code size() == 0}, but also if it has no child tree
     * nodes at all, since {@code size()} could be zero but the tree might have pointers to other
     * empty trees.
     * 
     * @apiNote an empty tree id MUST be {@link #EMPTY_TREE_ID}
     * 
     * @return {@code true} if the tree is completely empty (i.e. has no feature nor child tree
     *         nodes)
     */
    public default boolean isEmpty() {
        boolean empty = treesSize() == 0 && featuresSize() == 0 && bucketsSize() == 0;
        if (empty) {
            Preconditions.checkState(size() == 0L);
            Preconditions.checkState(EMPTY_TREE_ID.equals(getId()));
        }
        return empty;
    }

    /**
     * The {@link TYPE#TREE tree} nodes held directly by this tree.
     * <p>
     * A {@code RevTree} with an empty tree list may still have direct children trees if it has been
     * split into bucket trees, hence {@link #numTrees()} may be greater than zero while this list
     * is empty.
     * 
     * @apiNote the returned list does not contain {@code null} objects
     */
    public ImmutableList<Node> trees();

    /**
     * @return the number of {@link Node}s in the {@link #trees} property
     */
    public default int treesSize() {
        return trees().size();
    }

    public default Node getTree(int index) {
        return trees().get(index);
    }

    /**
     * Performs the given action for each element of the {@link #trees} collection respecting its
     * iteration order
     */
    public default void forEachTree(Consumer<Node> consumer) {
        trees().forEach(consumer);
    }

    /**
     * The {@link TYPE#FEATURE feature} nodes held directly by this tree.
     * <p>
     * A {@code RevTree} instance may hold as many feature nodes as allowed by it's builder before
     * being split into bucket trees.
     * <p>
     * Also, depending on the "clustering strategy" the tree builder employs, a {@code RevTree}
     * instance may have both direct feature nodes and {@link #buckets}.
     * <p>
     * In the former case, where it has features and no buckets, it's a leaf tree, meaning it can't
     * be split into buckets because it hasn't overcome the builder's imposed split threshold.
     * <p>
     * In the later, when it has both features and buckets, it's called a "mixed" tree.
     * <p>
     * The {@link CanonicalNodeNameOrder canonical} representation of a {@link RevTree} does not
     * allow for "mixed trees" (that is, a tree is wither a leaf tree or a pure bucket tree), but
     * other kinds of trees could be created that allow for mixed states, such as a tree that's
     * built using a quad-tree where nodes whose {@link Node#bounds() bounds} don't fall into any of
     * the quadrant buckets is left in the parent's tree.
     * 
     * @apiNote the returned list does not contain {@code null} objects
     */
    public ImmutableList<Node> features();

    /**
     * @return the number of {@link Node}s in the {@link #features} property
     */
    public default int featuresSize() {
        return features().size();
    }

    public default Node getFeature(int index) {
        return features().get(index);
    }

    /**
     * Performs the given action for each element of the {@link #features} collection respecting its
     * iteration order
     */
    public default void forEachFeature(Consumer<Node> consumer) {
        features().forEach(consumer);
    }

    /**
     * The mapping of (zero-based) bucket index to the bucket (pointer to {@link RevTree} instance)
     * this revtree has been split into.
     * <p>
     * {@code RevTree} doesn't impose into how many subtrees a tree instance is split into. Instead,
     * that limit is to be consistently imposed by the tree builder.
     * <p>
     * For example, the canonical representation of a tree, as mandated by
     * {@link CanonicalNodeNameOrder} defines a split factor based on the bucket's
     * {@link CanonicalNodeNameOrder#maxBucketsForLevel(int) depth), while a tree built to represent
     * a quad-tree would always be split into four subtrees to represent the next set of quadrants.
     * 
     * @apiNote the returned map does not contain {@code null} keys nor values
     * @deprecated
     */
    public ImmutableSortedMap<Integer, Bucket> buckets();

    public Iterable<Bucket> getBuckets();

    /**
     * @return the number of buckets in the {@link #buckets} property
     */
    public default int bucketsSize() {
        return Iterables.size(getBuckets());
    }

    /**
     * Performs the given action for each element of the {@link #buckets} collection respecting its
     * iteration order, which is the order of the bucket index.
     * 
     * @deprecated
     * @param consumer a consumer that accepts a tuple given by the bucket index and the bucket
     *        itself
     */
    public default void forEachBucket(BiConsumer<Integer, Bucket> consumer) {
        getBuckets().forEach(b -> consumer.accept(Integer.valueOf(b.getIndex()), b));
    }

    public default void forEachBucket(Consumer<Bucket> consumer) {
        getBuckets().forEach(consumer);
    }

    public default Optional<Bucket> getBucket(int bucketIndex) {
        for (Bucket b : getBuckets()) {
            if (bucketIndex == b.getIndex()) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }
}
