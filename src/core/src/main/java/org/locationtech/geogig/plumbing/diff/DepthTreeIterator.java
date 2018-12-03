/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Iterator;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;

/**
 * An iterator over a {@link RevTree} that can return different results depending on the
 * {@link #Strategy} given;
 */
public class DepthTreeIterator extends AbstractIterator<NodeRef> {
    public enum Strategy {
        /**
         * Default strategy, list the all direct child entries of a tree, no recursion
         */
        CHILDREN,
        /**
         * List only the direct child entries of a tree that are of type FEATURE
         */
        FEATURES_ONLY,
        /**
         * List only the direct child entries of a tree that are of type TREE
         */
        TREES_ONLY,
        /**
         * Recursively list the contents of a tree in depth-first order, including both TREE and
         * FEATURE entries
         */
        RECURSIVE,
        /**
         * Recursively list the contents of a tree in depth-first order, but do not report TREE
         * entries, only FEATURE ones
         */
        RECURSIVE_FEATURES_ONLY,
        /**
         * Recursively list the contents of a tree in depth-first order, but do not report TREE
         * entries, only FEATURE ones
         */
        RECURSIVE_TREES_ONLY
    }

    private Iterator<NodeRef> iterator;

    private ObjectStore source;

    private Strategy strategy;

    private Predicate<Bounded> boundsFilter;

    private NodeToRef functor;

    private RevTree tree;

    private String treePath;

    private ObjectId metadataId;

    private static class NodeToRef implements Function<Node, NodeRef> {

        private final String treePath;

        private final ObjectId metadataId;

        public NodeToRef(String treePath, ObjectId metadataId) {
            this.treePath = treePath;
            this.metadataId = metadataId;
        }

        @Override
        public NodeRef apply(Node node) {
            return new NodeRef(node, treePath, node.getMetadataId().or(metadataId));
        }
    };

    public DepthTreeIterator(final String treePath, final ObjectId metadataId, RevTree tree,
            ObjectStore source, Strategy strategy) {
        checkNotNull(treePath);
        checkNotNull(metadataId);
        checkNotNull(tree);
        checkNotNull(source);
        checkNotNull(strategy);

        this.tree = tree;
        this.treePath = treePath;
        this.metadataId = metadataId;
        this.source = source;
        this.strategy = strategy;
        this.functor = new NodeToRef(treePath, metadataId);
        this.boundsFilter = Predicates.alwaysTrue();
    }

    public void setBoundsFilter(@Nullable Predicate<Bounded> boundsFilter) {
        Predicate<Bounded> alwaysTrue = Predicates.alwaysTrue();
        this.boundsFilter = boundsFilter == null ? alwaysTrue : boundsFilter;
    }

    @Override
    protected NodeRef computeNext() {
        if (iterator == null) {
            switch (strategy) {
            case CHILDREN:
                iterator = Iterators.transform(new Children(tree), functor);
                break;
            case FEATURES_ONLY:
                iterator = Iterators.transform(new Features(tree), functor);
                break;
            case TREES_ONLY:
                iterator = Iterators.transform(new Trees(tree), functor);
                break;
            case RECURSIVE:
                iterator = new Recursive(treePath, metadataId, tree, true, true);
                break;
            case RECURSIVE_FEATURES_ONLY:
                iterator = new Recursive(treePath, metadataId, tree, true, false);
                break;
            case RECURSIVE_TREES_ONLY:
                iterator = new Recursive(treePath, metadataId, tree, false, true);
                break;
            default:
                throw new IllegalArgumentException("Unrecognized strategy: " + strategy);
            }

        }
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return endOfData();
    }

    private class Recursive extends AbstractIterator<NodeRef> {

        private boolean features;

        private boolean trees;

        private Iterator<Node> myEntries;

        private Iterator<NodeRef> currEntryIterator;

        private NodeToRef functor;

        public Recursive(String treePath, ObjectId metadataId, RevTree tree, boolean features,
                boolean trees) {
            Preconditions.checkArgument(features || trees);
            this.functor = new NodeToRef(treePath, metadataId);
            this.features = features;
            this.trees = trees;
            if (!features) {
                this.myEntries = new Trees(tree);
            } else {
                this.myEntries = new Children(tree);
            }
            currEntryIterator = Collections.emptyIterator();
        }

        @Override
        protected NodeRef computeNext() {
            while (!currEntryIterator.hasNext()) {
                if (myEntries.hasNext()) {
                    currEntryIterator = resolveEntryIterator(myEntries.next());
                } else {
                    return endOfData();
                }
            }
            return currEntryIterator.next();
        }

