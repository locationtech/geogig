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
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.HashObject;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.hash.HashCode;
import com.vividsolutions.jts.geom.Envelope;

public class RevObjectTestSupport {

    public static RevTree createTreesTree(ObjectStore source, int numSubTrees,
            int featuresPerSubtre, ObjectId metadataId) {

        RevTree tree = createTreesTreeBuilder(source, numSubTrees, featuresPerSubtre, metadataId)
                .build();
        source.put(tree);
        return tree;
    }

    public static RevTreeBuilder createTreesTreeBuilder(ObjectStore source, int numSubTrees,
            int featuresPerSubtre, ObjectId metadataId) {

        RevTreeBuilder builder = CanonicalTreeBuilder.create(source);
        for (int treeN = 0; treeN < numSubTrees; treeN++) {
            RevTree subtree = createFeaturesTreeBuilder(source, "subtree" + treeN,
                    featuresPerSubtre).build();
            source.put(subtree);
            builder.put(
                    Node.create("subtree" + treeN, subtree.getId(), metadataId, TYPE.TREE, null));
        }
        return builder;
    }

    public static RevTreeBuilder createFeaturesTreeBuilder(ObjectStore source,
            final String namePrefix, final int numEntries) {
        return createFeaturesTreeBuilder(source, namePrefix, numEntries, 0, false);
    }

    public static RevTree createFeaturesTree(ObjectStore source, final String namePrefix,
            final int numEntries) {
        RevTree tree = createFeaturesTreeBuilder(source, namePrefix, numEntries).build();
        source.put(tree);
        return tree;
    }

    public static RevTreeBuilder createFeaturesTreeBuilder(ObjectStore source,
            final String namePrefix, final int numEntries, final int startIndex,
            boolean randomIds) {

        RevTreeBuilder tree = CanonicalTreeBuilder.create(source);
        for (int i = startIndex; i < startIndex + numEntries; i++) {
            tree.put(featureNode(namePrefix, i, randomIds));
        }
        return tree;
    }

    public static RevTree createFeaturesTree(ObjectDatabase source, final String namePrefix,
            final int numEntries, final int startIndex, boolean randomIds) {

        RevTree tree = createFeaturesTreeBuilder(source, namePrefix, numEntries, startIndex,
                randomIds).build();
        source.put(tree);
        return tree;
    }

    public static RevTreeBuilder createLargeFeaturesTreeBuilder(ObjectDatabase source,
            final String namePrefix, final int numEntries, final int startIndex,
            boolean randomIds) {

        RevTreeBuilder tree = CanonicalTreeBuilder.create(source);

        for (int i = startIndex; i < startIndex + numEntries; i++) {
            tree.put(featureNode(namePrefix, i, randomIds));
        }
        return tree;
    }

    public static RevTree createLargeFeaturesTree(ObjectDatabase source, final String namePrefix,
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
            oid = ObjectId.createNoClone(raw);
        } else {// predictable id
            oid = RevObjectTestSupport.hashString(name);
        }
        Node ref = Node.create(name, oid, ObjectId.NULL, TYPE.FEATURE,
                new Envelope(index, index + 1, index, index + 1));
        return ref;
    }

    /**
     * Only for testing: allows to return a {@link RevFeature} with the specified id instead of the
     * one resulting from {@link HashObject}
     */
    public static RevFeature featureForceId(ObjectId forceId, Object... rawValues) {
        RevFeatureBuilder builder = RevFeatureBuilder.builder().addAll(rawValues);
        RevFeature revFeature = builder.build(forceId);
        return revFeature;
    }

    public static RevFeature feature(Object... rawValues) {
        RevFeatureBuilder builder = RevFeatureBuilder.builder().addAll(rawValues);
        return builder.build();
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
        return ObjectId.createNoClone(hashCode.asBytes());
    }

    public static Set<Node> getTreeNodes(RevTree tree, ObjectStore source) {
        Set<Node> nodes = new HashSet<>();
        if (tree.buckets().isEmpty()) {
            nodes.addAll(tree.features());
            nodes.addAll(tree.trees());
        } else {
            Iterable<ObjectId> ids = Iterables.transform(tree.buckets().values(),
                    (b) -> b.getObjectId());
            Iterator<RevTree> buckets = source.getAll(ids, BulkOpListener.NOOP_LISTENER,
                    RevTree.class);
            while (buckets.hasNext()) {
                nodes.addAll(getTreeNodes(buckets.next(), source));
            }
        }
        return nodes;
    }
}
