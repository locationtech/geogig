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
import static com.google.common.base.Preconditions.checkState;
import static org.locationtech.geogig.model.RevTree.EMPTY;
import static org.locationtech.geogig.model.RevTree.EMPTY_TREE_ID;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.model.impl.RevTreeBuilder;
import org.locationtech.geogig.plumbing.DiffCount;
import org.locationtech.geogig.plumbing.DiffIndex;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateTree;
import org.locationtech.geogig.repository.AutoCloseableIterator;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DiffEntry;
import org.locationtech.geogig.repository.DiffEntry.ChangeType;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.impl.PersistedIterable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Default implementation of {@link StagingArea}
 */
public class StagingAreaImpl implements StagingArea {

    private Context context;

    @Inject
    public StagingAreaImpl(final Context context) {
        Preconditions.checkNotNull(context);
        this.context = context;
    }

    @Override
    public ConflictsDatabase conflictsDatabase() {
        return context.conflictsDatabase();
    }

    /**
     * Updates the STAGE_HEAD ref to the specified tree.
     * 
     * @param newTree the tree to set as the new STAGE_HEAD
     */
    @Override
    public void updateStageHead(ObjectId newTree) {
        context.command(UpdateRef.class).setName(Ref.STAGE_HEAD).setNewValue(newTree).call();
    }

    /**
     * @return the tree represented by STAGE_HEAD. If there is no tree set at STAGE_HEAD, it will
     *         return the HEAD tree (no unstaged changes).
     */
    @Override
    public RevTree getTree() {
        Optional<ObjectId> stageTreeId = context.command(ResolveTreeish.class)
                .setTreeish(Ref.STAGE_HEAD).call();

        RevTree stageTree = EMPTY;

        if (stageTreeId.isPresent()) {
            if (!stageTreeId.get().equals(EMPTY_TREE_ID)) {
                stageTree = context.objectDatabase().getTree(stageTreeId.get());
            }
        } else {
            // Stage tree was not resolved, update it to the head.
            Optional<ObjectId> headTreeId = context.command(ResolveTreeish.class)
                    .setTreeish(Ref.HEAD).call();

            if (headTreeId.isPresent() && !headTreeId.get().equals(EMPTY_TREE_ID)) {
                stageTree = context.objectDatabase().getTree(headTreeId.get());
                updateStageHead(stageTree.getId());
            }
        }
        return stageTree;
    }

