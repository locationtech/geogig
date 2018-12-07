/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;

/**
 * 
 */
@CanRunDuringConflict
public class FindFeatureTypeTrees extends AbstractGeoGigOp<List<NodeRef>> {

    private String refSpec;

    private ObjectStore source;

    private RevTree rootTree;

    /**
     * @param refSpec a ref spec, as supported by {@link RevParse}, that resolves to the root tree
     *        that's to be inspected for leaf trees with metadata ids set; most common use being of
     *        the type {@code <head name>[:<path>]}
     */
    public FindFeatureTypeTrees setRootTreeRef(String refSpec) {
        this.refSpec = refSpec;
        return this;
    }

    public FindFeatureTypeTrees setRootTree(RevTree rootTree) {
        this.rootTree = rootTree;
        return this;
    }

    public FindFeatureTypeTrees setSource(ObjectStore source) {
        this.source = source;
        return this;
    }

    @Override
    protected List<NodeRef> _call() {
        Preconditions.checkArgument(refSpec != null || rootTree != null,
                "refSpec was not provided");
        if (source == null) {
            source = objectDatabase();
        }

        final RevTree resolvedRootTree = resolveRootTree();
        return getSubTrees(resolvedRootTree, NodeRef.ROOT);
    }

    private List<NodeRef> getSubTrees(RevTree tree, String parentPath) {
        if (tree.numTrees() == 0) {
            return Collections.emptyList();
        }

        List<NodeRef> subtrees = new ArrayList<>();
        if (tree.treesSize() > 0) {
            subtrees.addAll(toNodeRef(tree.trees(), parentPath));
        }
        if (tree.bucketsSize() > 0) {
            Iterable<ObjectId> bucketIds = () -> Streams.stream(tree.getBuckets())
                    .map(Bucket::getObjectId).iterator();
            Iterator<RevTree> bucketTrees = source.getAll(bucketIds, BulkOpListener.NOOP_LISTENER,
                    RevTree.class);
            bucketTrees
                    .forEachRemaining(bucket -> subtrees.addAll(getSubTrees(bucket, parentPath)));
        }
        return subtrees;
    }

    private List<NodeRef> toNodeRef(List<Node> trees, String parentPath) {
        List<NodeRef> refs = new ArrayList<>();
        for (Node treeNode : trees) {
            Optional<ObjectId> metadataId = treeNode.getMetadataId();
            if (metadataId.isPresent()) {
                refs.add(NodeRef.create(parentPath, treeNode));
            } else {
                // nested parent
                if (!treeNode.getObjectId().equals(RevTree.EMPTY_TREE_ID)) {
                    String path = NodeRef.appendChild(parentPath, treeNode.getName());
                    RevTree subtree = source.getTree(treeNode.getObjectId());
                    List<NodeRef> subTrees = getSubTrees(subtree, path);
                    refs.addAll(subTrees);
                }
            }
        }
        return refs;
    }

    private RevTree resolveRootTree() {
        RevTree root = this.rootTree;
        if (root == null) {
            Optional<ObjectId> treeish = command(ResolveTreeish.class).setSource(source)
                    .setTreeish(refSpec).call();
            return treeish.isPresent() ? source.getTree(treeish.get()) : RevTree.EMPTY;
        }
        return root;
    }

}
