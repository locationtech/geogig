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
import static org.locationtech.geogig.model.RevTree.EMPTY;
import static org.locationtech.geogig.model.RevTree.EMPTY_TREE_ID;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.data.FindFeatureTypeTrees;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.DiffCount;
import org.locationtech.geogig.plumbing.DiffWorkTree;
import org.locationtech.geogig.plumbing.FindOrCreateSubtree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateTree;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.FeatureType;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;

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
    public synchronized ObjectId updateWorkHead(ObjectId newTree) {
        context.command(UpdateRef.class).setName(Ref.WORK_HEAD).setNewValue(newTree).call();
        return newTree;
    }

    /**
     * @return the tree represented by WORK_HEAD. If there is no tree set at WORK_HEAD, it will
     *         return the HEAD tree (no unstaged changes).
     */
    @Override
    public synchronized RevTree getTree() {
        Optional<ObjectId> workTreeId = context.command(ResolveTreeish.class)
                .setTreeish(Ref.WORK_HEAD).call();

        RevTree workTree = EMPTY;

        if (workTreeId.isPresent()) {
            if (!workTreeId.get().equals(EMPTY_TREE_ID)) {
                workTree = indexDatabase.getTree(workTreeId.get());
            }
        } else {
            // Work tree was not resolved, update it to the head.
            Optional<ObjectId> headTreeId = context.command(ResolveTreeish.class)
                    .setTreeish(Ref.HEAD).call();

            if (headTreeId.isPresent() && !headTreeId.get().equals(EMPTY_TREE_ID)) {
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
     * @param parentPath the parent path of the feature
     * @param featureId the id of the feature
     * @return true if the object was found and deleted, false otherwise
     */
    @Override
    public boolean delete(final String parentPath, final String featureId) {
        final RevTree workHead = getTree();
        final ObjectId newWorkHeadId = delete(NodeRef.appendChild(parentPath, featureId));
        return !workHead.getId().equals(newWorkHeadId);
    }

    /**
     * @param path the path to the tree to truncate
     * @return the new {@link ObjectId} for the root tree in the {@link Ref#WORK_HEAD working tree}
     */
    @Override
    public ObjectId truncate(final String path) {
        final RevTree workHead = getTree();

        final NodeRef currentTypeRef = context.command(FindTreeChild.class).setParent(workHead)
                .setChildPath(path).call().orNull();

        if (null == currentTypeRef) {
            return workHead.getId();
        }
        checkArgument(TYPE.TREE.equals(currentTypeRef.getType()), "%s is not a tree: %s", path,
                currentTypeRef.getType());

        final NodeRef newTypeRef = currentTypeRef.update(EMPTY_TREE_ID, (Envelope) null);

        final RevTree newWorkHead = context.command(UpdateTree.class).setRoot(workHead)
                .setChild(newTypeRef).call();
        if (!workHead.equals(newWorkHead)) {
            updateWorkHead(newWorkHead.getId());
        }
        return newWorkHead.getId();
    }

    /**
     * Deletes a tree and the features it contains from the working tree and updates the WORK_HEAD
     * ref.
     * <p>
     * Note this methods completely removes the tree from the working tree. If the tree pointed out
     * to by {@code path} should be left empty, use {@link #truncate} instead.
     * 
     * @param treePath the path to the tree to delete
     * @return
     * @throws Exception
     */
    @Override
    public ObjectId delete(final String treePath) {
        final RevTree workHead = getTree();

        final NodeRef childRef = context.command(FindTreeChild.class).setParent(workHead)
                .setChildPath(treePath).call().orNull();

        if (null == childRef) {
            return workHead.getId();
        }
        final RevTree newWorkTree;
        if (TYPE.FEATURE.equals(childRef.getType())) {
            final String parentTreePath = childRef.getParentPath();
            final NodeRef typeTreeRef = context.command(FindTreeChild.class).setParent(workHead)
                    .setChildPath(parentTreePath).call().get();
            final RevTree currentParent = indexDatabase.getTree(typeTreeRef.getObjectId());
            RevTreeBuilder parentBuilder = RevTreeBuilder.builder(indexDatabase, currentParent);
            parentBuilder.remove(childRef.getNode());

            final RevTree newParent = parentBuilder.build();
            if (newParent.getId().equals(typeTreeRef.getObjectId())) {
                return workHead.getId();
            }
            final Envelope newBounds = SpatialOps.boundsOf(newParent);
            final NodeRef newParentRef = typeTreeRef.update(newParent.getId(), newBounds);
            newWorkTree = context.command(UpdateTree.class).setRoot(workHead).setChild(newParentRef)
                    .call();
        } else {
            checkArgument(TYPE.TREE.equals(childRef.getType()));
            newWorkTree = context.command(UpdateTree.class).setRoot(workHead)
                    .removeChildTree(treePath).call();
        }

        if (!workHead.equals(newWorkTree)) {
            updateWorkHead(newWorkTree.getId());
        }
        return newWorkTree.getId();
    }

    @Override
    public ObjectId delete(Iterator<String> features, ProgressListener progress) {

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
                return currewntWorkHead.getId();
            }

            final Map<NodeRef, RevTree> trees = insertHelper.buildTrees();

            UpdateTree updateTree = context.command(UpdateTree.class).setRoot(currewntWorkHead);

            for (Map.Entry<NodeRef, RevTree> treeEntry : trees.entrySet()) {
                if (progress.isCanceled()) {
                    return currewntWorkHead.getId();
                }
                NodeRef treeRef = treeEntry.getKey();
                assert indexDatabase.exists(treeRef.getObjectId());
                updateTree.setChild(treeRef);
            }

            final RevTree newWorkHead = updateTree.call();

            if (!newWorkHead.equals(currewntWorkHead) && !progress.isCanceled()) {
                updateWorkHead(newWorkHead.getId());
            }

            return newWorkHead.getId();
        } finally {
            treeBuildingService.shutdownNow();
        }
    }

    @Override
    public synchronized NodeRef createTypeTree(final String treePath,
            final FeatureType featureType) {

        NodeRef.checkValidPath(treePath);

        final RevTree workHead = getTree();
        final NodeRef currentTreeRef = context.command(FindTreeChild.class).setParent(workHead)
                .setChildPath(treePath).call().orNull();
        if (null != currentTreeRef) {
            throw new IllegalArgumentException("Tree already exists at " + treePath);
        }

        final RevFeatureType revType = RevFeatureType.builder().type(featureType).build();
        indexDatabase.put(revType);

        final ObjectId metadataId = revType.getId();

        final String parentPath = NodeRef.parentPath(treePath);
        final String treeName = NodeRef.nodeFromPath(treePath);

        final NodeRef newTreeRef = NodeRef.create(parentPath, RevObjectFactory.defaultInstance()
                .createNode(treeName, EMPTY_TREE_ID, metadataId, TYPE.TREE, null, null));

        final RevTree newWorkHead = context.command(UpdateTree.class).setRoot(workHead)
                .setChild(newTreeRef).call();

        updateWorkHead(newWorkHead.getId());

        NodeRef ref = context.command(FindTreeChild.class).setParent(newWorkHead)
                .setChildPath(treePath).call().orNull();
        checkNotNull(ref, "tree wasn't created: " + treePath);
        return ref;
    }

    @Override
    public ObjectId insert(FeatureInfo featureInfo) {
        checkNotNull(featureInfo);
        return insert(Iterators.singletonIterator(featureInfo), DefaultProgressListener.NULL);
    }

    @Override
    public ObjectId insert(Iterator<FeatureInfo> featureInfos, ProgressListener progress) {
        checkArgument(featureInfos != null);
        checkArgument(progress != null);

        final RevTree currentWorkHead = getTree();

        // NodeRef::path, but friendly for Fortify
        Function<NodeRef, String> fn_path = new Function<NodeRef, String>() {
            @Override
            public String apply(NodeRef noderef) {
                return noderef.path();
            }
        };

        final Map<String, NodeRef> currentTrees = Maps
                .newHashMap(Maps.uniqueIndex(getFeatureTypeTrees(), fn_path));

        Map<String, RevTreeBuilder> parentBuilders = new HashMap<>();

        progress.setProgress(0);
        final AtomicLong p = new AtomicLong();
        Function<FeatureInfo, RevFeature> treeBuildingTransformer = fi -> {
            final String parentPath = NodeRef.parentPath(fi.getPath());
            final String fid = NodeRef.nodeFromPath(fi.getPath());
            @Nullable
            ObjectId metadataId = fi.getFeatureTypeId();
            RevTreeBuilder parentBuilder = getTreeBuilder(currentTrees, parentBuilders, parentPath,
                    metadataId);

            if (fi.isDelete()) {
                if (parentBuilder != null) {
                    parentBuilder.remove(RevObjectFactory.defaultInstance().createNode(fid,
                            ObjectId.NULL, ObjectId.NULL, TYPE.FEATURE, null, null));
                }
                return null;
            }

            Preconditions.checkState(parentBuilder != null);
            RevFeature feature = fi.getFeature();
            NodeRef parentRef = currentTrees.get(parentPath);
            Preconditions.checkNotNull(parentRef);
            if (fi.getFeatureTypeId().equals(parentRef.getMetadataId())) {
                metadataId = ObjectId.NULL;// use the parent's default
            }

            ObjectId oid = feature.getId();
            Envelope bounds = SpatialOps.boundsOf(feature);
            Node featureNode = RevObjectFactory.defaultInstance().createNode(fid, oid, metadataId,
                    TYPE.FEATURE, bounds, null);

            parentBuilder.put(featureNode);

            progress.setProgress(p.incrementAndGet());
            return feature;
        };

        Iterator<RevFeature> features = Iterators.transform(featureInfos, treeBuildingTransformer);
        features = Iterators.filter(features, Predicates.notNull());

        // (f) -> !progress.isCanceled()
        Predicate<RevFeature> fn = new Predicate<RevFeature>() {
            @Override
            public boolean apply(RevFeature f) {
                return !progress.isCanceled();
            }
        };

        features = Iterators.filter(features, fn);

        Stopwatch insertTime = Stopwatch.createStarted();
        indexDatabase.putAll(features);
        insertTime.stop();
        if (progress.isCanceled()) {
            return currentWorkHead.getId();
        }

        progress.setDescription(String.format("%,d features inserted in %s", p.get(), insertTime));

        UpdateTree updateTree = context.command(UpdateTree.class).setRoot(currentWorkHead);
        parentBuilders.forEach((path, builder) -> {

            final NodeRef oldTreeRef = currentTrees.get(path);
            progress.setDescription(String.format("Building final tree %s...", oldTreeRef.name()));
            Stopwatch treeTime = Stopwatch.createStarted();
            final RevTree newFeatureTree = builder.build();
            treeTime.stop();
            progress.setDescription(String.format("%,d features tree built in %s",
                    newFeatureTree.size(), treeTime));
            final NodeRef newTreeRef = oldTreeRef.update(newFeatureTree.getId(),
                    SpatialOps.boundsOf(newFeatureTree));
            updateTree.setChild(newTreeRef);
        });

        final RevTree newWorkHead = updateTree.call();
        return updateWorkHead(newWorkHead.getId());
    }

    @Nullable
    private RevTreeBuilder getTreeBuilder(final Map<String, NodeRef> currentTrees,
            final Map<String, RevTreeBuilder> treeBuilders, final String treePath,
            final @Nullable ObjectId featureMetadataId) {

        checkNotNull(treePath);
        RevTreeBuilder builder = treeBuilders.get(treePath);
        if (builder == null) {
            NodeRef treeRef = currentTrees.get(treePath);
            if (treeRef == null) {
                if (featureMetadataId == null) {
                    return null;
                }
                String parentPath = NodeRef.parentPath(treePath);
                String name = NodeRef.nodeFromPath(treePath);
                Node treeNode = RevObjectFactory.defaultInstance().createNode(name, EMPTY_TREE_ID,
                        featureMetadataId, TYPE.TREE, null, null);
                treeRef = new NodeRef(treeNode, parentPath, featureMetadataId);
                currentTrees.put(treePath, treeRef);
            }
            builder = RevTreeBuilder.builder(indexDatabase,
                    context.command(FindOrCreateSubtree.class).setParent(getTree())
                            .setChildPath(treePath).call());
            treeBuilders.put(treePath, builder);
        }
        return builder;
    }

    /**
     * Determines if a specific feature type is versioned (existing in the main repository).
     * 
     * @param treePath feature type to check
     * @return true if the feature type is versioned, false otherwise.
     */
    @Override
    public boolean hasRoot(final String treePath) {

        Optional<NodeRef> typeNameTreeRef = context.command(FindTreeChild.class)
                .setChildPath(treePath).call();

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
        Optional<ObjectId> stageHead;
        Optional<ObjectId> workHead;
        stageHead = context.command(ResolveTreeish.class).setTreeish(Ref.STAGE_HEAD).call();
        workHead = context.command(ResolveTreeish.class).setTreeish(Ref.WORK_HEAD).call();
        return workHead.equals(stageHead);
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
     * <p>
     * It also modifies the metadataId associated to feature nodes under the passed path, which used
     * the previous default feature type.
     * 
     * @param path the path
     * @param featureType the new feature type definition to set as default for the passed path
     */
    @Override
    public NodeRef updateTypeTree(final String treePath, final FeatureType featureType) {

        final RevTree workHead = getTree();

        final NodeRef typeTreeRef = context.command(FindTreeChild.class).setParent(workHead)
                .setChildPath(treePath).call().orNull();
        Preconditions.checkArgument(null != typeTreeRef, "Tree does not exist: %s", treePath);

        final RevFeatureType newRevType = RevFeatureType.builder().type(featureType).build();
        if (newRevType.getId().equals(typeTreeRef.getMetadataId())) {
            return typeTreeRef;
        }
        indexDatabase.put(newRevType);

        final ObjectId oldDefaultMetadata = typeTreeRef.getDefaultMetadataId();
        final ObjectId newDeafultMetadata = newRevType.getId();

        Preconditions.checkState(!ObjectId.NULL.equals(oldDefaultMetadata));

        Iterator<NodeRef> oldFeatureRefs = context.command(LsTreeOp.class)//
                .setReference(treePath)//
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).call();

        // rebuild the tree with nodes with updated metadata id as necessary
        RevTreeBuilder newTreeBuilder = RevTreeBuilder.builder(indexDatabase);

        while (oldFeatureRefs.hasNext()) {
            NodeRef ref = oldFeatureRefs.next();
            Node node = ref.getNode();
            ObjectId newMetadataId;
            final @Nullable ObjectId nodesMetadataId = node.getMetadataId().orNull();
            if (null == nodesMetadataId) {
                // force the node holding the old metadata id instead of relying on its parent's
                newMetadataId = oldDefaultMetadata;
            } else if (newDeafultMetadata.equals(nodesMetadataId)) {
                // explicitly had the new metadataid, make it default to the new parent's
                newMetadataId = ObjectId.NULL;
            } else {
                // neither the old nor the new
                newMetadataId = nodesMetadataId;
            }
            Node newNode = RevObjectFactory.defaultInstance().createNode(node.getName(),
                    node.getObjectId(), newMetadataId, TYPE.FEATURE, node.bounds().orNull(), null);
            newTreeBuilder.put(newNode);
        }

        final RevTree newTypeTree = newTreeBuilder.build();

        final ObjectId overridingMetadataId = newRevType.getId();

        final Node overridingTreeNode = RevObjectFactory.defaultInstance().createNode(
                typeTreeRef.name(), newTypeTree.getId(), overridingMetadataId, TYPE.TREE, null,
                null);

        final NodeRef newTreeRef = NodeRef.create(typeTreeRef.getParentPath(), overridingTreeNode);

        final RevTree newWorkHead = context.command(UpdateTree.class).setRoot(workHead)
                .setChild(newTreeRef).call();

        updateWorkHead(newWorkHead.getId());

        return context.command(FindTreeChild.class).setParent(getTree()).setChildPath(treePath)
                .call().get();

    }
}
