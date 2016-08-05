/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.store.FeatureIteratorIterator;
import org.geotools.factory.Hints;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.geogig.data.FeatureBuilder;
import org.locationtech.geogig.data.FindFeatureTypeTrees;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureBuilder;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevFeatureTypeBuilder;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.DiffCount;
import org.locationtech.geogig.plumbing.DiffWorkTree;
import org.locationtech.geogig.plumbing.FindOrCreateSubtree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.WriteBack;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.vividsolutions.jts.geom.CoordinateSequenceFactory;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.impl.PackedCoordinateSequenceFactory;

/**
 * A working tree is the collection of Features for a single FeatureType in GeoServer that has a
 * repository associated with it (and hence is subject of synchronization).
 * <p>
 * It represents the set of Features tracked by some kind of geospatial data repository (like the
 * GeoServer Catalog). It is essentially a "tree" with various roots and only one level of nesting,
 * since the FeatureTypes held in this working tree are the equivalents of files in a git working
 * tree.
 * </p>
 * <p>
 * <ul>
 * <li>A WorkingTree represents the current working copy of the versioned feature types
 * <li>A WorkingTree has a Repository
 * <li>A Repository holds commits and branches
 * <li>You perform work on the working tree (insert/delete/update features)
 * <li>Then you commit to the current Repository's branch
 * <li>You can checkout a different branch from the Repository and the working tree will be updated
 * to reflect the state of that branch
 * </ul>
 * 
 * @see Repository
 */
public class WorkingTreeImpl implements WorkingTree {

    private ObjectDatabase indexDatabase;

    private Context context;

    @Inject
    public WorkingTreeImpl(final Context injector) {
        this.indexDatabase = injector.objectDatabase();
        this.context = injector;
    }

    /**
     * Updates the WORK_HEAD ref to the specified tree.
     * 
     * @param newTree the tree to be set as the new WORK_HEAD
     */
    @Override
    public synchronized void updateWorkHead(ObjectId newTree) {
        context.command(UpdateRef.class).setName(Ref.WORK_HEAD).setNewValue(newTree).call();
    }

    /**
     * @return the tree represented by WORK_HEAD. If there is no tree set at WORK_HEAD, it will
     *         return the HEAD tree (no unstaged changes).
     */
    @Override
    public synchronized RevTree getTree() {
        Optional<ObjectId> workTreeId = context.command(ResolveTreeish.class)
                .setTreeish(Ref.WORK_HEAD).call();

        RevTree workTree = RevTreeBuilder.EMPTY;

        if (workTreeId.isPresent()) {
            if (!workTreeId.get().equals(RevTreeBuilder.EMPTY_TREE_ID)) {
                workTree = indexDatabase.getTree(workTreeId.get());
            }
        } else {
            // Work tree was not resolved, update it to the head.
            Optional<ObjectId> headTreeId = context.command(ResolveTreeish.class)
                    .setTreeish(Ref.HEAD).call();

            if (headTreeId.isPresent() && !headTreeId.get().equals(RevTreeBuilder.EMPTY_TREE_ID)) {
                workTree = context.objectDatabase().getTree(headTreeId.get());
                updateWorkHead(workTree.getId());
            }
        }
        Preconditions.checkState(workTree != null);
        return workTree;
    }

    /**
     * Deletes a single feature from the working tree and updates the WORK_HEAD ref.
     * 
     * @param path the path of the feature
     * @param featureId the id of the feature
     * @return true if the object was found and deleted, false otherwise
     */
    @Override
    public boolean delete(final String path, final String featureId) {
        Optional<NodeRef> typeTreeRef = context.command(FindTreeChild.class).setParent(getTree())
                .setChildPath(path).call();

        ObjectId metadataId = null;
        if (typeTreeRef.isPresent()) {
            metadataId = typeTreeRef.get().getMetadataId();
        }

        RevTreeBuilder parentTree = RevTreeBuilder.canonical(indexDatabase, context
                .command(FindOrCreateSubtree.class).setParent(getTree()).setChildPath(path).call());

        String featurePath = NodeRef.appendChild(path, featureId);
        Optional<Node> node = findUnstaged(featurePath);
        if (node.isPresent()) {
            parentTree.remove(node.get().getName());
        }

        ObjectId newTree = context.command(WriteBack.class).setAncestor(getTree())
                .setChildPath(path).setMetadataId(metadataId).setTree(parentTree.build()).call();

        updateWorkHead(newTree);

        return node.isPresent();
    }

