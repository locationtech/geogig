/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.HashObject;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;

public class RevObjectTestSupport {

    public static final RevObjectTestSupport INSTANCE = new RevObjectTestSupport();

    private boolean spatialTrees;

    private Envelope maxBounds;

    /**
     * Controls whether the {@link RevTree}s created through the {@code #createFeaturesTree} methods
     * create quad or canonical trees
     * 
     * @param spatialTrees
     */
    public void setBuildSpatialTrees(boolean spatialTrees) {
        this.spatialTrees = spatialTrees;
    }

    /**
     * Sets the max bounds of the quad trees if {@link #setBuildSpatialTrees
     * setBuildSpatialTrees(true)} was called, defaults to WGS84 bounds if not set.
     */
    public void setQuadTreeMaxBounds(Envelope maxBounds) {
        this.maxBounds = maxBounds;
    }

    private Envelope getMaxBounds() {
        return maxBounds == null ? new Envelope(-180, 180, -90, 90) : maxBounds;
    }

    public RevTree createTreesTree(ObjectStore source, int numSubTrees, int featuresPerSubtre,
            ObjectId metadataId) {

        RevTree tree = createTreesTreeBuilder(source, numSubTrees, featuresPerSubtre, metadataId)
                .build();
        source.put(tree);
        return tree;
    }

    public RevTreeBuilder createTreesTreeBuilder(ObjectStore source, int numSubTrees,
            int featuresPerSubtre, ObjectId metadataId) {

        RevTreeBuilder builder = RevTreeBuilder.builder(source);
        for (int treeN = 0; treeN < numSubTrees; treeN++) {
            RevTree subtree = createFeaturesTreeBuilder(source, "subtree" + treeN,
                    featuresPerSubtre).build();
            source.put(subtree);
            builder.put(RevObjectFactory.defaultInstance().createNode("subtree" + treeN,
                    subtree.getId(), metadataId, TYPE.TREE, null, null));
        }
        return builder;
    }

    public RevTreeBuilder createFeaturesTreeBuilder(ObjectStore source, final String namePrefix,
            final int numEntries) {
        return createFeaturesTreeBuilder(source, namePrefix, numEntries, 0, false);
    }

    public RevTree createFeaturesTree(ObjectStore source, final String namePrefix,
            final int numEntries) {
        RevTree tree = createFeaturesTreeBuilder(source, namePrefix, numEntries).build();
        source.put(tree);
        return tree;
    }

    public RevTreeBuilder createFeaturesTreeBuilder(ObjectStore source, final String namePrefix,
            final int numEntries, final int startIndex, boolean randomIds) {

        RevTreeBuilder builder;
        if (spatialTrees) {
            Envelope maxBounds = getMaxBounds();
            builder = RevTreeBuilder.quadBuilder(source, source, RevTree.EMPTY, maxBounds);
        } else {
            builder = RevTreeBuilder.builder(source);
        }
        for (int i = startIndex; i < startIndex + numEntries; i++) {
            builder.put(featureNode(namePrefix, i, randomIds));
        }
        return builder;
    }

    public RevTree createFeaturesTree(ObjectStore source, final String namePrefix,
            final int numEntries, final int startIndex, boolean randomIds) {

        RevTree tree = createFeaturesTreeBuilder(source, namePrefix, numEntries, startIndex,
                randomIds).build();
        source.put(tree);
        return tree;
    }

    public RevTreeBuilder createLargeFeaturesTreeBuilder(ObjectDatabase source,
            final String namePrefix, final int numEntries, final int startIndex,
            boolean randomIds) {

        RevTreeBuilder builder;
        if (spatialTrees) {
            Envelope maxBounds = getMaxBounds();
            builder = RevTreeBuilder.quadBuilder(source, source, RevTree.EMPTY, maxBounds);
        } else {
            builder = RevTreeBuilder.builder(source);
        }

        for (int i = startIndex; i < startIndex + numEntries; i++) {
            builder.put(featureNode(namePrefix, i, randomIds));
        }
        return builder;
    }

    public RevTree createLargeFeaturesTree(ObjectDatabase source, final String namePrefix,
            final int numEntries, final int startIndex, boolean randomIds) {

        RevTreeBuilder builder = createLargeFeaturesTreeBuilder(source, namePrefix, numEntries,
                startIndex, randomIds);
        RevTree tree = builder.build();
        source.put(tree);
        return tree;
    }

    public static List<Node> featureNodes(int fromIndexInclussive, int toIndexExclussive,
            boolean randomIds) {

        List<Node> nodes = new ArrayList<>(1 + (toIndexExclussive - fromIndexInclussive));
        for (int i = fromIndexInclussive; i < toIndexExclussive; i++) {
            nodes.add(featureNode("f", i, randomIds));
        }
        return nodes;
    }

    public static Node featureNode(String namePrefix, int index) {
        return featureNode(namePrefix, index, false);
    }

    private static Random RND = new Random();

    public static Node featureNode(String namePrefix, int index, boolean randomIds) {
        String name = namePrefix + String.valueOf(index);
        ObjectId oid;
        if (randomIds) {
            byte[] raw = new byte[ObjectId.NUM_BYTES];
            RND.nextBytes(raw);
            oid = ObjectId.create(raw);
        } else {// predictable id
            oid = RevObjectTestSupport.hashString(name);
        }
        Node ref = RevObjectFactory.defaultInstance().createNode(name, oid, ObjectId.NULL,
                TYPE.FEATURE, new Envelope(index, index + 1, index, index + 1), null);
        return ref;
    }

