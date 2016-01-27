/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevFeatureTypeImpl;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.FindOrCreateSubtree;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;
import org.opengis.geometry.BoundingBox;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Envelope;

class WorkingTreeInsertHelper {

    private static final Function<Feature, String> SIMPLE_PATH_RESOLVER = new Function<Feature, String>() {
        @Override
        public String apply(Feature f) {
            return f.getType().getName().getLocalPart();
        }
    };

    private final ObjectDatabase db;

    private final Context context;

    private final RevTree workHead;

    private Function<Feature, String> treePathResolver;

    private final Map<String, RevTreeBuilder2> treeBuilders = Maps.newHashMap();

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
        return new ArrayList<String>(treeBuilders.keySet());
    }

    public Node put(final ObjectId revFeatureId, final Feature feature) {

        final RevTreeBuilder2 treeBuilder = getTreeBuilder(feature);

        String fid = feature.getIdentifier().getID();
        BoundingBox bounds = feature.getBounds();
        FeatureType type = feature.getType();

        final Node node = treeBuilder.putFeature(revFeatureId, fid, bounds, type);
        return node;
    }

    public void remove(FeatureToDelete feature) {
        final RevTreeBuilder2 treeBuilder = getTreeBuilder(feature);

        String fid = feature.getIdentifier().getID();
        treeBuilder.removeFeature(fid);
    }

    public void remove(String featurePath) {
        final String treePath = NodeRef.parentPath(featurePath);
        final String featureId = NodeRef.nodeFromPath(featurePath);
        Optional<RevTreeBuilder2> treeBuilder = getTreeBuilder(treePath);
        if (treeBuilder.isPresent()) {
            RevTreeBuilder2 builder = treeBuilder.get();
            builder.removeFeature(featureId);
        }
    }

    private Optional<RevTreeBuilder2> getTreeBuilder(final String treePath) {
        RevTreeBuilder2 builder = treeBuilders.get(treePath);
        if (builder == null) {
            Optional<NodeRef> treeNode = context.command(FindTreeChild.class).setParent(workHead)
                    .setChildPath(treePath).call();
            if (treeNode.isPresent()) {
                RevTree parentTree = db.getTree(treeNode.get().getObjectId());
                ObjectId metadataId = treeNode.get().getMetadataId();
                builder = createBuilder(parentTree, metadataId);
                treeBuilders.put(treePath, builder);
            }
        }
        return Optional.fromNullable(builder);
    }

    private RevTreeBuilder2 getTreeBuilder(final Feature feature) {

        final String treePath = treePathResolver.apply(feature);
        RevTreeBuilder2 builder = getTreeBuilder(treePath).orNull();
        if (builder == null) {
            FeatureType type = feature.getType();
            builder = createBuilder(treePath, type);
            treeBuilders.put(treePath, builder);
        }
        return builder;
    }

    private NodeRef findOrCreateTree(final String treePath, final FeatureType type) {

        RevTree tree = context.command(FindOrCreateSubtree.class).setChildPath(treePath)
                .setParent(workHead).setParentPath(NodeRef.ROOT).call();

        ObjectId metadataId = ObjectId.NULL;
        if (type != null) {
            RevFeatureType revFeatureType = RevFeatureTypeImpl.build(type);
            if (tree.isEmpty()) {
                db.put(revFeatureType);
            }
            metadataId = revFeatureType.getId();
        }
        Envelope bounds = SpatialOps.boundsOf(tree);
        Node node = Node.create(NodeRef.nodeFromPath(treePath), tree.getId(), metadataId,
                TYPE.TREE, bounds);

        String parentPath = NodeRef.parentPath(treePath);
        return new NodeRef(node, parentPath, ObjectId.NULL);
    }

    private RevTreeBuilder2 createBuilder(String treePath, FeatureType type) {

        final NodeRef treeRef = findOrCreateTree(treePath, type);
        final ObjectId treeId = treeRef.getObjectId();
        final RevTree origTree = db.getTree(treeId);

        ObjectId defaultMetadataId = treeRef.getMetadataId();

        RevTreeBuilder2 builder = createBuilder(origTree, defaultMetadataId);
        return builder;
    }

    private RevTreeBuilder2 createBuilder(final RevTree origTree, ObjectId defaultMetadataId) {
        RevTreeBuilder2 builder;
        Platform platform = context.platform();
        builder = new RevTreeBuilder2(db, origTree, defaultMetadataId, platform, executorService);
        return builder;
    }

    public Map<NodeRef, RevTree> buildTrees() {

        final Map<NodeRef, RevTree> result = Maps.newConcurrentMap();

        List<AsyncBuildTree> tasks = Lists.newArrayList();

        for (Entry<String, RevTreeBuilder2> builderEntry : treeBuilders.entrySet()) {
            final String treePath = builderEntry.getKey();
            final RevTreeBuilder2 builder = builderEntry.getValue();
            tasks.add(new AsyncBuildTree(treePath, builder, result));
        }
        try {
            executorService.invokeAll(tasks);
        } catch (InterruptedException e) {
            throw Throwables.propagate(e);
        }
        db.putAll(result.values().iterator());
        return result;
    }

    private class AsyncBuildTree implements Callable<Void> {

        private String treePath;

        private RevTreeBuilder2 builder;

        private Map<NodeRef, RevTree> target;

        AsyncBuildTree(final String treePath, final RevTreeBuilder2 builder,
                final Map<NodeRef, RevTree> target) {

            this.treePath = treePath;
            this.builder = builder;
            this.target = target;
        }

        @Override
        public Void call() {
            RevTree tree = builder.build();

            Node treeNode;
            {
                ObjectId treeMetadataId = builder.getDefaultMetadataId();
                String name = NodeRef.nodeFromPath(treePath);
                ObjectId oid = tree.getId();
                Envelope bounds = SpatialOps.boundsOf(tree);
                treeNode = Node.create(name, oid, treeMetadataId, RevObject.TYPE.TREE, bounds);
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