    /**
     * @param path the path to the tree to truncate
     * @return the new {@link ObjectId} for the root tree in the {@link Ref#WORK_HEAD working tree}
     */
    @Override
    public ObjectId truncate(final String path) {
        final String parentPath = NodeRef.parentPath(path);
        final String childName = NodeRef.nodeFromPath(path);

        final RevTree workHead = getTree();

        RevTree parent;
        ObjectId parentMetadataId = ObjectId.NULL;
        if (parentPath.isEmpty()) {
            parent = workHead;
        } else {
            Optional<NodeRef> parentRef = context.command(FindTreeChild.class).setParent(workHead)
                    .setChildPath(parentPath).call();
            if (!parentRef.isPresent()) {
                return workHead.getId();
            }

            parentMetadataId = parentRef.get().getMetadataId();
            parent = context.command(RevObjectParse.class)
                    .setObjectId(parentRef.get().getObjectId()).call(RevTree.class).get();
        }
        Optional<NodeRef> treeRef = context.command(FindTreeChild.class).setParent(parent)
                .setChildPath(childName).call();
        if (!treeRef.isPresent()) {
            return workHead.getId();
        }
        final ObjectId treeMetadataId = treeRef.get().getMetadataId();
        Map<String, Object> extraData = treeRef.get().getNode().getExtraData();
        if (extraData != null) {
            extraData = new HashMap<>(extraData);
        }
        RevTreeBuilder parentBuilder = RevTreeBuilder.canonical(indexDatabase, parent);
        Envelope bounds = null;
        Node newTreeNode = Node.create(childName, RevTreeBuilder.EMPTY_TREE_ID, treeMetadataId,
                TYPE.TREE, bounds, extraData);
        RevTree newParent = parentBuilder.put(newTreeNode).build();
        indexDatabase.put(newParent);

        if (parent.getId().equals(newParent.getId())) {
            return workHead.getId();// nothing changed
        }

        ObjectId newWorkHead;
        if (parentPath.isEmpty()) {
            newWorkHead = newParent.getId();
        } else {
            newWorkHead = context.command(WriteBack.class).setAncestor(workHead)
                    .setChildPath(parentPath).setTree(newParent).setMetadataId(parentMetadataId)
                    .call();
        }
        updateWorkHead(newWorkHead);
        return newWorkHead;
    }

    /**
     * Deletes a tree and the features it contains from the working tree and updates the WORK_HEAD
     * ref.
     * <p>
     * Note this methods completely removes the tree from the working tree. If the tree pointed out
     * to by {@code path} should be left empty, use {@link #truncate} instead.
     * 
     * @param path the path to the tree to delete
     * @return
     * @throws Exception
     */
    @Override
    public ObjectId delete(final String path) {

        final String parentPath = NodeRef.parentPath(path);
        final String childName = NodeRef.nodeFromPath(path);

        final RevTree workHead = getTree();

        RevTree parent;
        RevTreeBuilder parentBuilder;
        ObjectId parentMetadataId = ObjectId.NULL;
        if (parentPath.isEmpty()) {
            parent = workHead;
            parentBuilder = RevTreeBuilder.canonical(indexDatabase, workHead);
        } else {
            Optional<NodeRef> parentRef = context.command(FindTreeChild.class).setParent(workHead)
                    .setChildPath(parentPath).call();
            if (!parentRef.isPresent()) {
                return workHead.getId();
            }

            parentMetadataId = parentRef.get().getMetadataId();
            parent = context.command(RevObjectParse.class)
                    .setObjectId(parentRef.get().getObjectId()).call(RevTree.class).get();
            parentBuilder = RevTreeBuilder.canonical(indexDatabase, parent);
        }
        RevTree newParent = parentBuilder.remove(childName).build();
        indexDatabase.put(newParent);
        if (parent.getId().equals(newParent.getId())) {
            return workHead.getId();// nothing changed
        }

        ObjectId newWorkHead;
        if (parentPath.isEmpty()) {
            newWorkHead = newParent.getId();
        } else {
            newWorkHead = context.command(WriteBack.class).setAncestor(workHead)
                    .setChildPath(parentPath).setTree(newParent).setMetadataId(parentMetadataId)
                    .call();
        }
        updateWorkHead(newWorkHead);
        return newWorkHead;
    }

