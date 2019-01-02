/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.index;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterators.singletonIterator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.AbstractConsumer;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;

class MaterializedBuilderConsumer extends AbstractConsumer {

    private final int batchSize = 1000;

    private static class Tuple implements Iterable<Node> {
        final Node left, right;

        public Tuple(@Nullable NodeRef left, @Nullable NodeRef right) {
            this.left = left == null ? null : left.getNode();
            this.right = right == null ? null : right.getNode();
        }

        @Override
        public Iterator<Node> iterator() {
            return left == null ? singletonIterator(right)
                    : (right == null ? singletonIterator(left) : Iterators.forArray(left, right));
        }
    }

    private BlockingQueue<MaterializedBuilderConsumer.Tuple> nodes = new ArrayBlockingQueue<>(
            batchSize);

    final AtomicLong count = new AtomicLong();

    final RevTreeBuilder builder;

    final ProgressListener progress;

    final ObjectStore featureSource;

    final Map<String, Integer> extraDataProperties;

    MaterializedBuilderConsumer(RevTreeBuilder builder, ObjectStore featureSource,
            Map<String, Integer> extraDataProperties, ProgressListener listener) {
        this.builder = builder;
        this.featureSource = featureSource;
        this.extraDataProperties = extraDataProperties;
        this.progress = listener;
    }

    @Override
    public boolean tree(@Nullable NodeRef left, @Nullable NodeRef right) {
        return !progress.isCanceled();
    }

    @Override
    public boolean bucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
            @Nullable Bucket left, @Nullable Bucket right) {
        return !progress.isCanceled();
    }

    @Override
    public void endTree(@Nullable NodeRef left, @Nullable NodeRef right) {
        if (NodeRef.ROOT.equals(right.name())) {
            addAll();
        }
    }

    @Override
    public boolean feature(final @Nullable NodeRef left, final NodeRef right) {
        while (!nodes.offer(new Tuple(left, right))) {
            addAll();
        }
        progress.setProgress(count.incrementAndGet());

        final boolean keepGoing = !progress.isCanceled();
        return keepGoing;
    }

    private void addAll() {
        List<MaterializedBuilderConsumer.Tuple> list = new ArrayList<>(batchSize);
        nodes.drainTo(list);

        final Map<ObjectId, RevFeature> objects = new HashMap<>();
        {
            Iterable<Node> allNodes = Iterables.concat(list);

            //Node::getObjectId, but friendly for Fortify
            Function<Node, ObjectId> fn_Node_getObjectId =  new Function<Node, ObjectId>() {
                @Override
                public ObjectId apply(Node node) {
                    return node.getObjectId();
                }};

            Iterable<ObjectId> nodeIds = Iterables.transform(allNodes, fn_Node_getObjectId);
            Iterator<RevFeature> objectsIt = featureSource.getAll(nodeIds,
                    BulkOpListener.NOOP_LISTENER, RevFeature.class);
            objectsIt.forEachRemaining((o) -> objects.put(o.getId(), o));
        }

        for (MaterializedBuilderConsumer.Tuple t : list) {
            @Nullable
            Node left = materialize(t.left, objects);
            @Nullable
            Node right = materialize(t.right, objects);
            if (left == null) {
                // if (!right.getExtraData().isEmpty()) {
                // System.err.printf(">>>> PUT: %s (%s)\n", right.getName(), right.getExtraData());
                // }
                boolean put = builder.put(right);
                checkState(put, "Node was not added to index: %s", right);
            } else if (right == null) {
                // if (!left.getExtraData().isEmpty()) {
                // System.err.printf("<<<< REMOVE: %s (%s)\n", left.getName(),
                // left.getExtraData());
                // }
                boolean removed = builder.remove(left);
                checkState(removed, "Node was not removed from index: %s", left);
            } else {
                // if (!right.getExtraData().isEmpty() || !left.getExtraData().isEmpty()) {
                // System.err.printf("<<>>> UPDATE: %s (%s)->(%s)\n", right.getName(),
                // left.getExtraData(), right.getExtraData());
                // }
                boolean updated = builder.update(left, right);
                if (!left.equals(right)) {
                    checkState(updated, "Node %s was not updated to %s", left, right);
                }
            }
        }
    }

    private @Nullable Node materialize(@Nullable Node node, Map<ObjectId, RevFeature> objects) {
        Node materialized = null;
        if (null != node) {
            ObjectId objectId = node.getObjectId();
            RevFeature f = objects.get(objectId);
            Preconditions.checkState(f != null, "Feature %s of node '%s' not found", objectId,
                    node.getName());
            Map<String, Object> atts = new HashMap<>();
            extraDataProperties.forEach((attName, attIndex) -> {
                Optional<Object> value = f.get(attIndex.intValue());
                atts.put(attName, value.orNull());
            });

            Map<String, Object> extraData = new HashMap<>(node.getExtraData());

            extraData.put(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA, atts);

            String name = node.getName();
            ObjectId metadataId = node.getMetadataId().or(ObjectId.NULL);
            TYPE type = node.getType();
            Envelope orNull = node.bounds().orNull();
            materialized = RevObjectFactory.defaultInstance().createNode(name, objectId, metadataId,
                    type, orNull, extraData);
        }
        return materialized;
    }
}