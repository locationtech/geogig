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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.HashObject;
import org.locationtech.geogig.repository.impl.DepthSearch;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 
 * @deprecated this is the old and inefficient RevTreeBuilder, it's deprecated at 1.0-RC3 and will
 *             be next moved to the test folder to only be used to verify the correctness of the new
 *             (still experimental) builder.
 */
@Deprecated
public @Accessors(fluent = true) class LegacyTreeBuilder implements RevTreeBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(RevTreeBuilder.class);

    public static final RevTree EMPTY = empty();

    public static final ObjectId EMPTY_TREE_ID = EMPTY.getId();

    private static final CanonicalNodeOrder NODE_STORAGE_ORDER = new CanonicalNodeOrder();

    /**
     * How many children nodes to hold before forcing normalization of the internal data structures
     * into tree buckets on the database
     * 
     * @todo make this configurable
     */
    public static final int DEFAULT_NORMALIZATION_THRESHOLD = 1000 * 1000;

    private final ObjectStore obStore;

    private int normalizationThreshold = DEFAULT_NORMALIZATION_THRESHOLD;

    private final Set<String> deletes;

    private final Map<String, Node> treeChanges;

    private final Map<String, Node> featureChanges;

    protected final TreeMap<Integer, Bucket> bucketTreesByBucket;

    private int depth;

    private long initialSize;

    private int initialNumTrees;

    protected CanonicalNodeNameOrder storageOrder = new CanonicalNodeNameOrder();

    private Map<ObjectId, RevTree> pendingWritesCache;

    private @Setter RevTree original;

    /**
     * Empty tree constructor, used to create trees from scratch
     * 
     * @param db
     */
    public LegacyTreeBuilder(ObjectStore db) {
        this(db, null);
    }

    /**
     * Only useful to {@link #build() build} the named {@link #empty() empty} tree
     */
    private LegacyTreeBuilder() {
        obStore = null;
        treeChanges = Maps.newTreeMap();
        featureChanges = Maps.newTreeMap();
        deletes = Sets.newTreeSet();
        bucketTreesByBucket = Maps.newTreeMap();
        pendingWritesCache = Maps.newTreeMap();
        original = RevTree.EMPTY;
    }

    public RevTreeBuilder normalizationThreshold(final int threshold) {
        this.normalizationThreshold = threshold;
        return this;
    }

    /**
     * Copy constructor with tree depth
     * 
     * @param obStore {@link org.locationtech.geogig.storage.ObjectStore ObjectStore} with which to
     *        initialize this RevTreeBuilder.
     * @param copy {@link org.locationtech.geogig.model.RevTree RevTree} to copy.
     */
    public LegacyTreeBuilder(ObjectStore obStore, @Nullable final RevTree copy) {
        this(obStore, copy, 0, new TreeMap<ObjectId, RevTree>(), DEFAULT_NORMALIZATION_THRESHOLD);
    }

    /**
     * Copy constructor
     */
    private LegacyTreeBuilder(final ObjectStore obSotre, @Nullable final RevTree copy,
            final int depth, final Map<ObjectId, RevTree> pendingWritesCache,
            final int normalizationThreshold) {

        checkNotNull(obSotre);
        checkNotNull(pendingWritesCache);

        this.obStore = obSotre;
        this.normalizationThreshold = normalizationThreshold;
        this.depth = depth;
        this.pendingWritesCache = pendingWritesCache;

        this.deletes = Sets.newHashSet();
        this.treeChanges = Maps.newHashMap();
        this.featureChanges = Maps.newHashMap();
        this.bucketTreesByBucket = Maps.newTreeMap();

        this.original = copy == null ? RevTree.EMPTY : copy;

        if (copy != null) {
            this.initialSize = copy.size();
            this.initialNumTrees = copy.numTrees();

            if (!copy.trees().isEmpty()) {
                checkArgument(0 == copy.bucketsSize());
                for (Node node : copy.trees()) {
                    putInternal(node);
                }
            }
            if (!copy.features().isEmpty()) {
                checkArgument(0 == copy.bucketsSize());
                for (Node node : copy.features()) {
                    putInternal(node);
                }
            }
            if (copy.bucketsSize() > 0) {
                checkArgument(copy.features().isEmpty());
                bucketTreesByBucket.putAll(copy.buckets());
            }
        }
    }

    /**
     */
    private void putInternal(Node node) {
        deletes.remove(node.getName());

        switch (node.getType()) {
        case FEATURE:
            featureChanges.put(node.getName(), node);
            break;
        case TREE:
            treeChanges.put(node.getName(), node);
            break;
        default:
            throw new IllegalArgumentException("Only tree or feature nodes can be added to a tree: "
                    + node + " " + node.getType());
        }
    }

    private RevTree loadTree(final ObjectId subtreeId) {
        RevTree subtree = this.pendingWritesCache.get(subtreeId);
        if (subtree == null) {
            subtree = obStore.getTree(subtreeId);
        }
        return subtree;
    }

    private Optional<Node> getInternal(final String key, final boolean deep) {
        Node found = featureChanges.get(key);
        if (found == null) {
            found = treeChanges.get(key);
        }
        if (found != null) {
            return Optional.of(found);
        }

        if (!deep) {
            return Optional.absent();
        }
        if (deletes.contains(key)) {
            return Optional.absent();
        }

        final Integer bucketIndex = computeBucket(key);
        final Bucket bucket = bucketTreesByBucket.get(bucketIndex);
        if (bucket == null) {
            return Optional.absent();
        }

        RevTree subtree = loadTree(bucket.getObjectId());

        DepthSearch depthSearch = new DepthSearch(obStore);
        Optional<Node> node = depthSearch.getDirectChild(subtree, key, depth + 1);

        if (node.isPresent()) {
            return Optional.of(node.get());
        } else {
            return Optional.absent();
        }
    }

    private long sizeOfTree(ObjectId treeId) {
        if (treeId.isNull()) {
            return 0L;
        }
        RevTree tree = loadTree(treeId);
        return tree.size();
    }

    private int numPendingChanges() {
        int totalChanges = featureChanges.size() + treeChanges.size() + deletes.size();
        return totalChanges;
    }

    /**
     * Splits the cached entries into subtrees and saves them, making sure the tree contains either
     * only entries or subtrees
     */
    private RevTree normalize() {
        Stopwatch sw = Stopwatch.createStarted();
        RevTree tree;

        final int numPendingChanges = numPendingChanges();
        if (bucketTreesByBucket.isEmpty()
                && numPendingChanges <= CanonicalNodeNameOrder.normalizedSizeLimit(this.depth)) {
            tree = normalizeToChildren();
        } else {
            tree = normalizeToBuckets();
            checkState(featureChanges.isEmpty());
            checkState(treeChanges.isEmpty());

            if (tree.size() <= CanonicalNodeNameOrder.normalizedSizeLimit(this.depth)) {
                this.bucketTreesByBucket.clear();
                if (!tree.buckets().isEmpty()) {
                    tree = moveBucketsToChildren(tree);
                }
                if (this.depth == 0) {
                    pendingWritesCache.clear();
                }
            }
        }

        checkPendingWrites();

        this.initialSize = tree.size();
        this.initialNumTrees = tree.numTrees();
        if (this.depth == 0) {
            LOGGER.debug("Normalization took {}. Changes: {}", sw.stop(), numPendingChanges);
        }
        return tree;
    }

    private void checkPendingWrites() {
        final int pendingWritesThreshold = 10 * 1000;
        final boolean topLevelTree = this.depth == 0;// am I an actual (addressable) tree or bucket
                                                     // tree of a higher level one?
        final boolean forceWrite = pendingWritesCache.size() >= pendingWritesThreshold;
        if (!pendingWritesCache.isEmpty() && (topLevelTree || forceWrite)) {
            LOGGER.debug("calling db.putAll for {} buckets because {}...",
                    pendingWritesCache.size(), (topLevelTree ? "writing top level tree"
                            : "there are " + pendingWritesCache.size() + " pending bucket writes"));
            Stopwatch sw2 = Stopwatch.createStarted();
            obStore.putAll(pendingWritesCache.values().iterator());
            pendingWritesCache.clear();
            LOGGER.debug("done in {}", sw2.stop());
        }
    }

    /**
     * @param tree
     * @return
     */
    private RevTree moveBucketsToChildren(RevTree tree) {
        checkState(!tree.buckets().isEmpty());
        checkState(this.bucketTreesByBucket.isEmpty());

        for (Bucket bucket : tree.buckets().values()) {
            ObjectId id = bucket.getObjectId();
            RevTree bucketTree = this.loadTree(id);
            if (!bucketTree.buckets().isEmpty()) {
                moveBucketsToChildren(bucketTree);
            } else {
                Iterator<Node> children = RevObjects.children(bucketTree,
                        CanonicalNodeOrder.INSTANCE);
                while (children.hasNext()) {
                    Node next = children.next();
                    putInternal(next);
                }
            }
        }

        return normalizeToChildren();
    }

    /**
     * 
     */
    private RevTree normalizeToChildren() {
        Preconditions.checkState(this.bucketTreesByBucket.isEmpty());
        // remove delete requests, we're building a leaf tree out of our nodes
        deletes.clear();

        long size = featureChanges.size();
        if (!treeChanges.isEmpty()) {
            for (Node node : treeChanges.values()) {
                size += sizeOf(node);
            }
        }
        Collection<Node> features = featureChanges.values();
        Collection<Node> trees = treeChanges.values();
        RevTree tree = createLeafTree(size, features, trees);
        return tree;
    }

    public static RevTree createLeafTree(long size, Collection<Node> features,
            Collection<Node> trees) {
        Preconditions.checkNotNull(features);
        Preconditions.checkNotNull(trees);

        ImmutableList<Node> featuresList = ImmutableList.of();
        ImmutableList<Node> treesList = ImmutableList.of();

        if (!features.isEmpty()) {
            featuresList = NODE_STORAGE_ORDER.immutableSortedCopy(features);
        }
        if (!trees.isEmpty()) {
            treesList = NODE_STORAGE_ORDER.immutableSortedCopy(trees);
        }

        final ObjectId id = HashObject.hashTree(treesList, featuresList, (Iterable<Bucket>) null);

        return RevObjectFactory.defaultInstance().createTree(id, size, treesList, featuresList);
    }

    private RevTree createNodeTree(long size, int numTrees,
            @NonNull SortedMap<Integer, Bucket> buckets) {

        final ObjectId id = HashObject.hashTree(null, null, buckets.values());

        return RevObjectFactory.defaultInstance().createTree(id, size, numTrees,
                new TreeSet<>(buckets.values()));
    }

    private long sizeOf(Node node) {
        return node.getType().equals(TYPE.TREE) ? sizeOfTree(node.getObjectId()) : 1L;
    }

    /**
     * @return
     * 
     */
    private RevTree normalizeToBuckets() {
        // update all inner trees
        final ImmutableSet<Integer> changedBucketIndexes;

        // aggregate size delta for all changed buckets
        long sizeDelta = 0L;
        // aggregate number of trees delta for all changed buckets
        int treesDelta = 0;

        try {
            Multimap<Integer, Node> changesByBucket = getChangesByBucket();
            Preconditions.checkState(featureChanges.isEmpty());
            Preconditions.checkState(treeChanges.isEmpty());
            Preconditions.checkState(deletes.isEmpty());

            changedBucketIndexes = ImmutableSet.copyOf(changesByBucket.keySet());
            final Map<Integer, RevTree> bucketTrees = getBucketTrees(changedBucketIndexes);
            List<RevTree> newLeafTreesToSave = Lists.newArrayList();

            for (Integer bucketIndex : changedBucketIndexes) {
                final RevTree currentBucketTree = bucketTrees.get(bucketIndex);
                final int bucketDepth = this.depth + 1;
                final LegacyTreeBuilder bucketTreeBuilder = new LegacyTreeBuilder(this.obStore,
                        currentBucketTree, bucketDepth, this.pendingWritesCache,
                        this.normalizationThreshold);
                {
                    final Collection<Node> bucketEntries = changesByBucket.removeAll(bucketIndex);
                    for (Node node : bucketEntries) {
                        if (node.getObjectId().isNull()) {
                            bucketTreeBuilder.remove(node.getName());
                        } else {
                            bucketTreeBuilder.put(node);
                        }
                    }
                }
                final RevTree modifiedBucketTree = bucketTreeBuilder.build();
                final long bucketSizeDelta = modifiedBucketTree.size() - currentBucketTree.size();
                final int bucketTreesDelta = modifiedBucketTree.numTrees()
                        - currentBucketTree.numTrees();
                sizeDelta += bucketSizeDelta;
                treesDelta += bucketTreesDelta;
                if (modifiedBucketTree.isEmpty()) {
                    bucketTreesByBucket.remove(bucketIndex);
                } else {
                    final Bucket currBucket = this.bucketTreesByBucket.get(bucketIndex);
                    if (currBucket == null
                            || !currBucket.getObjectId().equals(modifiedBucketTree.getId())) {
                        // if (currBucket != null) {
                        // db.delete(currBucket.id());
                        // }
                        // have it on the pending writes set only if its not a leaf tree. Non bucket
                        // trees may be too large and cause OOM
                        if (null != pendingWritesCache.remove(currentBucketTree.getId())) {
                            // System.err.printf(" ---> removed bucket %s from list\n",
                            // currentBucketTree.getId());
                        }
                        if (!modifiedBucketTree.buckets().isEmpty()) {
                            pendingWritesCache.put(modifiedBucketTree.getId(), modifiedBucketTree);
                        } else {
                            // db.put(modifiedBucketTree);
                            newLeafTreesToSave.add(modifiedBucketTree);
                        }
                        Envelope bucketBounds = SpatialOps.boundsOf(modifiedBucketTree);
                        Bucket bucket = RevObjectFactory.defaultInstance().createBucket(
                                modifiedBucketTree.getId(), bucketIndex.intValue(), bucketBounds);
                        bucketTreesByBucket.put(bucketIndex, bucket);
                    }
                }
            }
            if (!newLeafTreesToSave.isEmpty()) {
                // db.putAll(newLeafTreesToSave.iterator());
                for (RevTree leaf : newLeafTreesToSave) {
                    pendingWritesCache.put(leaf.getId(), leaf);
                }
                newLeafTreesToSave.clear();
                checkPendingWrites();
                checkPendingWrites();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // compute final size and number of trees out of the aggregate deltas
        long accSize = sizeDelta;
        if (initialSize > CanonicalNodeNameOrder.normalizedSizeLimit(this.depth)) {
            accSize += initialSize;
        }
        int accChildTreeCount = this.initialNumTrees + treesDelta;

        RevTree tree = createNodeTree(accSize, accChildTreeCount, this.bucketTreesByBucket);
        return tree;
    }

    private Map<Integer, RevTree> getBucketTrees(ImmutableSet<Integer> changedBucketIndexes) {
        Map<Integer, RevTree> bucketTrees = new HashMap<>();
        List<Integer> missing = new ArrayList<>(changedBucketIndexes.size());
        for (Integer bucketIndex : changedBucketIndexes) {
            Bucket bucket = bucketTreesByBucket.get(bucketIndex);
            RevTree cached = bucket == null ? RevTree.EMPTY
                    : pendingWritesCache.get(bucket.getObjectId());
            if (cached == null) {
                missing.add(bucketIndex);
            } else {
                bucketTrees.put(bucketIndex, cached);
            }
        }
        if (!missing.isEmpty()) {
            Map<ObjectId, Integer> ids = Maps.uniqueIndex(missing,
                    new Function<Integer, ObjectId>() {
                        @Override
                        public ObjectId apply(Integer index) {
                            return bucketTreesByBucket.get(index).getObjectId();
                        }
                    });
            Iterator<RevObject> all = obStore.getAll(ids.keySet());
            while (all.hasNext()) {
                RevObject next = all.next();
                bucketTrees.put(ids.get(next.getId()), (RevTree) next);
            }
        }
        return bucketTrees;
    }

    private Multimap<Integer, Node> getChangesByBucket() {
        Multimap<Integer, Node> changesByBucket = ArrayListMultimap.create();
        if (!featureChanges.isEmpty()) {
            for (Node change : featureChanges.values()) {
                Integer bucketIndex = computeBucket(change.getName());
                changesByBucket.put(bucketIndex, change);
            }
            featureChanges.clear();
        }

        if (!treeChanges.isEmpty()) {
            for (Node change : treeChanges.values()) {
                Integer bucketIndex = computeBucket(change.getName());
                changesByBucket.put(bucketIndex, change);
            }
            treeChanges.clear();
        }

        if (!deletes.isEmpty()) {
            for (String delete : deletes) {
                Integer bucketIndex = computeBucket(delete);
                Node node = RevObjectFactory.defaultInstance().createNode(delete, ObjectId.NULL,
                        ObjectId.NULL, TYPE.FEATURE, null, null);
                changesByBucket.put(bucketIndex, node);
            }
            deletes.clear();
        }
        return changesByBucket;
    }

    protected final Integer computeBucket(final String path) {
        return CanonicalNodeNameOrder.bucket(path, this.depth);
    }

    /**
     * Gets an entry by key, this is potentially slow.
     * 
     * @param key
     * @return
     */
    public Optional<Node> get(final String key) {
        return getInternal(key, true);
    }

    /**
     * Adds or replaces an element in the tree with the given node.
     * <p>
     * <!-- Implementation detail: If the number of cached entries (entries held directly by this
     * tree) reaches {@link #DEFAULT_NORMALIZATION_THRESHOLD}, this tree will {@link #normalize()}
     * itself.
     * 
     * -->
     * 
     * @param node The {@link org.locationtech.geogig.model.Node Node} to add or replace.
     * @return a reference to this {@link org.locationtech.geogig.model.RevTreeBuilder
     *         RevTreeBuilder}
     */
    public synchronized boolean put(final Node node) {
        Preconditions.checkNotNull(node, "node can't be null");

        putInternal(node);
        if (numPendingChanges() >= this.normalizationThreshold) {
            // hit the split factor modification tolerance, lets normalize
            normalize();
        }
        return true;
    }

    /**
     * Removes an element from the tree
     * 
     * @param childName the name of the child to remove
     * @return {@code this}
     */
    public boolean remove(final String childName) {
        Preconditions.checkNotNull(childName, "key can't be null");
        if (null == featureChanges.remove(childName)) {
            treeChanges.remove(childName);
        }

        deletes.add(childName);
        return true;
    }

    @Override
    public boolean remove(final Node node) {
        Preconditions.checkNotNull(node, "key can't be null");
        return remove(node.getName());
    }

    /**
     * @return the new tree, not saved to the object database. Any bucket tree though is saved when
     *         this method returns.
     */
    public RevTree build() {
        RevTree tree = normalize();
        checkState(bucketTreesByBucket.isEmpty()
                || (featureChanges.isEmpty() && treeChanges.isEmpty()));
        if (obStore != null) {
            obStore.put(tree);
        }

        ObjectId oldid = HashObject.hashTree(original.trees(), original.features(),
                original.getBuckets());
        ObjectId newid = HashObject.hashTree(tree.trees(), tree.features(), tree.getBuckets());

        return oldid.equals(newid) ? original : tree;
    }

    public void dispose() {
        //
    }

    /**
     * Deletes all nodes that represent subtrees
     * 
     * @return {@code this}
     */
    public RevTreeBuilder clearSubtrees() {
        this.treeChanges.clear();
        return this;
    }

    /**
     * @return a new instance of a properly "named" empty tree (as in with a proper object id after
     *         applying {@link HashObject})
     */
    public static RevTree empty() {
        RevTree theEmptyTree = new LegacyTreeBuilder().build();
        return theEmptyTree;
    }

    @Override
    public boolean update(Node oldNode, Node newNode) {
        return put(newNode);
    }

    @Override
    public @Nullable RevTree build(BooleanSupplier abortFlag) {
        return build();
    }
}
