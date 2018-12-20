/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.Streams;

/**
 * Copies the {@link #setObjectRef(Supplier) specified object} from the
 * {@link #setFrom(ObjectDatabase) provided} object database this command's repository
 * {@link ObjectDatabase object database}, including any child reference.
 */
public class DeepCopy extends AbstractGeoGigOp<ObjectId> {

    private Supplier<Node> objectRef;

    private Supplier<ObjectId> objectId;

    private Supplier<Iterator<Node>> nodesToCopy;

    private ObjectStore from;

    /**
     * @param id the id of the object to move, mutually exclusive with
     *        {@link #setObjectRef(Supplier)}
     * @return
     */
    public DeepCopy setObject(Supplier<ObjectId> id) {
        this.objectId = id;
        this.objectRef = null;
        this.nodesToCopy = null;
        return this;
    }

    /**
     * @param objectRef the object to move from the origin database to the destination one, mutually
     *        exclusive with {@link #setObject(Supplier)}
     * @return {@code this}
     */
    public DeepCopy setObjectRef(Supplier<Node> objectRef) {
        this.objectRef = objectRef;
        this.objectId = null;
        this.nodesToCopy = null;
        return this;
    }

    public DeepCopy setObjects(Supplier<Iterator<Node>> nodesToMove) {
        this.nodesToCopy = nodesToMove;
        this.objectId = null;
        this.objectRef = null;
        return this;
    }

    /**
     * Executes a deep copy using the supplied {@link Node}.
     * 
     * @return the {@link ObjectId} of the moved object, or {@code null} if {@link #setObjects} was
     *         used and hence no such information it available
     */
    @Override
    protected ObjectId _call() {
        Preconditions.checkState(from != null, "No from database specified");
        ObjectStore from = this.from;
        ObjectStore to = objectDatabase();

        Set<ObjectId> metadataIds = new HashSet<ObjectId>();

        final ObjectId ret;
        if (objectRef != null) {
            Node ref = objectRef.get();
            ret = ref.getObjectId();
            deepCopy(ref, from, to, metadataIds);
        } else if (objectId != null) {
            ObjectId id = objectId.get();
            copyObject(id, from, to);
            ret = id;
        } else if (nodesToCopy != null) {
            copyObjects(from, to, nodesToCopy, metadataIds);
            ret = null;
        } else {
            throw new IllegalStateException("No object supplied to be moved");
        }

        for (ObjectId metadataId : metadataIds) {
            copyObject(metadataId, from, to);
        }

        return ret;
    }

    private void copyObjects(final ObjectStore from, final ObjectStore to,
            final Supplier<Iterator<Node>> nodesToMove, final Set<ObjectId> metadataIds) {

        Iterable<ObjectId> ids = new Iterable<ObjectId>() {

            final Function<Node, ObjectId> asId = (node) -> {

                Optional<ObjectId> metadataId = node.getMetadataId();
                if (metadataId.isPresent()) {
                    metadataIds.add(node.getMetadataId().get());
                }
                ObjectId id = node.getObjectId();
                return id;
            };

            @Override
            public Iterator<ObjectId> iterator() {
                Iterator<Node> iterator = nodesToMove.get();
                Iterator<ObjectId> ids = Iterators.transform(iterator, asId);

                return ids;
            }
        };

        // store objects into the target db and remove them from the origin db in one shot
        to.putAll(from.getAll(ids));
    }

    /**
     * Transfers the object referenced by {@code objectRef} from the given object database to the
     * given objectInserter as well as any child object if {@code objectRef} references a tree.
     */
    private void deepCopy(final Node objectRef, final ObjectStore from, final ObjectStore to,
            Set<ObjectId> metadataIds) {

        if (objectRef.getMetadataId().isPresent()) {
            metadataIds.add(objectRef.getMetadataId().get());
        }

        final ObjectId objectId = objectRef.getObjectId();
        if (TYPE.TREE.equals(objectRef.getType())) {
            copyTree(objectId, from, to, metadataIds);
        } else {
            copyObject(objectId, from, to);
        }
    }

    private void copyTree(final ObjectId treeId, final ObjectStore from, final ObjectStore to,
            final Set<ObjectId> metadataIds) {
        if (to.exists(treeId)) {
            return;
        }
        Supplier<Iterator<NodeRef>> refs = command(LsTreeOp.class).setReference(treeId.toString())
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES);

        //        Supplier<Iterator<Node>> nodes = Suppliers.compose(//
        //                it -> Iterators.transform(it, NodeRef::getNode)//
        //                , refs);

        Iterator<Node> ns = Streams.stream(refs.get()).map(NodeRef::getNode).iterator();

        Supplier<Iterator<Node>> nodes = Suppliers.ofInstance(ns);

        // move all features, recursively as given by the LsTreeOp strategy
        copyObjects(from, to, nodes, metadataIds);

        // iterator that traverses the tree,all its subtrees, an bucket trees
        Iterator<RevTree> allSubtreesAndBuckets = new AllTrees(treeId, from);

        to.putAll(allSubtreesAndBuckets);
    }

    private static class AllTrees extends AbstractIterator<RevTree> {

        private RevTree tree;

        private ObjectStore from;

        private Iterator<Node> trees;

        private Iterator<Bucket> buckets;

        private Iterator<RevTree> bucketTrees;

        public AllTrees(ObjectId id, ObjectStore from) {
            this.from = from;
            this.tree = from.getTree(id);
            this.trees = tree.trees().iterator();
            buckets = tree.getBuckets().iterator();

            bucketTrees = Collections.emptyIterator();
        }

        @Override
        protected RevTree computeNext() {
            if (tree != null) {
                RevTree ret = tree;
                tree = null;
                return ret;
            }
            if (trees.hasNext()) {
                ObjectId objectId = trees.next().getObjectId();
                return from.getTree(objectId);
            }
            if (bucketTrees.hasNext()) {
                return bucketTrees.next();
            }
            if (buckets.hasNext()) {
                bucketTrees = new AllTrees(buckets.next().getObjectId(), from);
                return computeNext();
            }
            return endOfData();
        }

    }

    private void copyObject(RevObject object, ObjectStore from, ObjectStore to) {
        to.put(object);
    }

    private void copyObject(final ObjectId objectId, final ObjectStore from, final ObjectStore to) {

        RevObject object = from.get(objectId);

        if (object instanceof RevTree) {
            Set<ObjectId> metadataIds = new HashSet<ObjectId>();
            copyTree(object.getId(), from, to, metadataIds);
            for (ObjectId metadataId : metadataIds) {
                copyObject(metadataId, from, to);
            }
        } else {
            copyObject(object, from, to);
        }
    }

    public DeepCopy setFrom(ObjectStore odb) {
        this.from = odb;
        return this;
    }

}
