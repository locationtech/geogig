/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.locationtech.geogig.model.NodeRef.PATH_SEPARATOR;

import java.util.List;

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

/**
 * Searches for a {@link Node} within a particular tree.
 * 
 * @see Node
 * @see RevTree
 * @see ObjectDatabase
 */
public class DepthSearch {

    private final ObjectStore objectDb;

    private CanonicalNodeNameOrder refOrder = CanonicalNodeNameOrder.INSTANCE;

    /**
     * Constructs a new {@code DepthSearch} with the given parameters.
     * 
     * @param db the object database where {@link Node}s and {@link RevTree}s are stored
     */
    public DepthSearch(final ObjectStore db) {
        this.objectDb = db;
    }

    /**
     * Searches for a {@link Node} in the given tree.
     * 
     * @param rootTreeId the tree to search
     * @param path the path to the {@code Node} to search for
     * @return an {@link Optional} of the {@code Node} if it was found, or {@link Optional#absent()}
     *         if it wasn't found.
     */
    public Optional<NodeRef> find(final ObjectId rootTreeId, final String path) {
        RevTree tree = objectDb.get(rootTreeId, RevTree.class);
        if (tree == null) {
            return null;
        }
        return find(tree, path);
    }

    /**
     * Searches for a {@link Node} in the given tree.
     * 
     * @param rootTree the tree to search
     * @param childPath the path to the {@code Node} to search for
     * @return an {@link Optional} of the {@code Node} if it was found, or {@link Optional#absent()}
     *         if it wasn't found.
     */
    public Optional<NodeRef> find(final RevTree rootTree, final String childPath) {
        return find(rootTree, "", childPath);
    }

    /**
     * Searches for the direct child path in the parent tree.
     * 
     * @param parent the tree to search
     * @param parentPath the path of the parent tree
     * @param childPath the path to search for
     * @return an {@link Optional} of the {@code Node} if the child path was found, or
     *         {@link Optional#absent()} if it wasn't found.
     */
    public Optional<NodeRef> find(final RevTree parent, final String parentPath,
            final String childPath) {

        checkNotNull(parent, "parent");
        checkNotNull(parentPath, "parentPath");
        checkNotNull(childPath, "childPath");
        checkArgument(parentPath.isEmpty()
                || parentPath.charAt(parentPath.length() - 1) != PATH_SEPARATOR);
        checkArgument(!childPath.isEmpty(), "empty child path: '%s/%s'", parentPath, childPath);
        checkArgument(childPath.charAt(childPath.length() - 1) != PATH_SEPARATOR);

        checkArgument(parentPath.isEmpty() || childPath.startsWith(parentPath + PATH_SEPARATOR));

        final List<String> parentSteps = Lists
                .newArrayList(Splitter.on(PATH_SEPARATOR).omitEmptyStrings().split(parentPath));
        List<String> childSteps = Lists.newArrayList(Splitter.on(PATH_SEPARATOR).split(childPath));
        childSteps = childSteps.subList(parentSteps.size(), childSteps.size());

        RevTree subTree = parent;
        ObjectId metadataId = ObjectId.NULL;
        for (int i = 0; i < childSteps.size() - 1; i++) {
            String directChildName = childSteps.get(i);
            Optional<Node> subtreeRef = getDirectChild(subTree, directChildName, 0);
            if (!subtreeRef.isPresent()) {
                return Optional.absent();
            }
            metadataId = subtreeRef.get().getMetadataId().or(ObjectId.NULL);
            subTree = objectDb.get(subtreeRef.get().getObjectId(), RevTree.class);
        }
        final String childName = childSteps.get(childSteps.size() - 1);
        Optional<Node> node = getDirectChild(subTree, childName, 0);
        NodeRef depthSearchResult = null;
        if (node.isPresent()) {
            String nodeParentPath = NodeRef.parentPath(childPath);
            depthSearchResult = new NodeRef(node.get(), nodeParentPath,
                    node.get().getMetadataId().or(metadataId));
        }
        return Optional.fromNullable(depthSearchResult);
    }

    /**
     * @param parent
     * @param directChildName
     * @return
     */
    public Optional<Node> getDirectChild(RevTree parent, String directChildName,
            final int subtreesDepth) {
        if (parent.isEmpty()) {
            return Optional.absent();
        }

        if (parent.treesSize() > 0 || parent.featuresSize() > 0) {
            if (parent.treesSize() > 0) {
                final int size = parent.treesSize();
                for (int i = 0; i < size; i++) {
                    Node node = parent.getTree(i);
                    if (directChildName.equals(node.getName())) {
                        return Optional.of(node);
                    }
                }
            }
            if (parent.featuresSize() > 0) {
                final int size = parent.featuresSize();
                for (int i = 0; i < size; i++) {
                    Node node = parent.getFeature(i);
                    if (directChildName.equals(node.getName())) {
                        return Optional.of(node);
                    }
                }
            }
            return Optional.absent();
        }

        // TODO revisit, shouldn't be using NodeOrder.bucket (a non static method) to respect the
        // ordering of the tree layout being used?
        Integer bucket = refOrder.bucket(directChildName, subtreesDepth);
        Bucket subtreeBucket = parent.getBucket(bucket.intValue()).orElse(null);
        if (subtreeBucket == null) {
            return Optional.absent();
        }
        RevTree subtree = objectDb.get(subtreeBucket.getObjectId(), RevTree.class);
        return getDirectChild(subtree, directChildName, subtreesDepth + 1);
    }
}
