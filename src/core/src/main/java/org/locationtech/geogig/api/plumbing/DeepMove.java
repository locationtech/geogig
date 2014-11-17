/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Moves the {@link #setObjectRef(Supplier) specified object} from the {@link StagingArea index
 * database} to the permanent {@link ObjectDatabase object database}, including any child reference,
 * or from the repository database to the index database if {@link #setToIndex} is set to
 * {@code true}.
 */
public class DeepMove extends AbstractGeoGigOp<ObjectId> {

    private boolean toIndex;

    private Supplier<Node> objectRef;

    private Supplier<ObjectId> objectId;

    private Supplier<Iterator<Node>> nodesToMove;

    private ObjectDatabase odb;

    
    /**
     * @param toIndex if {@code true} moves the object from the repository's object database to the
     *        index database instead
     * @return {@code this}
     */
    public DeepMove setToIndex(boolean toIndex) {
        this.toIndex = toIndex;
        return this;
    }

    /**
     * @param id the id of the object to move, mutually exclusive with
     *        {@link #setObjectRef(Supplier)}
     * @return
     */
    public DeepMove setObject(Supplier<ObjectId> id) {
        this.objectId = id;
        this.objectRef = null;
        this.nodesToMove = null;
        return this;
    }

    /**
     * @param objectRef the object to move from the origin database to the destination one, mutually
     *        exclusive with {@link #setObject(Supplier)}
     * @return {@code this}
     */
    public DeepMove setObjectRef(Supplier<Node> objectRef) {
        this.objectRef = objectRef;
        this.objectId = null;
        this.nodesToMove = null;
        return this;
    }

    public DeepMove setObjects(Supplier<Iterator<Node>> nodesToMove) {
        this.nodesToMove = nodesToMove;
        this.objectId = null;
        this.objectRef = null;
        return this;
    }

    /**
     * Executes a deep move using the supplied {@link Node}.
     * 
     * @return the {@link ObjectId} of the moved object, or {@code null} if {@link #setObjects} was
     *         used and hence no such information it available
     */
    @Override
    protected  ObjectId _call() {
        ObjectDatabase from = toIndex ? objectDatabase() : stagingDatabase();
        ObjectDatabase to = toIndex ? stagingDatabase() : objectDatabase();

        Set<ObjectId> metadataIds = new HashSet<ObjectId>();

        final ObjectId ret;
        if (objectRef != null) {
            Node ref = objectRef.get();
            ret = ref.getObjectId();
            deepMove(ref, from, to, metadataIds);
        } else if (objectId != null) {
            ObjectId id = objectId.get();
            moveObject(id, from, to);
            ret = id;
        } else if (nodesToMove != null) {
            moveObjects(from, to, nodesToMove, metadataIds);
            ret = null;
        } else {
            throw new IllegalStateException("No object supplied to be moved");
        }

        for (ObjectId metadataId : metadataIds) {
            moveObject(metadataId, from, to);
        }

        return ret;
    }

    private static class DeleteTask implements Runnable {
        private List<ObjectId> ids;

        private ObjectDatabase db;

        public DeleteTask(List<ObjectId> ids, ObjectDatabase db) {
            this.ids = ids;
            this.db = db;
        }

        @Override
        public void run() {
            db.deleteAll(ids.iterator());
            ids.clear();
            ids = null;
        }
    }

    private static class DeletingListener extends BulkOpListener {

        private final int limit = 1000 * 10;

        final List<ObjectId> removeIds = Lists.newArrayListWithCapacity(limit);

        private ExecutorService deletingService;

        private ObjectDatabase db;

        public DeletingListener(ExecutorService deletingService, ObjectDatabase db) {
            this.deletingService = deletingService;
            this.db = db;
        }

        @Override
        public synchronized void inserted(ObjectId object, @Nullable Integer storageSizeBytes) {
            removeIds.add(object);
            if (removeIds.size() == limit) {
                deleteInserted();
            }
        }

        public void deleteInserted() {
            if (!removeIds.isEmpty()) {
                DeleteTask deleteTask = new DeleteTask(Lists.newArrayList(removeIds), db);
                deletingService.execute(deleteTask);
                removeIds.clear();
            }
        }
    }

    private void moveObjects(final ObjectDatabase from, final ObjectDatabase to,
            final Supplier<Iterator<Node>> nodesToMove, final Set<ObjectId> metadataIds) {

        Iterable<ObjectId> ids = new Iterable<ObjectId>() {

            final Function<Node, ObjectId> asId = new Function<Node, ObjectId>() {
                @Override
                public ObjectId apply(Node input) {
                    Optional<ObjectId> metadataId = input.getMetadataId();
                    if (metadataId.isPresent()) {
                        metadataIds.add(input.getMetadataId().get());
                    }
                    ObjectId id = input.getObjectId();
                    return id;
                }
            };

            @Override
            public Iterator<ObjectId> iterator() {
                Iterator<Node> iterator = nodesToMove.get();
                Iterator<ObjectId> ids = Iterators.transform(iterator, asId);

                return ids;
            }
        };

        final ExecutorService deletingService = Executors.newSingleThreadExecutor();
        try {
            final DeletingListener deletingListener = new DeletingListener(deletingService, from);

            // store objects into the target db and remove them from the origin db in one shot
            to.putAll(from.getAll(ids), deletingListener);
            // in case there are some deletes pending cause the iterator finished and the listener
            // didn't fill its buffer
            deletingListener.deleteInserted();

        } finally {
            deletingService.shutdown();
            while (!deletingService.isTerminated()) {
                try {
                    deletingService.awaitTermination(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // ok, still awaiting for delete tasks to finish
                }
            }
        }
    }

    /**
     * Transfers the object referenced by {@code objectRef} from the given object database to the
     * given objectInserter as well as any child object if {@code objectRef} references a tree.
     */
    private void deepMove(final Node objectRef, final ObjectDatabase from, final ObjectDatabase to,
            Set<ObjectId> metadataIds) {

        if (objectRef.getMetadataId().isPresent()) {
            metadataIds.add(objectRef.getMetadataId().get());
        }

        final ObjectId objectId = objectRef.getObjectId();
        if (TYPE.TREE.equals(objectRef.getType())) {
            moveTree(objectId, from, to, metadataIds);
        } else {
            moveObject(objectId, from, to);
        }
    }

    private void moveTree(final ObjectId treeId, final ObjectDatabase from,
            final ObjectDatabase to, final Set<ObjectId> metadataIds) {

        Supplier<Iterator<NodeRef>> refs = command(LsTreeOp.class).setReference(treeId.toString())
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES);

        Supplier<Iterator<Node>> nodes = Suppliers.compose(
                new Function<Iterator<NodeRef>, Iterator<Node>>() {

                    @Override
                    public Iterator<Node> apply(Iterator<NodeRef> input) {
                        return Iterators.transform(input, new Function<NodeRef, Node>() {
                            @Override
                            public Node apply(NodeRef input) {
                                return input.getNode();
                            }
                        });
                    }
                }, refs);

        // move all features, recursively as given by the LsTreeOp strategy
        moveObjects(from, to, nodes, metadataIds);

        // collect all subtree and bucket ids here to delete them from the origin db
        final Set<ObjectId> alltreeIds = Sets.newTreeSet();
        Predicate<RevTree> collectIds = new Predicate<RevTree>() {
            @Override
            public boolean apply(RevTree input) {
                alltreeIds.add(input.getId());
                return true;
            }
        };

        // iterator that traverses the tree,all its subtrees, an bucket trees
        Iterator<RevTree> allSubtreesAndBuckets = new AllTrees(treeId, from);
        allSubtreesAndBuckets = Iterators.filter(allSubtreesAndBuckets, collectIds);

        to.putAll(allSubtreesAndBuckets);
        from.deleteAll(alltreeIds.iterator());
    }

    private static class AllTrees extends AbstractIterator<RevTree> {

        private RevTree tree;

        private ObjectDatabase from;

        private Iterator<Node> trees;

        private Iterator<Bucket> buckets;

        private Iterator<RevTree> bucketTrees;

        public AllTrees(ObjectId id, ObjectDatabase from) {
            this.from = from;
            this.tree = from.getTree(id);
            this.trees = Iterators.emptyIterator();
            if (tree.trees().isPresent()) {
                trees = tree.trees().get().iterator();
            }
            buckets = Iterators.emptyIterator();
            if (tree.buckets().isPresent()) {
                buckets = tree.buckets().get().values().iterator();
            }
            bucketTrees = Iterators.emptyIterator();
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
                bucketTrees = new AllTrees(buckets.next().id(), from);
                return computeNext();
            }
            return endOfData();
        }

    }

    private void moveObject(RevObject object, ObjectDatabase from, ObjectDatabase to) {
        to.put(object);
        from.delete(object.getId());
    }

    private void moveObject(final ObjectId objectId, final ObjectDatabase from,
            final ObjectDatabase to) {

        RevObject object = from.get(objectId);

        if (object instanceof RevTree) {
            Set<ObjectId> metadataIds = new HashSet<ObjectId>();
            moveTree(object.getId(), from, to, metadataIds);
            for (ObjectId metadataId : metadataIds) {
                moveObject(metadataId, from, to);
            }
        } else {
            moveObject(object, from, to);
        }
    }

    @Override
    protected ObjectDatabase objectDatabase() {
        return this.odb == null ? super.objectDatabase() : this.odb;
    }
    
    public DeepMove setFrom(ObjectDatabase odb) {
        this.odb = odb;
        return this;
    }

}