    /**
     * @param path the path of the {@link Node} to find
     * @return the {@code Node} for the feature at the specified path if it exists in the index,
     *         otherwise {@link Optional#absent()}
     */
    @Override
    public Optional<Node> findStaged(final String path) {
        Optional<NodeRef> entry = context.command(FindTreeChild.class).setParent(getTree())
                .setChildPath(path).call();
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
     * @param numChanges number of unstaged changes, or negative if unknown
     */
    @Override
    public void stage(final ProgressListener progress, final Iterator<DiffEntry> unstaged,
            final long numChanges) {
        int i = 0;
        progress.started();

        final RevTree currentIndexHead = getTree();

        Map<String, CanonicalTreeBuilder> featureTypeTrees = Maps.newHashMap();
        Map<String, NodeRef> currentFeatureTypeRefs = Maps.newHashMap();
        Set<String> removedTrees = Sets.newHashSet();

        final ConflictsDatabase conflictsDb = conflictsDatabase();
        final boolean hasConflicts = conflictsDb.hasConflicts(null);

        // persisted iterable for very large number of matching conflict paths
        PersistedIterable<String> pathsForConflictCleanup = null;
        // local buffer to check for matching conflict paths every N diff entries
        Set<String> pathBuffer = null;
        if (hasConflicts) {
            pathsForConflictCleanup = PersistedIterable.newStringIterable(10_000, true);
            pathBuffer = new HashSet<>();
        }
        try {
            UpdateTree updateTree = context.command(UpdateTree.class).setRoot(currentIndexHead);

            while (unstaged.hasNext()) {
                final DiffEntry diff = unstaged.next();
                final String fullPath = diff.path();
                if (hasConflicts) {
                    pathBuffer.add(fullPath);
                    if (pathBuffer.size() == 100_000) {
                        // Stopwatch s = Stopwatch.createStarted();
                        Set<String> matches = conflictsDb.findConflicts(null, pathBuffer);
                        // System.err.printf("queried %,d possible conflicts in %s\n",
                        // pathBuffer.size(), s.stop());
                        if (!matches.isEmpty()) {
                            pathsForConflictCleanup.addAll(matches);
                        }
                        pathBuffer.clear();
                    }
                }
                final String parentPath = NodeRef.parentPath(fullPath);
                /*
                 * TODO: revisit, ideally the list of diff entries would come with one single entry
                 * for the whole removed tree instead of that one and every single children of it.
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

                // RevTreeBuilder parentTree = getParentTree(currentIndexHead, parentPath,
                // featureTypeTrees, currentFeatureTypeRefs);

                i++;
                if (numChanges > 0) {
                    progress.setProgress((float) (i * 100) / numChanges);
                } else {
                    progress.setProgress(i);
                }
                final NodeRef oldObject = diff.getOldObject();
                final NodeRef newObject = diff.getNewObject();
                final ChangeType changeType = diff.changeType();
                switch (changeType) {
                case REMOVED:
                    if (TYPE.TREE.equals(oldObject.getType())) {
                        updateTree.removeChildTree(fullPath);
                        removedTrees.add(fullPath);
                    } else {
                        getTreeBuilder(currentIndexHead, oldObject, featureTypeTrees,
                                currentFeatureTypeRefs).remove(oldObject.name());
                    }
                    break;
                default:
                    checkArgument(newObject != null);
                    if (TYPE.TREE.equals(newObject.getType())) {
                        updateTree.setChild(newObject);
                    } else {
                        getTreeBuilder(currentIndexHead, newObject, featureTypeTrees,
                                currentFeatureTypeRefs).put(newObject.getNode());
                    }
                    break;
                }
            }

            for (Map.Entry<String, CanonicalTreeBuilder> entry : featureTypeTrees.entrySet()) {
                final String changedTreePath = entry.getKey();
                final NodeRef currentTreeRef = currentFeatureTypeRefs.get(changedTreePath);
                checkState(null != currentTreeRef);
                final RevTreeBuilder changedTreeBuilder = entry.getValue();
                if (!NodeRef.ROOT.equals(changedTreePath)) {
                    progress.setDescription("Building final tree " + changedTreePath);
                }
                final RevTree changedTree = changedTreeBuilder.build();
                final Envelope newBounds = SpatialOps.boundsOf(changedTree);
                final NodeRef newTreeRef = currentTreeRef.update(changedTree.getId(), newBounds);
                updateTree.setChild(newTreeRef);
            }
            RevTree newRootTree = updateTree.call();
            updateStageHead(newRootTree.getId());

            // remove conflicts once the STAGE_HEAD was updated
            if (hasConflicts) {
                if (!pathBuffer.isEmpty()) {
                    // Stopwatch s = Stopwatch.createStarted();
                    Set<String> matches = conflictsDb.findConflicts(null, pathBuffer);
                    // System.err.printf("queried %,d possible conflicts in %s\n",
                    // pathBuffer.size(), s.stop());
                    if (!matches.isEmpty()) {
                        pathsForConflictCleanup.addAll(matches);
                    }
                    pathBuffer.clear();
                }

                if (pathsForConflictCleanup.size() > 0L) {
                    progress.setDescription(String.format("Removing %,d merged conflicts...",
                            pathsForConflictCleanup.size()));
                    // Stopwatch sw = Stopwatch.createStarted();
                    conflictsDb.removeConflicts(null, pathsForConflictCleanup);
                }
                // System.err.printf("tried to remove paths in %s\n", sw.stop());
                long remainingConflicts = conflictsDb.getCountByPrefix(null, null);
                progress.setDescription(
                        String.format("Done. %,d unmerged conflicts.", remainingConflicts));
            } else {
                progress.setDescription("Done.");
            }
        } finally {
            if (pathsForConflictCleanup != null) {
                pathsForConflictCleanup.close();
            }
        }
        progress.complete();
    }

    private CanonicalTreeBuilder getTreeBuilder(RevTree currentIndexHead, NodeRef featureRef,
            Map<String, CanonicalTreeBuilder> featureTypeTrees,
            Map<String, NodeRef> currentFeatureTypeRefs) {

        checkArgument(TYPE.FEATURE.equals(featureRef.getType()));

        final String typeTreePath = featureRef.getParentPath();
        CanonicalTreeBuilder typeTreeBuilder = featureTypeTrees.get(typeTreePath);
        if (typeTreeBuilder == null) {
            NodeRef typeTreeRef = context.command(FindTreeChild.class).setParent(currentIndexHead)
                    .setChildPath(typeTreePath).call().orNull();

            final RevTree currentTypeTree;
            if (typeTreeRef == null) {
                ObjectId metadataId = featureRef.getMetadataId();
                Node parentNode = Node.tree(NodeRef.nodeFromPath(typeTreePath), EMPTY_TREE_ID,
                        metadataId);
                typeTreeRef = NodeRef.create(NodeRef.parentPath(typeTreePath), parentNode);
                currentTypeTree = EMPTY;
            } else {
                currentTypeTree = context.objectDatabase().getTree(typeTreeRef.getObjectId());
            }
            typeTreeBuilder = CanonicalTreeBuilder.create(context.objectDatabase(),
                    currentTypeTree);
            currentFeatureTypeRefs.put(typeTreePath, typeTreeRef);
            featureTypeTrees.put(typeTreePath, typeTreeBuilder);
        }
        return typeTreeBuilder;
    }

    /**
     * @param pathFilter if specified, only changes that match the filter will be returned
     * @return an iterator for all of the differences between STAGE_HEAD and HEAD based on the path
     *         filter.
     */
    @Override
    public AutoCloseableIterator<DiffEntry> getStaged(final @Nullable List<String> pathFilters) {
        AutoCloseableIterator<DiffEntry> unstaged = context.command(DiffIndex.class)
                .setFilter(pathFilters).setReportTrees(true).call();
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
    public long countConflicted(String pathFilter) {
        return conflictsDatabase().getCountByPrefix(null, null);
    }

    @Override
    public Iterator<Conflict> getConflicted(@Nullable String pathFilter) {
        return conflictsDatabase().getByPrefix(null, pathFilter);
    }

}