    /**
     * Deletes a collection of features of the same type from the working tree and updates the
     * WORK_HEAD ref.
     * 
     * @param typeName feature type
     * @param filter - currently unused
     * @param affectedFeatures features to remove
     * @throws Exception
     */
    @Override
    public void delete(final Name typeName, final Filter filter,
            final Iterator<Feature> affectedFeatures) throws Exception {

        Optional<NodeRef> typeTreeRef = context.command(FindTreeChild.class).setParent(getTree())
                .setChildPath(typeName.getLocalPart()).call();

        ObjectId parentMetadataId = null;
        if (typeTreeRef.isPresent()) {
            parentMetadataId = typeTreeRef.get().getMetadataId();
        }

        RevTreeBuilder parentTree = RevTreeBuilder.canonical(indexDatabase,
                context.command(FindOrCreateSubtree.class)
                        .setParent(Suppliers.ofInstance(Optional.of(getTree())))
                        .setChildPath(typeName.getLocalPart()).call());

        String fid;
        String featurePath;

        while (affectedFeatures.hasNext()) {
            fid = affectedFeatures.next().getIdentifier().getID();
            featurePath = NodeRef.appendChild(typeName.getLocalPart(), fid);
            Optional<Node> ref = findUnstaged(featurePath);
            if (ref.isPresent()) {
                parentTree.remove(ref.get().getName());
            }
        }

        ObjectId newTree = context.command(WriteBack.class).setAncestor(getTree())
                .setMetadataId(parentMetadataId).setChildPath(typeName.getLocalPart())
                .setTree(parentTree.build()).call();

        updateWorkHead(newTree);
    }

    /**
     * Deletes a feature type from the working tree and updates the WORK_HEAD ref.
     * 
     * @param typeName feature type to remove
     * @throws Exception
     */
    @Override
    public void delete(final Name typeName) throws Exception {
        checkNotNull(typeName);

        RevTreeBuilder workRoot = RevTreeBuilder.canonical(indexDatabase, getTree());

        final String treePath = typeName.getLocalPart();
        workRoot.remove(treePath);
        RevTree newRoot = workRoot.build();
        indexDatabase.put(newRoot);
        updateWorkHead(newRoot.getId());
    }

    /**
     * 
     * @param features the features to delete
     */
    @Override
    public void delete(Iterator<String> features) {
        delete(features, DefaultProgressListener.NULL);
    }

    @Override
    public void delete(Iterator<String> features, ProgressListener progress) {

        final ExecutorService treeBuildingService = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat("WorkingTree-tree-builder-%d").build());

