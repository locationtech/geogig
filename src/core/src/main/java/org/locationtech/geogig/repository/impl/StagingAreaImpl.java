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
import static java.util.Spliterators.spliterator;
import static java.util.Spliterators.spliteratorUnknownSize;
import static org.locationtech.geogig.model.RevTree.EMPTY;
import static org.locationtech.geogig.model.RevTree.EMPTY_TREE_ID;
import static org.locationtech.geogig.storage.impl.PersistedIterable.newStringIterable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.DiffCount;
import org.locationtech.geogig.plumbing.DiffIndex;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateTree;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.impl.PersistedIterable;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

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
        Optional<ObjectId> head;
        Optional<ObjectId> stageHead;
        head = context.command(ResolveTreeish.class).setTreeish(Ref.HEAD).call();
        stageHead = context.command(ResolveTreeish.class).setTreeish(Ref.STAGE_HEAD).call();
        return head.equals(stageHead);
    }

    private static final @ThreadSafe class StageState {

        final Context context;

        final RevTree currentIndexHead;

        final UpdateTree updateTree;

        final ConflictsDatabase conflictsDb;

        final private ProgressListener progress;

        // persisted iterable for very large number of matching conflict paths
        final PersistedIterable<String> conflictsCleanup;

        // local buffer to check for matching conflict paths every N diff entries
        final Set<String> conflictPathBuffer = Sets.newConcurrentHashSet();

        final boolean hasConflicts;

        final Map<String, RevTreeBuilder> featureTypeTrees = Maps.newConcurrentMap();

        final Map<String, NodeRef> currentFeatureTypeRefs = Maps.newConcurrentMap();

        final Set<String> removedTrees = Sets.newConcurrentHashSet();

        StageState(Context context, PersistedIterable<String> conflictsCleanup,
                RevTree currentIndexHead, ProgressListener progress) {
            this.context = context;
            this.conflictsDb = context.conflictsDatabase();
            this.conflictsCleanup = conflictsCleanup;
            this.currentIndexHead = currentIndexHead;
            this.updateTree = context.command(UpdateTree.class).setRoot(currentIndexHead);
            this.progress = progress;
            this.hasConflicts = conflictsDb.hasConflicts(null);
        }

        public RevTreeBuilder getTreeBuilder(final NodeRef featureRef) {
            checkArgument(TYPE.FEATURE.equals(featureRef.getType()));

            final String typeTreePath = featureRef.getParentPath();
            RevTreeBuilder typeTreeBuilder;
            typeTreeBuilder = featureTypeTrees.computeIfAbsent(typeTreePath,
                    path -> newTreeBuilder(featureRef));
            return typeTreeBuilder;
        }

        private RevTreeBuilder newTreeBuilder(final NodeRef featureRef) {
            final String typeTreePath = featureRef.getParentPath();
            NodeRef typeTreeRef = context.command(FindTreeChild.class).setParent(currentIndexHead)
                    .setChildPath(typeTreePath).call().orNull();

            final RevTree currentTypeTree;
            if (typeTreeRef == null) {
                ObjectId metadataId = featureRef.getMetadataId();
                Node parentNode = RevObjectFactory.defaultInstance().createNode(
                        NodeRef.nodeFromPath(typeTreePath), EMPTY_TREE_ID, metadataId, TYPE.TREE,
                        null, null);
                typeTreeRef = NodeRef.create(NodeRef.parentPath(typeTreePath), parentNode);
                currentTypeTree = EMPTY;
            } else {
                currentTypeTree = context.objectDatabase().getTree(typeTreeRef.getObjectId());
            }
            RevTreeBuilder typeTreeBuilder = RevTreeBuilder.builder(context.objectDatabase(),
                    currentTypeTree);
            currentFeatureTypeRefs.put(typeTreePath, typeTreeRef);
            return typeTreeBuilder;
        }

        public void updateConflicts(final @Nullable DiffEntry diff, int buffLimit) {
            if (hasConflicts) {
                if (diff != null) {
                    conflictPathBuffer.add(diff.path());
                }
                if (conflictPathBuffer.size() >= buffLimit) {
                    Set<String> lookup = null;
                    synchronized (conflictPathBuffer) {
                        if (conflictPathBuffer.size() >= buffLimit) {
                            lookup = new HashSet<>(conflictPathBuffer);
                            conflictPathBuffer.clear();
                        }
                    }
                    if (lookup != null) {
                        Set<String> matches = conflictsDb.findConflicts(null, lookup);
                        if (!matches.isEmpty()) {
                            conflictsCleanup.addAll(matches);
                        }
                    }
                }
            }
        }

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

        progress.started();
        progress.setMaxProgress(numChanges > 0 ? numChanges : -1);
        progress.setProgressIndicator(p -> String.format("%,d", (long) p.getProgress()));

        final RevTree currentIndexHead = getTree();
        final ConflictsDatabase conflictsDb = conflictsDatabase();

        try (PersistedIterable<String> conflictsCleanup = newStringIterable(10_000, true)) {

            StageState state = new StageState(context, conflictsCleanup, currentIndexHead,
                    progress);

            Stream<DiffEntry> stream;
            {
                final int characteristics = Spliterator.NONNULL | Spliterator.IMMUTABLE;
                Spliterator<DiffEntry> spliterator;
                if (numChanges > 0) {
                    spliterator = spliterator(unstaged, numChanges, characteristics);
                } else {
                    spliterator = spliteratorUnknownSize(unstaged, characteristics);
                }
                stream = StreamSupport.stream(spliterator, true);
            }

            stream.forEach(diff -> stage(state, diff));

            state.updateConflicts(null, 1);

            for (Map.Entry<String, RevTreeBuilder> entry : state.featureTypeTrees.entrySet()) {

                final String changedTreePath = entry.getKey();
                final NodeRef currentTreeRef = state.currentFeatureTypeRefs.get(changedTreePath);
                checkState(null != currentTreeRef);
                final RevTreeBuilder changedTreeBuilder = entry.getValue();
                if (state.removedTrees.contains(changedTreePath)) {
                    changedTreeBuilder.dispose();
                    continue;
                }

                progress.setMaxProgress(-1);
                progress.setProgress(0);
                if (!NodeRef.ROOT.equals(changedTreePath)) {
                    progress.setDescription("Building final tree " + changedTreePath);
                }
                Stopwatch st = Stopwatch.createStarted();
                final RevTree changedTree = changedTreeBuilder.build();
                progress.setDescription(
                        String.format("Tree %s staged in %s", changedTreePath, st.stop()));
                final Envelope newBounds = SpatialOps.boundsOf(changedTree);
                final NodeRef newTreeRef = currentTreeRef.update(changedTree.getId(), newBounds);
                state.updateTree.setChild(newTreeRef);
            }
            RevTree newRootTree = state.updateTree.call();
            updateStageHead(newRootTree.getId());

            // remove conflicts once the STAGE_HEAD was updated
            if (state.hasConflicts) {
                progress.setDescription(
                        String.format("Removing %,d merged conflicts...", conflictsCleanup.size()));
                // Stopwatch sw = Stopwatch.createStarted();
                conflictsDb.removeConflicts(null, conflictsCleanup);

                // System.err.printf("tried to remove paths in %s\n", sw.stop());
                long remainingConflicts = conflictsDb.getCountByPrefix(null, null);
                progress.setDescription(
                        String.format("Done. %,d unmerged conflicts.", remainingConflicts));
            } else {
                progress.setDescription("Done.");
            }
        } finally {
            progress.setProgressIndicator(null);
        }
        progress.complete();
    }

    private boolean stage(StageState state, DiffEntry diff) {

        state.updateConflicts(diff, 100_000);
        final String parentPath = diff.parentPath();
        /*
         * TODO: revisit, ideally the list of diff entries would come with one single entry for the
         * whole removed tree instead of that one and every single children of it.
         */
        if (state.removedTrees.contains(parentPath)) {
            return true;
        }
        if (null == parentPath) {
            // it is the root tree that's been changed, update head and ignore anything else
            ObjectId newRoot = diff.newObjectId();
            updateStageHead(newRoot);
            state.progress.complete();
            return false;
        }

        // RevTreeBuilder parentTree = getParentTree(currentIndexHead, parentPath,
        // featureTypeTrees, currentFeatureTypeRefs);
        state.progress.incrementBy(1f);
        final NodeRef oldObject = diff.getOldObject();
        final NodeRef newObject = diff.getNewObject();
        final ChangeType changeType = diff.changeType();
        switch (changeType) {
        case REMOVED:
            if (TYPE.TREE.equals(oldObject.getType())) {
                String fullPath = diff.path();
                state.removedTrees.add(fullPath);
                state.updateTree.removeChildTree(fullPath);
            } else {
                state.getTreeBuilder(oldObject).remove(oldObject.getNode());
            }
            break;
        default:
            checkArgument(newObject != null);
            if (TYPE.TREE.equals(newObject.getType())) {
                state.updateTree.setChild(newObject);
            } else {
                state.getTreeBuilder(newObject).put(newObject.getNode());
            }
            break;
        }
        return true;
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
