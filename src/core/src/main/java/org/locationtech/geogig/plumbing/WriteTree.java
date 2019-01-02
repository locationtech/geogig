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

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
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
 * @see DeepCopy
 * @see ResolveTreeish
 * @see CreateTree
 * @see RevObjectParse
 */
public class WriteTree extends AbstractGeoGigOp<ObjectId> {

    private Supplier<RevTree> oldRoot;

    private Supplier<AutoCloseableIterator<DiffEntry>> diffSupplier = null;

    /**
     * Where to copy objects from, if at all.
     */
    @Nullable
    private ObjectStore fromDb;

    /**
     * @param oldRoot a supplier for the old root tree
     * @return {@code this}
     */
    public WriteTree setOldRoot(Supplier<RevTree> oldRoot) {
        this.oldRoot = oldRoot;
        return this;
    }

    public WriteTree setDiffSupplier(
            @Nullable Supplier<AutoCloseableIterator<DiffEntry>> diffSupplier) {
        this.diffSupplier = diffSupplier;
        return this;
    }

    /**
     * If provided, objects pointed at by the {@link #setDiffSupplier(Supplier) diff supplier} will
     * be copied from {@code fromDb} to the local repository; otherwise only the new tree will be
     * created without copying any object.
     */
    public WriteTree setCopyFrom(ObjectStore fromDb) {
        this.fromDb = fromDb;
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
        Preconditions.checkState(diffSupplier != null, "No diff supplier was provided");
        final ProgressListener progress = getProgressListener();

        final RevTree oldRootTree = resolveRootTree();
        final ObjectDatabase repositoryDatabase = objectDatabase();

        Map<String, RevTreeBuilder> repositoryChangedTrees = Maps.newHashMap();
        Map<String, NodeRef> indexChangedTrees = Maps.newHashMap();
        Map<String, ObjectId> changedTreesMetadataId = Maps.newHashMap();
        Set<String> deletedTrees = Sets.newHashSet();
        final boolean copyObjects = this.fromDb != null;
        NodeRef ref;
        RevTree stageHead = stagingArea().getTree();
        try (final AutoCloseableIterator<DiffEntry> diffs = diffSupplier.get()) {
            if (!diffs.hasNext()) {
                return oldRootTree.getId();
            }
            if (progress.isCanceled()) {
                return null;
            }

            while (diffs.hasNext()) {
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
                    // this is to avoid re-creating the parentTree for a feature delete after its
                    // parent
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

                resolveSourceTreeRef(parentPath, indexChangedTrees, changedTreesMetadataId,
                        stageHead);

                Preconditions.checkState(parentTree != null);

                if (isDelete) {
                    Node oldNode = diff.getOldObject().getNode();
                    parentTree.remove(oldNode);
                    if (TYPE.TREE.equals(type)) {
                        deletedTrees.add(ref.path());
                    }
                } else {
                    if (copyObjects && ref.getType().equals(TYPE.TREE)) {
                        RevTree tree = fromDb.getTree(ref.getObjectId());
                        if (!ref.getMetadataId().isNull()) {
                            repositoryDatabase.put(fromDb.getFeatureType(ref.getMetadataId()));
                        }
                        if (tree.isEmpty()) {
                            repositoryDatabase.put(tree);
                        } else {
                            continue;
                        }
                    } else if (copyObjects) {
                        deepCopy(ref.getNode());
                    }
                    parentTree.put(ref.getNode());
                }
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

        UpdateTree updateTree = command(UpdateTree.class).setRoot(newTargetRootId);
        for (Entry<String, RevTreeBuilder> e : repositoryChangedTrees.entrySet()) {
            String treePath = e.getKey();
            ObjectId metadataId = changedTreesMetadataId.get(treePath);
            RevTreeBuilder treeBuilder = e.getValue();
            RevTree tree = treeBuilder.build();

            NodeRef newTreeRef = NodeRef.tree(treePath, tree.getId(), metadataId);
            updateTree.setChild(newTreeRef);
        }
        final RevTree newRoot = updateTree.call();

        progress.complete();

        return newRoot.getId();
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
                treeRef = command(FindTreeChild.class).setParent(stageHead).setChildPath(parentPath)
                        .call();
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
                treeBuilder = RevTreeBuilder.builder(repositoryDatabase, root);
            } else {
                Optional<NodeRef> treeRef = command(FindTreeChild.class).setParent(root)
                        .setChildPath(treePath).call();
                if (treeRef.isPresent()) {
                    metadataCache.put(treePath, treeRef.get().getMetadataId());
                    treeBuilder = RevTreeBuilder.builder(repositoryDatabase,
                            command(RevObjectParse.class).setObjectId(treeRef.get().getObjectId())
                                    .call(RevTree.class).get());
                } else {
                    metadataCache.put(treePath, fallbackMetadataId);
                    treeBuilder = RevTreeBuilder.builder(repositoryDatabase);
                }
            }
            treeCache.put(treePath, treeBuilder);
        }
        return treeBuilder;
    }

    private void deepCopy(Node ref) {
        Supplier<Node> objectRef = Suppliers.ofInstance(ref);
        command(DeepCopy.class).setObjectRef(objectRef).setFrom(fromDb).call();
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
        return objectDatabase().getTree(targetTreeId);
    }

}
