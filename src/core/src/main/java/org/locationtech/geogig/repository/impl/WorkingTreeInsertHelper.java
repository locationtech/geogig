/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.FindOrCreateSubtree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.jts.geom.Envelope;

import com.google.common.collect.Maps;

class WorkingTreeInsertHelper {

    private static final Function<Feature, String> SIMPLE_PATH_RESOLVER = (f) -> {
        return f.getType().getName().getLocalPart();
    };

    private final ObjectDatabase db;

    private final Context context;

    private final RevTree workHead;

    private Function<Feature, String> treePathResolver;

    private final Map<String, RevFeatureType> revFeatureTypes = Maps.newConcurrentMap();

    private final Map<String, RevTreeBuilder> treeBuilders = Maps.newHashMap();

    private final ExecutorService executorService;

    public WorkingTreeInsertHelper(Context context, RevTree tree,
            ExecutorService treeBuildingService) {
        this(context, tree, SIMPLE_PATH_RESOLVER, treeBuildingService);
    }

    public WorkingTreeInsertHelper(Context context, RevTree workHead,
            final Function<Feature, String> treePathResolver,
            final ExecutorService treeBuildingService) {

        this.db = context.objectDatabase();
        this.context = context;
        this.workHead = workHead;
        this.treePathResolver = treePathResolver;
        this.executorService = treeBuildingService;
    }

    public List<String> getTreeNames() {
        return new ArrayList<>(treeBuilders.keySet());
    }

    public Node put(final ObjectId revFeatureId, final Feature feature) {

        final RevTreeBuilder treeBuilder = getTreeBuilder(feature);

        String fid = feature.getId();
        // System.err.printf("%s -> %s\n", fid, treeBuilder);
        Envelope bounds = feature.getDefaultGeometryBounds();
        FeatureType type = feature.getType();

        final Node node = createFeatureNode(revFeatureId, fid, bounds, type);
        treeBuilder.put(node);
        return node;
    }

    private Node createFeatureNode(final ObjectId id, final String name,
            @Nullable final Envelope bbox, final FeatureType type) {

        RevFeatureType revFeatureType = revFeatureTypes.get(type.getName().getLocalPart());
        if (null == revFeatureType) {
            revFeatureType = RevFeatureType.builder().type(type).build();
            revFeatureTypes.put(type.getName().getLocalPart(), revFeatureType);
        }
        ObjectId defaultMetadataId = revFeatureType.getId();
        ObjectId metadataId = revFeatureType.getId().equals(defaultMetadataId) ? ObjectId.NULL
                : revFeatureType.getId();
        Node node = RevObjectFactory.defaultInstance().createNode(name, id, metadataId,
                TYPE.FEATURE, bbox, null);
        return node;
    }

    public void remove(FeatureToDelete feature) {
        final RevTreeBuilder treeBuilder = getTreeBuilder(feature);
        String fid = feature.getId();
        Node featureNode = RevObjectFactory.defaultInstance().createNode(fid, ObjectId.NULL,
                ObjectId.NULL, TYPE.FEATURE, null, null);
        treeBuilder.remove(featureNode);
    }

    public void remove(String featurePath) {
        final String treePath = NodeRef.parentPath(featurePath);
        final String featureId = NodeRef.nodeFromPath(featurePath);
        Optional<RevTreeBuilder> treeBuilder = getTreeBuilder(treePath);
        if (treeBuilder.isPresent()) {
            RevTreeBuilder builder = treeBuilder.get();
            Node featureNode = RevObjectFactory.defaultInstance().createNode(featureId,
                    ObjectId.NULL, ObjectId.NULL, TYPE.FEATURE, null, null);
            builder.remove(featureNode);
        }
    }

    private Optional<RevTreeBuilder> getTreeBuilder(final String treePath) {
        RevTreeBuilder builder = treeBuilders.get(treePath);
        if (builder == null) {
            Optional<NodeRef> treeNode = context.command(FindTreeChild.class).setParent(workHead)
                    .setChildPath(treePath).call();
            if (treeNode.isPresent()) {
                RevTree parentTree = db.getTree(treeNode.get().getObjectId());
                ObjectId metadataId = treeNode.get().getMetadataId();
                if (!metadataId.isNull()) {
                    RevFeatureType featureType = db.getFeatureType(metadataId);
                    revFeatureTypes.put(treePath, featureType);
                }
                builder = createBuilder(parentTree);
                treeBuilders.put(treePath, builder);
            }
        }
        return Optional.ofNullable(builder);
    }

