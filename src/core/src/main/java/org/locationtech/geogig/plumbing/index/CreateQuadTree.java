package org.locationtech.geogig.plumbing.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.QuadTreeBuilder;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.AbstractConsumer;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

/**
 * Creates a {@link RevTree} that represents a quad-tree out of an existing canonical
 * {@link RevTree}.
 *
 */
public class CreateQuadTree extends AbstractGeoGigOp<RevTree> {

    private Envelope maxBounds = wgs84Bounds();

    private ObjectId treeId;

    private static final Envelope wgs84Bounds() {
        return new Envelope(-180, 180, -90, 90);
    }

    /**
     * @param maxBounds Optional; the quad-tree max bounds, default to WGS84 bounds if not set
     */
    public CreateQuadTree setMaxBounds(Envelope maxBounds) {
        this.maxBounds = new Envelope(maxBounds);
        return this;
    }

    /**
     * @param treeId Mandatory; the feature tree for which to create the spatial index tree.
     */
    public CreateQuadTree setFeatureTree(ObjectId treeId) {
        this.treeId = treeId;
        return this;
    }

    @Override
    protected RevTree _call() {
        Preconditions.checkArgument(treeId != null, "FeatureTree not provided");

        final ObjectDatabase odb = objectDatabase();

        final RevTree tree = odb.getTree(treeId);
        final long size = tree.size();
        int maxDepth = 1;

        int numLeafTrees = 4;
        while (numLeafTrees * 64 < size) {
            maxDepth++;
            numLeafTrees = numLeafTrees * 4;
        }
        maxDepth = 16;
        System.err.println("Setting max depth = " + maxDepth);

        PreOrderDiffWalk walk = new PreOrderDiffWalk(RevTree.EMPTY, tree, odb, odb);

        final ProgressListener progress = getProgressListener();

        final QuadTreeBuilder builder = QuadTreeBuilder.quadTree(odb, RevTree.EMPTY, maxBounds,
                maxDepth);

        progress.setDescription(String.format("Creating Quad Tree for %,d features", tree.size()));

        Consumer consumer;
        // consumer= new SimpleQuadTreeBuilderConsumer(builder, odb, progress);
        consumer = new MaterializedQuadTreeBuilderConsumer(builder, odb, progress);

        walk.walk(consumer);

        if (progress.isCanceled()) {
            return null;
        }

        progress.setDescription("Building final tree...");

        int depth = builder.getDepth();
        RevTree quadTree;
        try {
            quadTree = builder.build();
        } catch (Exception e) {
            e.printStackTrace();
            throw Throwables.propagate(Throwables.getRootCause(e));
        }
        progress.setDescription(
                String.format("QuadTree created. Size: %,d, depth: %d", quadTree.size(), depth));

        progress.complete();

        return quadTree;
    }

    private static class SimpleQuadTreeBuilderConsumer extends AbstractConsumer
            implements Consumer {

        protected final AtomicLong count = new AtomicLong();

        protected final QuadTreeBuilder builder;

        protected final ProgressListener progress;

        protected ObjectStore source;

        SimpleQuadTreeBuilderConsumer(QuadTreeBuilder builder, ObjectStore odb,
                ProgressListener listener) {
            this.builder = builder;
            this.source = odb;
            this.progress = listener;
        }

        @Override
        public boolean tree(@Nullable NodeRef left, @Nullable NodeRef right) {
            boolean isSpatial = right.bounds().isPresent();
            return !progress.isCanceled() && isSpatial;
        }

        @Override
        public boolean bucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                @Nullable Bucket left, @Nullable Bucket right) {
            boolean isSpatial = right.bounds().isPresent();
            return !progress.isCanceled() && isSpatial;
        }

