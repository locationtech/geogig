/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry.ChangeType;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Creates a new root tree in the {@link ObjectDatabase object database} from the current index,
 * based on the current {@code HEAD} and returns the new root tree id.
 * <p>
 * This command creates a tree object using the current index. The id of the new root tree object is
 * returned. No {@link Ref ref} is updated as a result of this operation, so the resulting root tree
 * is "orphan". It's up to the calling code to update any needed reference.
 * 
 * The index must be in a fully merged state.
 * 
 * Conceptually, write-tree sync()s the current index contents into a set of tree objects on the
 * {@link ObjectDatabase}. In order to have that match what is actually in your directory right now,
 * you need to have done a {@link UpdateIndex} phase before you did the write-tree.
 * 
 * @see FindOrCreateSubtree
 * @see DeepMove
 * @see ResolveTreeish
 * @see CreateTree
 * @see RevObjectParse
 */
public class WriteTree extends AbstractGeoGigOp<ObjectId> {

    private Supplier<RevTree> oldRoot;

    private final List<String> pathFilters = Lists.newLinkedList();

    private Supplier<Iterator<DiffEntry>> diffSupplier = null;

    /**
     * Flag indicating whether or not to move objects from the staging to the objects database.
     * Defaults to true. See {@link #dontMoveObjects()}
     */
    private boolean moveObjects = true;

    /**
     * @param oldRoot a supplier for the old root tree
     * @return {@code this}
     */
    public WriteTree setOldRoot(Supplier<RevTree> oldRoot) {
        this.oldRoot = oldRoot;
        return this;
    }

    /**
     * 
     * @param pathFilter the pathfilter to pass on to the index
     * @return {@code this}
     */
    public WriteTree addPathFilter(String pathFilter) {
        if (pathFilter != null) {
            this.pathFilters.add(pathFilter);
        }
        return this;
    }

    public WriteTree setPathFilter(@Nullable List<String> pathFilters) {
        this.pathFilters.clear();
        if (pathFilters != null) {
            this.pathFilters.addAll(pathFilters);
        }
        return this;
    }

    public WriteTree setDiffSupplier(@Nullable Supplier<Iterator<DiffEntry>> diffSupplier) {
        this.diffSupplier = diffSupplier;
        return this;
    }

    /**
     * Executes the write tree operation.
     * 
     * @return the new root tree id, the current HEAD tree id if there are no differences between
     *         the index and the HEAD, or {@code null} if the operation has been cancelled (as
     *         indicated by the {@link #getProgressListener() progress listener}.
     */
    @Override
    protected ObjectId _call() {
        final ProgressListener progress = getProgressListener();

        final RevTree oldRootTree = resolveRootTree();
        final ObjectDatabase repositoryDatabase = objectDatabase();

        Iterator<DiffEntry> diffs = null;
        long numChanges = 0;
        if (diffSupplier == null) {
            diffs = index().getStaged(pathFilters);
            numChanges = index().countStaged(pathFilters).count();
        } else {
            diffs = diffSupplier.get();
        }

        if (!diffs.hasNext()) {
            return oldRootTree.getId();
        }
        if (progress.isCanceled()) {
            return null;
        }

        Map<String, RevTreeBuilder> repositoryChangedTrees = Maps.newHashMap();
        Map<String, NodeRef> indexChangedTrees = Maps.newHashMap();
        Map<String, ObjectId> changedTreesMetadataId = Maps.newHashMap();
        Set<String> deletedTrees = Sets.newHashSet();
        final boolean moveObjects = this.moveObjects;
        NodeRef ref;
        int i = 0;
        RevTree stageHead = index().getTree();
        while (diffs.hasNext()) {
            if (numChanges != 0) {
                progress.setProgress((float) (++i * 100) / numChanges);
            }
            if (progress.isCanceled()) {
                return null;
            }

            DiffEntry diff = diffs.next();
            // ignore the root entry
            if (NodeRef.ROOT.equals(diff.newName()) || NodeRef.ROOT.equals(diff.oldName())) {
                continue;
            }
            ref = diff.getNewObject();

            if (ref == null) {
                ref = diff.getOldObject();
            }

            final String parentPath = ref.getParentPath();
            final boolean isDelete = ChangeType.REMOVED.equals(diff.changeType());
            final TYPE type = ref.getType();
            if (isDelete && deletedTrees.contains(parentPath)) {
                // this is to avoid re-creating the parentTree for a feature delete after its parent
                // tree delete entry was processed
                continue;
            }
            RevTreeBuilder parentTree = resolveTargetTree(oldRootTree, parentPath,
                    repositoryChangedTrees, changedTreesMetadataId, ObjectId.NULL,
                    repositoryDatabase);
            if (type == TYPE.TREE && !isDelete) {
                // cache the tree
                resolveTargetTree(oldRootTree, ref.name(), repositoryChangedTrees,
                        changedTreesMetadataId, ref.getMetadataId(), repositoryDatabase);
            }

            resolveSourceTreeRef(parentPath, indexChangedTrees, changedTreesMetadataId, stageHead);

            Preconditions.checkState(parentTree != null);

            if (isDelete) {
                String oldName = diff.getOldObject().getNode().getName();
                parentTree.remove(oldName);
                if (TYPE.TREE.equals(type)) {
                    deletedTrees.add(ref.path());
                }
            } else {
                if (moveObjects && ref.getType().equals(TYPE.TREE)) {
                    RevTree tree = stagingDatabase().getTree(ref.objectId());
                    if (!ref.getMetadataId().isNull()) {
                        repositoryDatabase.put(stagingDatabase()
                                .getFeatureType(ref.getMetadataId()));
                    }
                    if (tree.isEmpty()) {
                        repositoryDatabase.put(tree);
                    } else {
                        continue;
                    }
                } else if (moveObjects) {
                    deepMove(ref.getNode());
                }
                parentTree.put(ref.getNode());
            }
        }

        if (progress.isCanceled()) {
            return null;
        }

        // now write back all changed trees
        ObjectId newTargetRootId = oldRootTree.getId();
        RevTreeBuilder directRootEntries = repositoryChangedTrees.remove(NodeRef.ROOT);
        if (directRootEntries != null) {
            RevTree newRoot = directRootEntries.build();
            repositoryDatabase.put(newRoot);
            newTargetRootId = newRoot.getId();
        }
        for (Map.Entry<String, RevTreeBuilder> e : repositoryChangedTrees.entrySet()) {
            String treePath = e.getKey();
            ObjectId metadataId = changedTreesMetadataId.get(treePath);
            RevTreeBuilder treeBuilder = e.getValue();
            RevTree newRoot = getTree(newTargetRootId);
            RevTree tree = treeBuilder.build();
            newTargetRootId = writeBack(newRoot.builder(repositoryDatabase), tree, treePath,
                    metadataId);
        }

        progress.complete();

        return newTargetRootId;
    }

