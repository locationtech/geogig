/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.repository;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.api.plumbing.DiffCount;
import org.locationtech.geogig.api.plumbing.DiffIndex;
import org.locationtech.geogig.api.plumbing.FindOrCreateSubtree;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.ResolveTreeish;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.WriteBack;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.DiffObjectCount;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.di.Singleton;
import org.locationtech.geogig.storage.StagingDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

/**
 * The Index keeps track of the changes that have been staged, but not yet committed to the
 * repository.
 * <p>
 * The Index uses an {@link StagingDatabase object database} as storage for the staged changes. This
 * allows for really large operations not to eat up too much heap, and also works better and allows
 * for easier implementation of operations that need to manipulate the index.
 * <p>
 * The Index database is a composite of its own ObjectDatabase and the repository's. Object look ups
 * against the index first search on the index db, and if not found defer to the repository object
 * db.
 * <p>
 * Internally, finding out what changes are unstaged is a matter of comparing (through a diff tree
 * walk) the working tree and the staged changes tree. And finding out what changes are staged to be
 * committed is performed through a diff tree walk comparing the staged changes tree and the
 * repository's head tree.
 * 
 */
@Singleton
public class Index implements StagingArea {

    private Context context;

    @Inject
    public Index(final Context context) {
        Preconditions.checkNotNull(context);
        this.context = context;
    }

    /**
     * @return the staging database.
     */
    @Override
    public StagingDatabase getDatabase() {
        return context.stagingDatabase();
    }

    /**
     * Updates the STAGE_HEAD ref to the specified tree.
     * 
     * @param newTree the tree to set as the new STAGE_HEAD
     */
    @Override
    public void updateStageHead(ObjectId newTree) {
        context.command(UpdateRef.class).setName(Ref.STAGE_HEAD).setNewValue(newTree).call();
        getDatabase().removeConflicts(null);
    }

    /**
     * @return the tree represented by STAGE_HEAD. If there is no tree set at STAGE_HEAD, it will
     *         return the HEAD tree (no unstaged changes).
     */
    @Override
    public RevTree getTree() {
        Optional<ObjectId> stageTreeId = context.command(ResolveTreeish.class)
                .setTreeish(Ref.STAGE_HEAD).call();

        RevTree stageTree = RevTree.EMPTY;

        if (stageTreeId.isPresent()) {
            if (!stageTreeId.get().equals(RevTree.EMPTY_TREE_ID)) {
                stageTree = context.stagingDatabase().getTree(stageTreeId.get());
            }
        } else {
            // Stage tree was not resolved, update it to the head.
            Optional<ObjectId> headTreeId = context.command(ResolveTreeish.class)
                    .setTreeish(Ref.HEAD).call();

            if (headTreeId.isPresent() && !headTreeId.get().equals(RevTree.EMPTY_TREE_ID)) {
                stageTree = context.objectDatabase().getTree(headTreeId.get());
                updateStageHead(stageTree.getId());
            }
        }
        return stageTree;
    }

    /**
     * @return a supplier for the index.
     */
    private Supplier<RevTreeBuilder> getTreeSupplier() {
        Supplier<RevTreeBuilder> supplier = new Supplier<RevTreeBuilder>() {
            @Override
            public RevTreeBuilder get() {
                return getTree().builder(getDatabase());
            }
        };
        return Suppliers.memoize(supplier);
    }

    /**
     * @param path the path of the {@link Node} to find
     * @return the {@code Node} for the feature at the specified path if it exists in the index,
     *         otherwise {@link Optional#absent()}
     */
    @Override
    public Optional<Node> findStaged(final String path) {
        Optional<NodeRef> entry = context.command(FindTreeChild.class).setIndex(true)
                .setParent(getTree()).setChildPath(path).call();
        if (entry.isPresent()) {
            return Optional.of(entry.get().getNode());
        } else {
            return Optional.absent();
        }
    }

    /**
     * Returns true if there are no unstaged changes, false otherwise
     */
    public boolean isClean() {
        Optional<ObjectId> resolved = context.command(ResolveTreeish.class).setTreeish(Ref.HEAD)
                .call();
        ObjectId indexTreeId = resolved.get();
        return getTree().getId().equals(indexTreeId);
    }