        private Iterator<NodeRef> resolveEntryIterator(Node next) {
            if (TYPE.FEATURE.equals(next.getType())) {
                if (features) {
                    return Iterators.singletonIterator(functor.apply(next));
                }
                return Collections.emptyIterator();
            }
            Preconditions.checkArgument(TYPE.TREE.equals(next.getType()));

            ObjectId treeId = next.getObjectId();
            RevTree childTree = source.getTree(treeId);

            String childTreePath = NodeRef.appendChild(this.functor.treePath, next.getName());
            Iterator<NodeRef> children = new Recursive(childTreePath,
                    next.getMetadataId().or(functor.metadataId), childTree, features, trees);
            if (trees) {
                children = Iterators.concat(Iterators.singletonIterator(functor.apply(next)),
                        children);
            }
            return children;
        }
    }

    private class Children extends AbstractIterator<Node> {

        private Iterator<Node> children;

        public Children(RevTree tree) {
            if (tree.bucketsSize() > 0) {
                this.children = new Buckets(tree);
            } else {
                this.children = Iterators.filter(
                        RevObjects.children(tree, CanonicalNodeOrder.INSTANCE), boundsFilter);
            }
        }

        @Override
        protected Node computeNext() {
            if (children.hasNext()) {
                return children.next();
            }
            return endOfData();
        }
    }

    private class Features extends AbstractIterator<Node> {

        private Iterator<Node> features;

        public Features(RevTree tree) {
            if (tree.featuresSize() > 0) {
                this.features = Iterators.filter(tree.features().iterator(), boundsFilter);
            } else if (tree.bucketsSize() > 0) {
                this.features = new FeatureBuckets(tree);
            } else {
                this.features = Collections.emptyIterator();
            }
        }

        @Override
        protected Node computeNext() {
            if (features.hasNext()) {
                return features.next();
            }
            return endOfData();
        }
    }

    private class Trees extends AbstractIterator<Node> {

        private Iterator<Node> trees;

        public Trees(RevTree tree) {
            if (tree.numTrees() == 0) {
                this.trees = Collections.emptyIterator();
            } else if (tree.treesSize() > 0) {
                this.trees = Iterators.filter(tree.trees().iterator(), boundsFilter);
            } else if (tree.bucketsSize() > 0) {
                this.trees = new TreeBuckets(tree);
            } else {
                this.trees = Collections.emptyIterator();
            }
        }

        @Override
        protected Node computeNext() {
            if (trees.hasNext()) {
                return trees.next();
            }
            return endOfData();
        }
    }

    /**
     * Returns all direct children of a buckets tree
     */
    private class Buckets extends AbstractIterator<Node> {

        private Iterator<Bucket> buckets;

        private Iterator<Node> bucketEntries;

        public Buckets(RevTree tree) {
            Preconditions.checkArgument(tree.bucketsSize() > 0);
            buckets = Iterators.filter(tree.getBuckets().iterator(), boundsFilter);
            bucketEntries = Collections.emptyIterator();
            // may it be a mixed tree (having both direct children and buckets)
            bucketEntries = RevObjects.children(tree, CanonicalNodeOrder.INSTANCE);
        }

        @Override
        protected Node computeNext() {
            while (!bucketEntries.hasNext()) {
                if (buckets.hasNext()) {
                    Bucket nextBucket = buckets.next();
                    bucketEntries = resolveBucketEntries(nextBucket.getObjectId());
                } else {
                    return endOfData();
                }
            }
            return bucketEntries.next();
        }

        /**
         * @param bucketId
         * @return
         */
        protected Iterator<Node> resolveBucketEntries(ObjectId bucketId) {
            RevTree bucketTree = source.getTree(bucketId);
            if (bucketTree.bucketsSize() > 0) {
                return new Buckets(bucketTree);
            }
            return new Children(bucketTree);
        }
    }

    /**
     * Returns all direct children of a buckets tree of type TREE
     */
    private class TreeBuckets extends Buckets {

        public TreeBuckets(RevTree tree) {
            super(tree);
        }

        @Override
        protected Iterator<Node> resolveBucketEntries(ObjectId bucketId) {
            RevTree bucketTree = source.getTree(bucketId);
            if (bucketTree.numTrees() == 0) {
                return Collections.emptyIterator();
            }
            if (bucketTree.treesSize() > 0) {
                return new Trees(bucketTree);
            }
            if (bucketTree.bucketsSize() > 0) {
                return new TreeBuckets(bucketTree);
            }
            return Collections.emptyIterator();
        }
    }

    /**
     * Returns all direct children of a buckets tree of type FEATURE
     */
    private class FeatureBuckets extends Buckets {

        public FeatureBuckets(RevTree tree) {
            super(tree);
        }

        @Override
        protected Iterator<Node> resolveBucketEntries(ObjectId bucketId) {
            RevTree bucketTree = source.getTree(bucketId);
            if (bucketTree.bucketsSize() > 0) {
                return new FeatureBuckets(bucketTree);
            }
            if (bucketTree.featuresSize() > 0) {
                return new Features(bucketTree);
            }
            return Collections.emptyIterator();
        }
    }
}