        @Override
        public synchronized boolean feature(final @Nullable NodeRef left,
                final NodeRef featureNode) {
            final Optional<Envelope> bounds = featureNode.bounds();
            Node node = featureNode.getNode();
            if (bounds.isPresent()) {
                builder.put(node);
                progress.setProgress(count.incrementAndGet());
            }
            final boolean keepGoing = !progress.isCanceled();
            return keepGoing;
        }

    };

    private static class MaterializedQuadTreeBuilderConsumer extends SimpleQuadTreeBuilderConsumer {

        private final int batchSize = 1000;

        private BlockingQueue<Node> nodes = new ArrayBlockingQueue<>(batchSize);

        MaterializedQuadTreeBuilderConsumer(QuadTreeBuilder builder, ObjectStore odb,
                ProgressListener listener) {
            super(builder, odb, listener);
        }

        @Override
        public void endTree(@Nullable NodeRef left, @Nullable NodeRef right) {
            if (NodeRef.ROOT.equals(right.name())) {
                addAll();
            }
        }

        @Override
        public boolean feature(final @Nullable NodeRef left, final NodeRef featureNode) {
            Node node = featureNode.getNode();
            while (!nodes.offer(node)) {
                addAll();
            }
            progress.setProgress(count.incrementAndGet());

            final boolean keepGoing = !progress.isCanceled();
            return keepGoing;
        }

        private void addAll() {
            List<Node> list = new ArrayList<>(batchSize);
            nodes.drainTo(list);

            Iterator<RevFeature> objectsIt = source.getAll(
                    Lists.transform(list, (n) -> n.getObjectId()), BulkOpListener.NOOP_LISTENER,
                    RevFeature.class);

            ImmutableMap<ObjectId, RevFeature> objects = Maps.uniqueIndex(objectsIt,
                    (o) -> o.getId());

            for (Node featureNode : list) {
                final Optional<Envelope> bounds = featureNode.bounds();
                if (bounds.isPresent()) {
                    Envelope envelope = bounds.get();
                    boolean isPoint = envelope.getWidth() == 0 && envelope.getHeight() == 0;
                    if (!isPoint) {
                        ObjectId oid = featureNode.getObjectId();
                        RevFeature feature = objects.get(oid);
                        Geometry geometry = toGeometry.apply(feature);
                        Map<String, Object> extraData = new HashMap<String, Object>();
                        extraData.put("geometry", geometry);

                        String name = featureNode.getName();
                        ObjectId metadataId = featureNode.getMetadataId().or(ObjectId.NULL);
                        featureNode = Node.create(name, oid, metadataId, TYPE.FEATURE, envelope,
                                extraData);
                    }
                    synchronized (builder) {
                        builder.put(featureNode);
                    }
                }
            }

            // addAll(list);
        }

        private void addAll(List<Node> nodes) {
            ImmutableMap<ObjectId, Node> map = Maps.uniqueIndex(nodes, nodeId);

            Iterator<RevObject> features = source.getAll(map.keySet());

            ImmutableMap<ObjectId, RevObject> objects = Maps.uniqueIndex(features, objId);

            Map<ObjectId, Geometry> geometries = Maps.transformValues(objects, toGeometry);

            ObjectId oid;
            Geometry geom;
            Node gn;
            Map<String, Object> extraData;
            for (Node node : nodes) {
                oid = node.getObjectId();
                geom = geometries.get(oid);
                if (geom != null) {
                    extraData = ImmutableMap.of("geometry", (Object) geom);
                    gn = Node.create(node.getName(), oid, node.getMetadataId().or(ObjectId.NULL),
                            node.getType(), node.bounds().get(), extraData);
                    builder.put(gn);
                }
            }
        }

        private Function<Node, ObjectId> nodeId = new Function<Node, ObjectId>() {
            @Override
            public ObjectId apply(Node n) {
                return n.getObjectId();
            }
        };

        private Function<RevObject, ObjectId> objId = new Function<RevObject, ObjectId>() {
            @Override
            public ObjectId apply(RevObject n) {
                return n.getId();
            }
        };

        private Function<RevObject, Geometry> toGeometry = new Function<RevObject, Geometry>() {
            @Override
            public Geometry apply(RevObject input) {
                RevFeature f = (RevFeature) input;
                AtomicReference<Geometry> geom = new AtomicReference<>();
                f.forEach((v) -> {
                    if (v instanceof Geometry) {
                        geom.set((Geometry) v);
                    }
                });

                return geom.get();
            }
        };

    };

}