    private void resolveSourceTreeRef(String parentPath, Map<String, NodeRef> indexChangedTrees,
            Map<String, ObjectId> metadataCache, RevTree stageHead) {

        if (NodeRef.ROOT.equals(parentPath)) {
            return;
        }
        NodeRef indexTreeRef = indexChangedTrees.get(parentPath);

        if (indexTreeRef == null) {
            Optional<NodeRef> treeRef = Optional.absent();
            if (!stageHead.isEmpty()) {// slight optimization, may save a lot of processing on
                                       // large first commits
                treeRef = command(FindTreeChild.class).setIndex(true).setParent(stageHead)
                        .setChildPath(parentPath).call();
            }
            if (treeRef.isPresent()) {// may not be in case of a delete
                indexTreeRef = treeRef.get();
                indexChangedTrees.put(parentPath, indexTreeRef);
                metadataCache.put(parentPath, indexTreeRef.getMetadataId());
            }
        } else {
            metadataCache.put(parentPath, indexTreeRef.getMetadataId());
        }
    }

    private RevTreeBuilder resolveTargetTree(final RevTree root, String treePath,
            Map<String, RevTreeBuilder> treeCache, Map<String, ObjectId> metadataCache,
            ObjectId fallbackMetadataId, ObjectDatabase repositoryDatabase) {

        RevTreeBuilder treeBuilder = treeCache.get(treePath);
        if (treeBuilder == null) {
            if (NodeRef.ROOT.equals(treePath)) {
                treeBuilder = root.builder(repositoryDatabase);
            } else {
                Optional<NodeRef> treeRef = command(FindTreeChild.class).setIndex(false)
                        .setParent(root).setChildPath(treePath).call();
                if (treeRef.isPresent()) {
                    metadataCache.put(treePath, treeRef.get().getMetadataId());
                    treeBuilder = command(RevObjectParse.class)
                            .setObjectId(treeRef.get().objectId()).call(RevTree.class).get()
                            .builder(repositoryDatabase);
                } else {
                    metadataCache.put(treePath, fallbackMetadataId);
                    treeBuilder = new RevTreeBuilder(repositoryDatabase);
                }
            }
            treeCache.put(treePath, treeBuilder);
        }
        return treeBuilder;
    }

    private RevTree getTree(ObjectId treeId) {
        return stagingDatabase().getTree(treeId);
    }

    private void deepMove(Node ref) {
        Supplier<Node> objectRef = Suppliers.ofInstance(ref);
        command(DeepMove.class).setObjectRef(objectRef).setToIndex(false).call();
    }

    /**
     * @return the resolved root tree id
     */
    private ObjectId resolveRootTreeId() {
        if (oldRoot != null) {
            RevTree rootTree = oldRoot.get();
            return rootTree.getId();
        }
        ObjectId targetTreeId = command(ResolveTreeish.class).setTreeish(Ref.HEAD).call().get();
        return targetTreeId;
    }

    /**
     * @return the resolved root tree
     */
    private RevTree resolveRootTree() {
        if (oldRoot != null) {
            return oldRoot.get();
        }
        final ObjectId targetTreeId = resolveRootTreeId();
        return stagingDatabase().getTree(targetTreeId);
    }

    private ObjectId writeBack(RevTreeBuilder root, final RevTree tree, final String pathToTree,
            final ObjectId metadataId) {

        return command(WriteBack.class).setAncestor(root).setAncestorPath("").setTree(tree)
                .setChildPath(pathToTree).setToIndex(false).setMetadataId(metadataId).call();
    }

    /**
     * Indicates that the WriteTree operation shall not attempt to move the objects from the staging
     * to the objects database, since they're known to already be present in the objects database.
     * Used usually when {@link #setDiffSupplier(Supplier)} is also set and the calling code takes
     * care of storing the features, types, and trees in the objectdatabase.
     */
    public WriteTree dontMoveObjects() {
        this.moveObjects = false;
        return this;
    }

}
