/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.plumbing.diff;

import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.diff.DiffTreeVisitor.Consumer;
import org.locationtech.geogig.storage.ObjectDatabase;

/**
 * A {@link Consumer} for diffs that computes the number for tree and feature changes between the
 * traversal's two trees.
 * 
 * <p>
 * Use {@link DiffCountConsumer#get() consumer.get()} after {@link DiffTreeVisitor#walk(Consumer)
 * visitor.walk(consumer)} to get the resulting {@link DiffObjectCount}.
 */
public class DiffCountConsumer implements DiffTreeVisitor.Consumer {

    private ObjectDatabase db;

    private DiffObjectCount count = new DiffObjectCount();

    public DiffCountConsumer(ObjectDatabase db) {
        this.db = db;
    }

    public DiffObjectCount get() {
        return count;
    }

    @Override
    public void feature(Node left, Node right) {
        if (left == null) {
            count.addedFeatures(1L);
        } else if (right == null) {
            count.removedFeatures(1L);
        } else {
            count.changedFeatures(1L);
        }
    }

    @Override
    public boolean tree(Node left, Node right) {
        final Node node = left == null ? right : left;
        if (NodeRef.ROOT.equals(node.getName())) {
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
    public boolean bucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
        if (left == null || right == null) {
            Bucket bucket = left == null ? right : left;
            addTreeFeatures(bucket.id(), left != null, right != null);
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

    @Override
    public void endTree(Node left, Node right) {
        // no need to do anything
    }

    @Override
    public void endBucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
        // no need to do anything
    }
}