    /**
     * Only for testing: allows to return a {@link RevFeature} with the specified id instead of the
     * one resulting from {@link HashObject}
     */
    public static RevFeature featureForceId(ObjectId forceId, Object... rawValues) {
        RevFeature revFeature = RevFeature.builder().addAll(rawValues).id(forceId).build();
        return revFeature;
    }

    public static RevFeature feature(Object... rawValues) {
        return RevFeature.builder().addAll(rawValues).build();
    }

    /**
     * Utility method to quickly hash a String and create an ObjectId out of the string SHA-1 hash.
     * <p>
     * Note this method is to hash a string, and is used for testing, not to convert the string
     * representation of an ObjectId. Use {@link ObjectId#valueOf(String)} for that purpose.
     * </p>
     * 
     * @param strToHash
     * @return the {@code ObjectId} generated from the string
     */
    public static ObjectId hashString(final String strToHash) {
        Preconditions.checkNotNull(strToHash);
        HashCode hashCode = ObjectId.HASH_FUNCTION.hashString(strToHash, Charset.forName("UTF-8"));
        return ObjectId.create(hashCode.asBytes());
    }

    public static Set<Node> getTreeNodes(RevTree tree, ObjectStore source) {
        Set<Node> nodes = new HashSet<>();
        if (tree.bucketsSize() == 0) {
            nodes.addAll(tree.features());
            nodes.addAll(tree.trees());
        } else {
            Iterable<ObjectId> ids = Iterables.transform(tree.getBuckets(), Bucket::getObjectId);
            Iterator<RevTree> buckets = source.getAll(ids, BulkOpListener.NOOP_LISTENER,
                    RevTree.class);
            while (buckets.hasNext()) {
                nodes.addAll(getTreeNodes(buckets.next(), source));
            }
        }
        return nodes;
    }

    public static Set<RevTree> getAllTrees(ObjectStore source, ObjectId rootTree) {
        RevTree root = rootTree.equals(RevTree.EMPTY_TREE_ID) ? RevTree.EMPTY
                : source.getTree(rootTree);
        return getAllTrees(source, root);
    }

    public static Set<RevTree> getAllTrees(ObjectStore source, RevTree tree) {
        Set<RevTree> trees = new HashSet<>();
        trees.add(tree);
        if (tree.bucketsSize() > 0) {
            Iterable<ObjectId> ids = Iterables.transform(tree.getBuckets(), Bucket::getObjectId);

            List<RevTree> buckets = Lists
                    .newArrayList(source.getAll(ids, BulkOpListener.NOOP_LISTENER, RevTree.class));
            trees.addAll(buckets);
            for (RevTree bucket : buckets) {
                trees.addAll(getAllTrees(source, bucket));
            }
        }
        return trees;
    }

    /**
     * Brute force lookup of nodes named {@code nodeName} on the {@code tree} from {@code store}
     * <p>
     * There shall always be one single node for a node name on a tree, this method can be used to
     * identify such catastrophic errors
     */
    public static List<Node> findNode(String nodeName, RevTree tree, ObjectStore store) {
        List<Node> matches = new ArrayList<>(2);
        Iterator<Node> children = RevObjects.children(tree, CanonicalNodeOrder.INSTANCE);
        while (children.hasNext()) {
            Node node = children.next();
            if (nodeName.equals(node.getName())) {
                matches.add(node);
            }
        }
        for (Bucket b : tree.getBuckets()) {
            RevTree bt = store.getTree(b.getObjectId());
            matches.addAll(findNode(nodeName, bt, store));
        }
        return matches;
    }

    public static int depth(ObjectStore store, RevTree tree) {

        int depth = 0;

        for (Bucket b : tree.getBuckets()) {
            RevTree btree = store.getTree(b.getObjectId());
            depth = Math.max(depth, 1 + depth(store, btree));
        }
        return depth;
    }

    public static List<RevCommit> createCommits(final int numCommits) {
        return createCommits(numCommits, (Platform) null);
    }

    public static List<RevCommit> createCommits(final int numCommits, @Nullable Platform platform) {
        LinkedList<RevCommit> commits = new LinkedList<>();

        // much faster way of creating several fake commits than running CommitOp N times
        RevCommitBuilder builder = RevCommit.builder().platform(platform);
        long timeStamp = System.currentTimeMillis();
        builder.author("gabe").authorEmail("gabe@example.com").committer("me")
                .committerEmail("me@too.com").committerTimestamp(timeStamp)
                .committerTimeZoneOffset(-1).authorTimestamp(timeStamp).authorTimeZoneOffset(-3)
                .treeId(RevTree.EMPTY_TREE_ID);

        ObjectId parent = null;
        for (int i = 1; i <= numCommits; i++) {
            ++timeStamp;
            builder.authorTimestamp(timeStamp).committerTimestamp(timeStamp);
            List<ObjectId> parents = parent == null ? null : ImmutableList.of(parent);
            RevCommit commit = builder.parentIds(parents).message("commit " + i).build();
            commits.addFirst(commit);
            parent = commit.getId();
        }
        return commits;
    }
}
