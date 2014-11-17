/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.plumbing.diff;

import java.util.Random;

import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.storage.ObjectDatabase;

public class TreeTestSupport {

    public static RevTreeBuilder createTreesTree(ObjectDatabase source, int numSubTrees,
            int featuresPerSubtre, ObjectId metadataId) {

        RevTreeBuilder builder = new RevTreeBuilder(source);
        for (int treeN = 0; treeN < numSubTrees; treeN++) {
            RevTree subtree = createFeaturesTree(source, "subtree" + treeN, featuresPerSubtre)
                    .build();
            source.put(subtree);
            builder.put(Node.create("subtree" + treeN, subtree.getId(), metadataId, TYPE.TREE, null));
        }
        return builder;
    }

    public static RevTreeBuilder createFeaturesTree(ObjectDatabase source, final String namePrefix,
            final int numEntries) {
        return createFeaturesTree(source, namePrefix, numEntries, 0, false);
    }

    public static RevTreeBuilder createFeaturesTree(ObjectDatabase source, final String namePrefix,
            final int numEntries, final int startIndex, boolean randomIds) {

        RevTreeBuilder tree = new RevTreeBuilder(source);
        for (int i = startIndex; i < startIndex + numEntries; i++) {
            tree.put(featureNode(namePrefix, i, randomIds));
        }
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

}