        try {
            final WorkingTreeInsertHelper insertHelper;
            final RevTree currewntWorkHead = getTree();

            insertHelper = new WorkingTreeInsertHelper(context, currewntWorkHead,
                    treeBuildingService);

            while (features.hasNext() && !progress.isCanceled()) {
                String featurePath = features.next();
                insertHelper.remove(featurePath);
            }
            if (progress.isCanceled()) {
                return;
            }

            Map<NodeRef, RevTree> trees = insertHelper.buildTrees();

            ObjectId newWorkHead = currewntWorkHead.getId();

            for (Map.Entry<NodeRef, RevTree> treeEntry : trees.entrySet()) {
                if (progress.isCanceled()) {
                    return;
                }
                NodeRef treeRef = treeEntry.getKey();
                assert indexDatabase.exists(treeRef.getObjectId());

                RevTree newFeatureTree = treeEntry.getValue();

                String treePath = treeRef.path();

                newWorkHead = context.command(WriteBack.class).setAncestor(newWorkHead)
                        .setChildPath(treePath).setMetadataId(treeRef.getMetadataId())
                        .setTree(newFeatureTree).call();
            }

            if (!newWorkHead.equals(currewntWorkHead.getId())) {
                updateWorkHead(newWorkHead);
            }
        } finally {
            treeBuildingService.shutdownNow();
        }
    }

    @Override
    public synchronized NodeRef createTypeTree(final String treePath,
            final FeatureType featureType) {

        final RevTree workHead = getTree();
        Optional<NodeRef> typeTreeRef = context.command(FindTreeChild.class).setParent(workHead)
                .setChildPath(treePath).call();

        final RevFeatureType revType = RevFeatureTypeBuilder.build(featureType);
        if (typeTreeRef.isPresent()) {
            throw new IllegalArgumentException("Tree already exists at " + treePath);
        }
        indexDatabase.put(revType);

        final ObjectId metadataId = revType.getId();
        final RevTree newTree = RevTreeBuilder.canonical(indexDatabase).build();

        ObjectId newWorkHeadId = context.command(WriteBack.class).setAncestor(workHead)
                .setChildPath(treePath).setTree(newTree).setMetadataId(metadataId).call();
        updateWorkHead(newWorkHeadId);

        return context.command(FindTreeChild.class).setParent(getTree()).setChildPath(treePath)
                .call().get();
    }

    @Override
    public void insert(FeatureInfo featureInfo) {
        checkNotNull(featureInfo);
        insert(Iterators.singletonIterator(featureInfo), DefaultProgressListener.NULL);
    }

    @Override
    public void insert(Iterator<FeatureInfo> featureInfos, ProgressListener progress) {
        checkArgument(featureInfos != null);
        checkArgument(progress != null);

        final Map<String, NodeRef> currentTrees = Maps
                .newHashMap(Maps.uniqueIndex(getFeatureTypeTrees(), (nr) -> nr.path()));

        Map<String, RevTreeBuilder> parentBuilders = new HashMap<>();

        Function<FeatureInfo, RevFeature> treeBuildingTransformer = (fi) -> {
            RevFeature feature = fi.getFeature();
            String parentPath = NodeRef.parentPath(fi.getPath());
            ObjectId metadataId = fi.getFeatureTypeId();
            RevTreeBuilder parentBuilder = getTreeBuilder(currentTrees, parentBuilders, parentPath,
                    metadataId);

            NodeRef parentRef = currentTrees.get(parentPath);
            Preconditions.checkNotNull(parentRef);
            if (fi.getFeatureTypeId().equals(parentRef.getMetadataId())) {
                metadataId = ObjectId.NULL;// use the parent's default
            }

            String fid = NodeRef.nodeFromPath(fi.getPath());
            ObjectId oid = feature.getId();
            Envelope bounds = SpatialOps.boundsOf(feature);
            Node featureNode = Node.create(fid, oid, metadataId, TYPE.FEATURE, bounds);

            parentBuilder.put(featureNode);
            return feature;
        };

        Iterator<RevFeature> features = Iterators.transform(featureInfos, treeBuildingTransformer);
        features = Iterators.filter(features, (f) -> !progress.isCanceled());

        indexDatabase.putAll(features);
        if (progress.isCanceled()) {
            return;
        }

        parentBuilders.forEach((path, builder) -> {

            RevTree newFeatureTree = builder.build();
            ObjectId treeMetadataId = currentTrees.get(path).getDefaultMetadataId();

            ObjectId newWorkTree = context.command(WriteBack.class).setAncestor(getTree())
                    .setChildPath(path).setTree(newFeatureTree).setMetadataId(treeMetadataId)
                    .call();

            updateWorkHead(newWorkTree);
        });

    }

    private RevTreeBuilder getTreeBuilder(Map<String, NodeRef> currentTrees,
            Map<String, RevTreeBuilder> treeBuilders, String treePath, ObjectId featureMetadataId) {

        RevTreeBuilder builder = treeBuilders.get(treePath);
        if (builder == null) {
            NodeRef treeRef = currentTrees.get(treePath);
            if (treeRef == null) {
                String parentPath = NodeRef.parentPath(treePath);
                Node treeNode = Node.create(NodeRef.nodeFromPath(treePath),
                        RevTreeBuilder.EMPTY_TREE_ID, featureMetadataId, TYPE.TREE, null);
                treeRef = new NodeRef(treeNode, parentPath, featureMetadataId);
                currentTrees.put(treePath, treeRef);
            }

            builder = RevTreeBuilder.canonical(indexDatabase,
                    context.command(FindOrCreateSubtree.class)
                            .setParent(Suppliers.ofInstance(Optional.of(getTree())))
                            .setChildPath(treePath).call());
            treeBuilders.put(treePath, builder);
        }
        return builder;
    }

    /**
     * Insert a single feature into the working tree and updates the WORK_HEAD ref.
     * 
     * @param parentTreePath path of the parent tree to insert the feature into
     * @param feature the feature to insert
     */
    @Override
    public Node insert(final String parentTreePath, final Feature feature) {

        final FeatureType featureType = feature.getType();

        NodeRef treeRef;

        Optional<NodeRef> typeTreeRef = context.command(FindTreeChild.class).setParent(getTree())
                .setChildPath(parentTreePath).call();
        ObjectId metadataId;
        if (typeTreeRef.isPresent()) {
            treeRef = typeTreeRef.get();
            RevFeatureType newFeatureType = RevFeatureTypeBuilder.build(featureType);
            metadataId = newFeatureType.getId().equals(treeRef.getMetadataId()) ? ObjectId.NULL
                    : newFeatureType.getId();
            if (!newFeatureType.getId().equals(treeRef.getMetadataId())) {
                indexDatabase.put(newFeatureType);
            }
        } else {
            treeRef = createTypeTree(parentTreePath, featureType);
            metadataId = ObjectId.NULL;// treeRef.getMetadataId();
        }

        // ObjectId metadataId = treeRef.getMetadataId();
        final RevFeature newFeature = RevFeatureBuilder.build(feature);
        final ObjectId objectId = newFeature.getId();
        final Envelope bounds = (ReferencedEnvelope) feature.getBounds();
        final String nodeName = feature.getIdentifier().getID();

        indexDatabase.put(newFeature);

        final Node node = Node.create(nodeName, objectId, metadataId, TYPE.FEATURE, bounds);

        RevTreeBuilder parentTree = RevTreeBuilder.canonical(indexDatabase,
                context.command(FindOrCreateSubtree.class)
                        .setParent(Suppliers.ofInstance(Optional.of(getTree())))
                        .setChildPath(parentTreePath).call());

        parentTree.put(node);
        final ObjectId treeMetadataId = treeRef.getMetadataId();

        ObjectId newTree = context.command(WriteBack.class).setAncestor(getTree())
                .setChildPath(parentTreePath).setTree(parentTree.build())
                .setMetadataId(treeMetadataId).call();

        updateWorkHead(newTree);

        final String featurePath = NodeRef.appendChild(parentTreePath, node.getName());
        Optional<NodeRef> featureRef = context.command(FindTreeChild.class).setParent(getTree())
                .setChildPath(featurePath).call();
        return featureRef.get().getNode();
    }

    @Override
    public void insert(final String treePath,
            @SuppressWarnings("rawtypes") final FeatureSource source, final Query query,
            ProgressListener listener) {

        final NodeRef treeRef = findOrCreateTypeTree(treePath, source);

        Long collectionSize = null;
        try {
            // try for a fast count
            int count = source.getCount(Query.ALL);
            if (count > -1) {
                collectionSize = Long.valueOf(count);
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        final int nFetchThreads;
        {
            // maxFeatures is assumed to be supported by all data sources, so supportsPaging depends
            // only on offset being supported
            boolean supportsPaging = source.getQueryCapabilities().isOffsetSupported();
            if (supportsPaging) {
                Platform platform = context.platform();
                int availableProcessors = platform.availableProcessors();
                nFetchThreads = Math.max(2, availableProcessors / 2);
            } else {
                nFetchThreads = 1;
            }
        }

        final ExecutorService executorService = Executors.newFixedThreadPool(2 + nFetchThreads,
                new ThreadFactoryBuilder().setNameFormat("WorkingTree-tree-builder-%d").build());

        listener.started();

        Stopwatch sw = Stopwatch.createStarted();

        final RevTree origTree = indexDatabase.getTree(treeRef.getObjectId());

        RevTree newFeatureTree;
        try {
            RevTreeBuilder builder = RevTreeBuilder.canonical(indexDatabase, origTree);
            List<Future<Integer>> insertBlobsFuture = insertBlobs(source, query, executorService,
                    listener, collectionSize, nFetchThreads, builder);
            long insertedCount = 0;
            for (Future<Integer> f : insertBlobsFuture) {
                insertedCount += f.get().longValue();
            }
            sw.stop();
            listener.setDescription(
                    String.format("%,d features inserted in %s", insertedCount, sw));

            listener.setDescription("Building final tree...");

            sw.reset().start();
            newFeatureTree = builder.build();

            listener.setDescription(String.format("%,d features tree built in %s",
                    newFeatureTree.size(), sw.stop()));
            listener.complete();

        } catch (Exception e) {
            throw Throwables.propagate(Throwables.getRootCause(e));
        } finally {
            executorService.shutdown();
        }
        ObjectId newTree = context.command(WriteBack.class).setAncestor(getTree())
                .setChildPath(treePath).setMetadataId(treeRef.getMetadataId())
                .setTree(newFeatureTree).call();

        updateWorkHead(newTree);

    }

    private NodeRef findOrCreateTypeTree(final String treePath,
            @SuppressWarnings("rawtypes") final FeatureSource source) {

        final NodeRef treeRef;
        {
            Optional<NodeRef> typeTreeRef = context.command(FindTreeChild.class)
                    .setParent(getTree()).setChildPath(treePath).call();

            if (typeTreeRef.isPresent()) {
                treeRef = typeTreeRef.get();
            } else {
                FeatureType featureType = source.getSchema();
                treeRef = createTypeTree(treePath, featureType);
            }
        }
        return treeRef;
    }

    @SuppressWarnings("rawtypes")
    private List<Future<Integer>> insertBlobs(final FeatureSource source, final Query baseQuery,
            final ExecutorService executorService, final ProgressListener listener,
            final @Nullable Long collectionSize, int nTasks, RevTreeBuilder builder) {

        listener.setMaxProgress(0);
        int partitionSize = 0;
        if (collectionSize == null) {
            nTasks = 1;
            partitionSize = Integer.MAX_VALUE;
        } else {
            final int total = collectionSize.intValue();
            partitionSize = total / nTasks;
        }

        List<Future<Integer>> results = Lists.newArrayList();
        for (int i = 0; i < nTasks; i++) {
            Integer offset = nTasks == 1 ? null : i * partitionSize;
            Integer limit = nTasks == 1 ? null : partitionSize;
            if (i == nTasks - 1) {
                limit = null;// let the last task take any remaining
                             // feature
            }
            results.add(executorService
                    .submit(new BlobInsertTask(source, offset, limit, listener, builder)));
        }
        return results;
    }

    private final class BlobInsertTask implements Callable<Integer> {

        private final ProgressListener listener;

        @SuppressWarnings("rawtypes")
        private FeatureSource source;

        private Integer offset;

        private Integer limit;

        private RevTreeBuilder builder;

        private BlobInsertTask(@SuppressWarnings("rawtypes") FeatureSource source,
                @Nullable Integer offset, @Nullable Integer limit, ProgressListener listener,
                RevTreeBuilder builder) {
            this.source = source;
            this.offset = offset;
            this.limit = limit;
            this.listener = listener;
            this.builder = builder;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public Integer call() throws Exception {

            final Query query = new Query();
            CoordinateSequenceFactory coordSeq = new PackedCoordinateSequenceFactory();
            query.getHints().add(new Hints(Hints.JTS_COORDINATE_SEQUENCE_FACTORY, coordSeq));
            if (offset != null) {
                query.setStartIndex(offset);
            }
            if (limit != null && limit.intValue() > 0) {
                query.setMaxFeatures(limit);
            }
            FeatureCollection collection = source.getFeatures(query);
            FeatureIterator features = collection.features();
            Iterator<Feature> fiterator = new FeatureIteratorIterator<Feature>(features);

            AtomicInteger count = new AtomicInteger();

            Map<String, ObjectId> metadataIds = new HashMap<>();

            Iterator<RevObject> objects = Iterators.transform(fiterator, (feature) -> {
                final RevFeature revFeature = RevFeatureBuilder.build(feature);

                ObjectId id = revFeature.getId();
                String name = feature.getIdentifier().getID();
                Envelope bounds = (Envelope) feature.getBounds();
                FeatureType type = feature.getType();

                ObjectId metadataId = metadataIds.get(type.getName().getLocalPart());
                if (metadataId == null) {
                    RevFeatureType revType = RevFeatureTypeBuilder.build(type);
                    indexDatabase.put(revType);
                    metadataId = revType.getId();
                    metadataIds.put(type.getName().getLocalPart(), metadataId);
                }
                Node node = Node.create(name, id, metadataId, TYPE.FEATURE, bounds);
                builder.put(node);
                count.incrementAndGet();
                listener.setProgress(1f + listener.getProgress());
                return revFeature;
            });

            try {
                indexDatabase.putAll(objects);
            } finally {
                features.close();
            }
            return Integer.valueOf(count.get());
        }
    }

    /**
     * Inserts a collection of features into the working tree and updates the WORK_HEAD ref.
     * 
     * @param treePath the path of the tree to insert the features into
     * @param features the features to insert
     * @param listener a {@link ProgressListener} for the current process
     * @param insertedTarget if provided, inserted features will be added to this list
     * @param collectionSize number of features to add
     * @throws Exception
     */
    @Override
    public void insert(final String treePath, Iterator<? extends Feature> features,
            final ProgressListener listener, @Nullable final List<Node> insertedTarget,
            @Nullable final Integer collectionSize) {

        final Function<Feature, String> providedPath = (f) -> treePath;

        insert(providedPath, features, listener, insertedTarget, collectionSize);
    }

    /**
     * Inserts the given {@code features} into the working tree, using the {@code treePathResolver}
     * function to determine to which tree each feature is added.
     * 
     * @param treePathResolver a function that determines the path of the tree where each feature
     *        node is stored
     * @param features the features to insert, possibly of different schema and targetted to
     *        different tree paths
     * @param listener a progress listener
     * @param insertedTarget if provided, all nodes created will be added to this list. Beware of
     *        possible memory implications when inserting a lot of features.
     * @param collectionSize if given, used to determine progress and notify the {@code listener}
     * @return the total number of inserted features
     */
    @Override
    public void insert(final Function<Feature, String> treePathResolver,
            Iterator<? extends Feature> features, final ProgressListener listener,
            @Nullable final List<Node> insertedTarget, @Nullable final Integer collectionSize) {

        checkArgument(collectionSize == null || collectionSize.intValue() > -1);

        final int nTreeThreads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        final ExecutorService treeBuildingService = Executors.newFixedThreadPool(nTreeThreads,
                new ThreadFactoryBuilder().setNameFormat("WorkingTree-tree-builder-%d").build());

        final WorkingTreeInsertHelper insertHelper;

        insertHelper = new WorkingTreeInsertHelper(context, getTree(), treePathResolver,
                treeBuildingService);

        UnmodifiableIterator<? extends Feature> filtered = Iterators.filter(features,
                new Predicate<Feature>() {
                    @Override
                    public boolean apply(Feature feature) {
                        if (listener.isCanceled()) {
                            return false;
                        }
                        if (feature instanceof FeatureToDelete) {
                            insertHelper.remove((FeatureToDelete) feature);
                            return false;
                        } else {
                            return true;
                        }
                    }

                });
        Iterator<RevObject> objects = Iterators.transform(filtered,
                new Function<Feature, RevObject>() {

                    private int count;

                    @Override
                    public RevFeature apply(Feature feature) {
                        final RevFeature revFeature = RevFeatureBuilder.build(feature);
                        ObjectId id = revFeature.getId();
                        final Node node = insertHelper.put(id, feature);

                        if (insertedTarget != null) {
                            insertedTarget.add(node);
                        }

                        count++;
                        if (collectionSize == null) {
                            listener.setProgress(count);
                        } else {
                            listener.setProgress((float) (count * 100) / collectionSize.intValue());
                        }
                        return revFeature;
                    }

                });
        try {
            listener.started();

            indexDatabase.putAll(objects);
            if (listener.isCanceled()) {
                return;
            }
            listener.setDescription(
                    "Building trees for " + new TreeSet<String>(insertHelper.getTreeNames()));
            Stopwatch sw = Stopwatch.createStarted();

            Map<NodeRef, RevTree> trees = insertHelper.buildTrees();

            listener.setDescription(String.format("Trees built in %s", sw.stop()));

            for (Map.Entry<NodeRef, RevTree> treeEntry : trees.entrySet()) {
                if (!listener.isCanceled()) {
                    NodeRef treeRef = treeEntry.getKey();
                    RevTree newFeatureTree = treeEntry.getValue();

                    String treePath = treeRef.path();

                    ObjectId newRootTree = context.command(WriteBack.class).setAncestor(getTree())
                            .setChildPath(treePath).setMetadataId(treeRef.getMetadataId())
                            .setTree(newFeatureTree).call();
                    updateWorkHead(newRootTree);
                }
            }
            listener.complete();
        } finally {
            treeBuildingService.shutdownNow();
        }
    }

    /**
     * Updates a collection of features in the working tree and updates the WORK_HEAD ref.
     * 
     * @param treePath the path of the tree to insert the features into
     * @param features the features to insert
     * @param listener a {@link ProgressListener} for the current process
     * @param collectionSize number of features to add
     * @throws Exception
     */
    @Override
    public void update(final String treePath, final Iterator<Feature> features,
            final ProgressListener listener, @Nullable final Integer collectionSize)
            throws Exception {

        checkArgument(collectionSize == null || collectionSize.intValue() > -1);

        final Integer size = collectionSize == null || collectionSize.intValue() < 1 ? null
                : collectionSize.intValue();

        insert(treePath, features, listener, null, size);
    }

    /**
     * Determines if a specific feature type is versioned (existing in the main repository).
     * 
     * @param typeName feature type to check
     * @return true if the feature type is versioned, false otherwise.
     */
    @Override
    public boolean hasRoot(final Name typeName) {
        String localPart = typeName.getLocalPart();

        Optional<NodeRef> typeNameTreeRef = context.command(FindTreeChild.class)
                .setChildPath(localPart).call();

        return typeNameTreeRef.isPresent();
    }

    /**
     * @param pathFilter if specified, only changes that match the filter will be returned
     * @return an iterator for all of the differences between the work tree and the index based on
     *         the path filter.
     */
    @Override
    public AutoCloseableIterator<DiffEntry> getUnstaged(final @Nullable String pathFilter) {
        AutoCloseableIterator<DiffEntry> unstaged = context.command(DiffWorkTree.class)
                .setFilter(pathFilter).setReportTrees(true).call();
        return unstaged;
    }

    /**
     * @param pathFilter if specified, only changes that match the filter will be counted
     * @return the number differences between the work tree and the index based on the path filter.
     */
    @Override
    public DiffObjectCount countUnstaged(final @Nullable String pathFilter) {
        DiffObjectCount count = context.command(DiffCount.class).setOldVersion(Ref.STAGE_HEAD)
                .setNewVersion(Ref.WORK_HEAD).addFilter(pathFilter).call();
        return count;
    }

    /**
     * Returns true if there are no unstaged changes, false otherwise
     */
    @Override
    public boolean isClean() {
        Optional<ObjectId> resolved = context.command(ResolveTreeish.class)
                .setTreeish(Ref.STAGE_HEAD).call();
        return getTree().getId().equals(resolved.or(ObjectId.NULL));
    }

    /**
     * @param path finds a {@link Node} for the feature at the given path in the index
     * @return the Node for the feature at the specified path if it exists in the work tree,
     *         otherwise Optional.absent()
     */
    @Override
    public Optional<Node> findUnstaged(final String path) {
        Optional<NodeRef> nodeRef = context.command(FindTreeChild.class).setParent(getTree())
                .setChildPath(path).call();
        if (nodeRef.isPresent()) {
            return Optional.of(nodeRef.get().getNode());
        } else {
            return Optional.absent();
        }
    }

    /**
     * @return a list of all the feature type names in the working tree
     * @see FindFeatureTypeTrees
     */
    @Override
    public List<NodeRef> getFeatureTypeTrees() {

        List<NodeRef> typeTrees = context.command(FindFeatureTypeTrees.class)
                .setRootTreeRef(Ref.WORK_HEAD).call();
        return typeTrees;
    }

    /**
     * Updates the definition of a Feature type associated as default feature type to a given path.
     * It also modifies the metadataId associated to features under the passed path, which used the
     * previous default feature type.
     * 
     * @param path the path
     * @param featureType the new feature type definition to set as default for the passed path
     */
    @Override
    public NodeRef updateTypeTree(final String treePath, final FeatureType featureType) {

        // TODO: This is not the optimal way of doing this. A better solution should be found.

        final RevTree workHead = getTree();
        Optional<NodeRef> typeTreeRef = context.command(FindTreeChild.class).setParent(workHead)
                .setChildPath(treePath).call();
        Preconditions.checkArgument(typeTreeRef.isPresent(), "Tree does not exist: %s", treePath);

        Iterator<NodeRef> iter = context.command(LsTreeOp.class).setReference(treePath)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).call();

        final RevFeatureType revType = RevFeatureTypeBuilder.build(featureType);
        indexDatabase.put(revType);

        final ObjectId metadataId = revType.getId();
        RevTreeBuilder treeBuilder = RevTreeBuilder.canonical(indexDatabase);

        final RevTree newTree = treeBuilder.build();
        ObjectId newWorkHeadId = context.command(WriteBack.class).setAncestor(workHead)
                .setChildPath(treePath).setTree(newTree).setMetadataId(metadataId).call();
        updateWorkHead(newWorkHeadId);

        Map<ObjectId, FeatureBuilder> featureBuilders = Maps.newHashMap();
        while (iter.hasNext()) {
            NodeRef noderef = iter.next();
            RevFeature feature = context.command(RevObjectParse.class)
                    .setObjectId(noderef.getObjectId()).call(RevFeature.class).get();
            if (!featureBuilders.containsKey(noderef.getMetadataId())) {
                RevFeatureType ft = context.command(RevObjectParse.class)
                        .setObjectId(noderef.getMetadataId()).call(RevFeatureType.class).get();
                featureBuilders.put(noderef.getMetadataId(), new FeatureBuilder(ft));
            }
            FeatureBuilder fb = featureBuilders.get(noderef.getMetadataId());
            String parentPath = NodeRef.parentPath(NodeRef.appendChild(treePath, noderef.path()));
            insert(parentPath, fb.build(noderef.getNode().getName(), feature));
        }

        return context.command(FindTreeChild.class).setParent(getTree()).setChildPath(treePath)
                .call().get();

    }

}
