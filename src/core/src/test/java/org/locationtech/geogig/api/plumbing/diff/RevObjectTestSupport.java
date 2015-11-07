/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.diff;

import java.util.Random;
import java.util.concurrent.ExecutorService;

import org.locationtech.geogig.api.DefaultPlatform;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureImpl;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.repository.RevTreeBuilder2;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.common.util.concurrent.MoreExecutors;

public class RevObjectTestSupport {

    public static RevTree createTreesTree(ObjectDatabase source, int numSubTrees,
            int featuresPerSubtre, ObjectId metadataId) {

        RevTree tree = createTreesTreeBuilder(source, numSubTrees, featuresPerSubtre, metadataId)
                .build();
        source.put(tree);
        return tree;
    }

    public static RevTreeBuilder createTreesTreeBuilder(ObjectDatabase source, int numSubTrees,
            int featuresPerSubtre, ObjectId metadataId) {

        RevTreeBuilder builder = new RevTreeBuilder(source);
        for (int treeN = 0; treeN < numSubTrees; treeN++) {
            RevTree subtree = createFeaturesTreeBuilder(source, "subtree" + treeN,
                    featuresPerSubtre).build();
            source.put(subtree);
            builder.put(Node.create("subtree" + treeN, subtree.getId(), metadataId, TYPE.TREE, null));
        }
        return builder;
    }

    public static RevTreeBuilder createFeaturesTreeBuilder(ObjectDatabase source,
            final String namePrefix, final int numEntries) {
        return createFeaturesTreeBuilder(source, namePrefix, numEntries, 0, false);
    }

    public static RevTree createFeaturesTree(ObjectDatabase source, final String namePrefix,
            final int numEntries) {
        RevTree tree = createFeaturesTreeBuilder(source, namePrefix, numEntries).build();
        source.put(tree);
        return tree;
    }

    public static RevTreeBuilder createFeaturesTreeBuilder(ObjectDatabase source,
            final String namePrefix, final int numEntries, final int startIndex, boolean randomIds) {

        RevTreeBuilder tree = new RevTreeBuilder(source);
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

    public static RevTreeBuilder2 createLargeFeaturesTreeBuilder(ObjectDatabase source,
            final String namePrefix, final int numEntries, final int startIndex, boolean randomIds) {

        Platform platform = new DefaultPlatform();// for tmp directory lookup
        ExecutorService executorService = MoreExecutors.sameThreadExecutor();
        RevTreeBuilder2 tree = new RevTreeBuilder2(source, RevTree.EMPTY, ObjectId.NULL, platform,
                executorService);

        for (int i = startIndex; i < startIndex + numEntries; i++) {
            tree.put(featureNode(namePrefix, i, randomIds));
        }
        return tree;
    }

    public static RevTree createLargeFeaturesTree(ObjectDatabase source, final String namePrefix,
            final int numEntries, final int startIndex, boolean randomIds) {

        RevTreeBuilder2 builder = createLargeFeaturesTreeBuilder(source, namePrefix, numEntries,
                startIndex, randomIds);
        RevTree tree = builder.build();
        source.put(tree);
        return tree;
    }

    public static Node featureNode(String namePrefix, int index) {
        return featureNode(namePrefix, index, false);
    }

    public static Node featureNode(String namePrefix, int index, boolean randomIds) {
        String name = namePrefix + String.valueOf(index);
        ObjectId oid;
        if (randomIds) {
            oid = ObjectId.forString(name + index + String.valueOf(new Random(index).nextInt()));
        } else {// predictable id
            oid = ObjectId.forString(name);
        }
        Node ref = Node.create(name, oid, ObjectId.NULL, TYPE.FEATURE, null);
        return ref;
    }

    public RevFeature feature(int fakeIdIndex, Object... rawValues) {
        ObjectId id = ObjectId.forString(String.valueOf(fakeIdIndex));
        return feature(id, rawValues);
    }

    public RevFeature feature(ObjectId id, Object... rawValues) {

        ImmutableList<Optional<Object>> values;
        Builder<Optional<Object>> builder = ImmutableList.builder();
        for (int i = 0; rawValues != null && i < rawValues.length; i++) {
            builder.add(Optional.fromNullable(rawValues[i]));
        }
        values = builder.build();
        return new RevFeatureImpl(id, values);
    }
}
