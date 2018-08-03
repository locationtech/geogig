/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.storage.ObjectStore;

/**
 * A {@link Consumer} for diffs that computes the number for tree and feature changes between the
 * traversal's two trees.
 * 
 * <p>
 * Use {@link DiffCountConsumer#get() consumer.get()} after {@link PreOrderDiffWalk#walk(Consumer)
 * visitor.walk(consumer)} to get the resulting {@link DiffObjectCount}.
 */
public class DiffCountConsumer extends PreOrderDiffWalk.AbstractConsumer {

    private DiffObjectCount count = new DiffObjectCount();

    private ObjectStore leftSource;

    private ObjectStore rightSource;

    public DiffCountConsumer(ObjectStore db) {
        this(db, db);
    }

    public DiffCountConsumer(ObjectStore leftSource, ObjectStore rightSource) {
        this.leftSource = leftSource;
        this.rightSource = rightSource;
    }

    public DiffObjectCount get() {
        return count;
    }

    @Override
    public boolean feature(NodeRef left, NodeRef right) {
        if (left == null) {
            count.addedFeatures(1L);
        } else if (right == null) {
            count.removedFeatures(1L);
        } else {
            count.changedFeatures(1L);
        }
        return true;
    }

    @Override
    public boolean tree(NodeRef left, NodeRef right) {
        final NodeRef node = left == null ? right : left;
        if (NodeRef.ROOT.equals(node.name())) {
            // ignore the call on the root tree and follow the traversal
            return true;
        }
        if (left == null || right == null) {
            RevTree tree = (left == null ? rightSource : leftSource).getTree(node.getObjectId());
            addTreeFeatures(tree, left != null, right != null);
            if (left == null) {
                count.addedTrees(1);
            } else {
                count.removedTrees(1);
            }
            return false;
        }

        count.changedTrees(1);// the tree changed, or this method wouldn't have been called
        return true;
    }

    @Override
    public boolean bucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
            @Nullable Bucket left, @Nullable Bucket right) {

        if (bucketIndex.left().isEmpty() || bucketIndex.right().isEmpty()) {
            Bucket bucket = left == null ? right : left;
            RevTree tree = (left == null ? rightSource : leftSource).getTree(bucket.getObjectId());
            addTreeFeatures(tree, left != null, right != null);
            return false;
        }
        return true;
    }

    private boolean addTreeFeatures(RevTree tree, boolean leftPresent, boolean rightPresent) {
        long size = tree.size();
        if (leftPresent && rightPresent) {
            count.changedFeatures(size);
        } else if (leftPresent) {
            count.removedFeatures(size);
        } else {
            count.addedFeatures(size);
        }

        int numTrees = tree.numTrees();
        return numTrees > 0;
    }
}