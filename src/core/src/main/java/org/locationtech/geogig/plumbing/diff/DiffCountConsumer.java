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

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.NodeRef;
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

    private final ObjectStore db;

    private DiffObjectCount count = new DiffObjectCount();

    public DiffCountConsumer(ObjectStore db) {
        this.db = db;
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
            addTreeFeatures(node.getObjectId(), left != null, right != null);
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
            Bucket left, Bucket right) {

        if (left == null || right == null) {
            Bucket bucket = left == null ? right : left;
            addTreeFeatures(bucket.getObjectId(), left != null, right != null);
            return false;
        }
        return true;
    }

    private boolean addTreeFeatures(ObjectId treeId, boolean leftPresent, boolean rightPresent) {
        RevTree tree = db.getTree(treeId);
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