    /**
     * Stages the changes indicated by the {@link DiffEntry} iterator.
     * 
     * @param progress the progress listener for the process
     * @param unstaged an iterator for the unstaged changes
     * @param numChanges number of unstaged changes
     */
    @Override
    public void stage(final ProgressListener progress, final Iterator<DiffEntry> unstaged,
            final long numChanges) {
        int i = 0;
        progress.started();

        final RevTree currentIndexHead = getTree();

        Map<String, RevTreeBuilder> parentTress = Maps.newHashMap();
        Map<String, ObjectId> parentMetadataIds = Maps.newHashMap();
        Set<String> removedTrees = Sets.newHashSet();
        StagingDatabase database = getDatabase();
        while (unstaged.hasNext()) {
            final DiffEntry diff = unstaged.next();
            final String fullPath = diff.oldPath() == null ? diff.newPath() : diff.oldPath();
            final String parentPath = NodeRef.parentPath(fullPath);
            /*
             * TODO: revisit, ideally the list of diff entries would come with one single entry for
             * the whole removed tree instead of that one and every single children of it.
             */
            if (removedTrees.contains(parentPath)) {
                continue;
            }
            if (null == parentPath) {
                // it is the root tree that's been changed, update head and ignore anything else
                ObjectId newRoot = diff.newObjectId();
                updateStageHead(newRoot);
                progress.setProgress(100f);
                progress.complete();
                return;
            }
            RevTreeBuilder parentTree = getParentTree(currentIndexHead, parentPath, parentTress,
                    parentMetadataIds);

            i++;
            progress.setProgress((float) (i * 100) / numChanges);

            NodeRef oldObject = diff.getOldObject();
            NodeRef newObject = diff.getNewObject();
            if (newObject == null) {
                // Delete
                parentTree.remove(oldObject.name());
                if (TYPE.TREE.equals(oldObject.getType())) {
                    removedTrees.add(oldObject.path());
                }
            } else if (oldObject == null) {
                // Add
                Node node = newObject.getNode();
                parentTree.put(node);
                parentMetadataIds.put(newObject.path(), newObject.getMetadataId());
            } else {
                // Modify
                Node node = newObject.getNode();
                parentTree.put(node);
            }

            database.removeConflict(null, fullPath);
        }

        ObjectId newRootTree = currentIndexHead.getId();

        for (Map.Entry<String, RevTreeBuilder> entry : parentTress.entrySet()) {
            String changedTreePath = entry.getKey();
            RevTreeBuilder changedTreeBuilder = entry.getValue();
            RevTree changedTree = changedTreeBuilder.build();
            ObjectId parentMetadataId = parentMetadataIds.get(changedTreePath);
            if (NodeRef.ROOT.equals(changedTreePath)) {
                // root
                database.put(changedTree);
                newRootTree = changedTree.getId();
            } else {
                // parentMetadataId = parentMetadataId == null ?
                Supplier<RevTreeBuilder> rootTreeSupplier = getTreeSupplier();
                newRootTree = context.command(WriteBack.class).setAncestor(rootTreeSupplier)
                        .setChildPath(changedTreePath).setMetadataId(parentMetadataId)
                        .setToIndex(true).setTree(changedTree).call();
            }
            updateStageHead(newRootTree);
        }

        progress.complete();
    }

    /**
     * @param currentIndexHead
     * @param diffEntry
     * @param parentTress
     * @param parentMetadataIds
     * @return
     */
    private RevTreeBuilder getParentTree(RevTree currentIndexHead, String parentPath,
            Map<String, RevTreeBuilder> parentTress, Map<String, ObjectId> parentMetadataIds) {

        RevTreeBuilder parentBuilder = parentTress.get(parentPath);
        if (parentBuilder == null) {
            ObjectId parentMetadataId = null;
            if (NodeRef.ROOT.equals(parentPath)) {
                parentBuilder = currentIndexHead.builder(getDatabase());
            } else {
                Optional<NodeRef> parentRef = context.command(FindTreeChild.class).setIndex(true)
                        .setParent(currentIndexHead).setChildPath(parentPath).call();

                if (parentRef.isPresent()) {
                    parentMetadataId = parentRef.get().getMetadataId();
                }

                parentBuilder = context.command(FindOrCreateSubtree.class)
                        .setParent(Suppliers.ofInstance(Optional.of(getTree()))).setIndex(true)
                        .setChildPath(parentPath).call().builder(getDatabase());
            }
            parentTress.put(parentPath, parentBuilder);
            if (parentMetadataId != null) {
                parentMetadataIds.put(parentPath, parentMetadataId);
            }
        }
        return parentBuilder;
    }

    /**
     * @param pathFilter if specified, only changes that match the filter will be returned
     * @return an iterator for all of the differences between STAGE_HEAD and HEAD based on the path
     *         filter.
     */
    @Override
    public Iterator<DiffEntry> getStaged(final @Nullable List<String> pathFilters) {
        Iterator<DiffEntry> unstaged = context.command(DiffIndex.class).setFilter(pathFilters)
                .setReportTrees(true).call();
        return unstaged;
    }

    /**
     * @param pathFilter if specified, only changes that match the filter will be returned
     * @return the number differences between STAGE_HEAD and HEAD based on the path filter.
     */
    @Override
    public DiffObjectCount countStaged(final @Nullable List<String> pathFilters) {
        DiffObjectCount count = context.command(DiffCount.class).setOldVersion(Ref.HEAD)
                .setNewVersion(Ref.STAGE_HEAD).setFilter(pathFilters).call();

        return count;
    }

    @Override
    public int countConflicted(String pathFilter) {
        return getDatabase().getConflicts(null, pathFilter).size();
    }

    @Override
    public List<Conflict> getConflicted(@Nullable String pathFilter) {
        return getDatabase().getConflicts(null, pathFilter);
    }
}