    private RevTreeBuilder getTreeBuilder(final Feature feature) {

        final String treePath = treePathResolver.apply(feature);
        RevTreeBuilder builder = getTreeBuilder(treePath).orElse(null);

        if (builder == null) {
            final FeatureType type = feature.getType();
            final NodeRef treeRef = findOrCreateTree(treePath, type);
            final ObjectId treeId = treeRef.getObjectId();
            final RevTree origTree = db.getTree(treeId);
            final ObjectId defaultMetadataId = treeRef.getMetadataId();
            if (!defaultMetadataId.isNull()) {
                RevFeatureType featureType = db.getFeatureType(defaultMetadataId);
                revFeatureTypes.put(treePath, featureType);
            }
            builder = createBuilder(origTree);
            treeBuilders.put(treePath, builder);
        }

        return builder;
    }

    private NodeRef findOrCreateTree(final String treePath, final FeatureType type) {

        RevTree tree = context.command(FindOrCreateSubtree.class).setChildPath(treePath)
                .setParent(workHead).setParentPath(NodeRef.ROOT).call();

        ObjectId metadataId = ObjectId.NULL;
        if (type != null) {
            RevFeatureType revFeatureType = RevFeatureType.builder().type(type).build();
            if (tree.isEmpty()) {
                db.put(revFeatureType);
            }
            metadataId = revFeatureType.getId();
        }
        Envelope bounds = SpatialOps.boundsOf(tree);
        Node node = RevObjectFactory.defaultInstance().createNode(NodeRef.nodeFromPath(treePath),
                tree.getId(), metadataId, TYPE.TREE, bounds, null);

        String parentPath = NodeRef.parentPath(treePath);
        return new NodeRef(node, parentPath, ObjectId.NULL);
    }

    private RevTreeBuilder createBuilder(final RevTree origTree) {
        return RevTreeBuilder.builder(db, origTree);
    }

    public Map<NodeRef, RevTree> buildTrees() {

        final Map<NodeRef, RevTree> builtTrees = new ConcurrentHashMap<>();

        List<AsyncBuildTree> tasks = new ArrayList<>();

        for (Entry<String, RevTreeBuilder> builderEntry : treeBuilders.entrySet()) {
            final String treePath = builderEntry.getKey();
            final RevTreeBuilder builder = builderEntry.getValue();
            final RevFeatureType revFeatureType = revFeatureTypes.get(treePath);
            final ObjectId metadataId = revFeatureType.getId();
            tasks.add(new AsyncBuildTree(treePath, builder, metadataId, builtTrees));
        }
        try {
            executorService.invokeAll(tasks);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        db.putAll(builtTrees.values().iterator());
        return builtTrees;
    }

    private class AsyncBuildTree implements Callable<Void> {

        private String treePath;

        private RevTreeBuilder builder;

        private Map<NodeRef, RevTree> target;

        private ObjectId defaultMetadataId;

        AsyncBuildTree(final String treePath, final RevTreeBuilder builder,
                final ObjectId defaultMetadataId, final Map<NodeRef, RevTree> target) {

            this.treePath = treePath;
            this.builder = builder;
            this.defaultMetadataId = defaultMetadataId;
            this.target = target;
        }

        public @Override Void call() {
            RevTree tree = builder.build();

            Node treeNode;
            {
                ObjectId treeMetadataId = defaultMetadataId;
                String name = NodeRef.nodeFromPath(treePath);
                ObjectId oid = tree.getId();
                Envelope bounds = SpatialOps.boundsOf(tree);
                treeNode = RevObjectFactory.defaultInstance().createNode(name, oid, treeMetadataId,
                        RevObject.TYPE.TREE, bounds, null);
            }

            final String parentPath = NodeRef.parentPath(treePath);
            final ObjectId parentMetadataId;
            if (NodeRef.ROOT.equals(parentPath)) {
                parentMetadataId = ObjectId.NULL;
            } else {
                Optional<NodeRef> parentRef = context.command(FindTreeChild.class)
                        .setChildPath(parentPath).setParent(workHead).setParentPath(NodeRef.ROOT)
                        .call();

                parentMetadataId = parentRef.isPresent() ? parentRef.get().getMetadataId()
                        : ObjectId.NULL;
            }
            NodeRef newTreeRef = new NodeRef(treeNode, parentPath, parentMetadataId);
            target.put(newTreeRef, tree);
            return null;
        }

    }

}