/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.DefaultProgressListener;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.DiffCount;
import org.locationtech.geogig.api.plumbing.LsTreeOp;
import org.locationtech.geogig.api.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.api.plumbing.ResolveTreeish;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.DiffObjectCount;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Removes a feature or a tree from the working tree and index
 * 
 */
@CanRunDuringConflict
public class RemoveOp extends AbstractGeoGigOp<DiffObjectCount> {

    private List<String> pathsToRemove;

    private boolean recursive;

    public RemoveOp() {
        this.pathsToRemove = new ArrayList<String>();
    }

    /**
     * @param path a path to remove
     * @return {@code this}
     */
    public RemoveOp addPathToRemove(final String path) {
        pathsToRemove.add(path);
        return this;
    }

    public RemoveOp setRecursive(boolean recursive) {
        this.recursive = recursive;
        return this;
    }

    /**
     * @see java.util.concurrent.Callable#call()
     */
    protected DiffObjectCount _call() {

        // Check that all paths are valid and exist
        final WorkingTree workingTree = workingTree();

        final RevTree initialWorkTree = workingTree.getTree();

        final Map<String, NodeRef> deleteTrees = getDeleteTrees(initialWorkTree);
        final List<String> deleteFeatures = filterFeatures(deleteTrees.keySet());

        final ProgressListener listener = getProgressListener();

        for (Entry<String, NodeRef> e : deleteTrees.entrySet()) {
            String treePath = e.getKey();

            if (!recursive) {
                throw new IllegalArgumentException(
                        String.format("Cannot remove tree %s if -r is not specified", treePath));
            }

            ObjectId newWorkHead = workingTree.delete(treePath);
            if (initialWorkTree.getId().equals(newWorkHead)) {

            } else {
                getProgressListener().setDescription(String.format("Deleted %s tree", treePath));
            }
            if (listener.isCanceled()) {
                return null;
            }
        }

        if (!deleteFeatures.isEmpty()) {
            getProgressListener().setDescription("Deleting features...");
            workingTree.delete(deleteFeatures.iterator(), listener);
        }

        getProgressListener().setDescription("Staging changes...");

        final RevTree finalWorkTree = workingTree.getTree();

        Optional<ObjectId> headTree = command(ResolveTreeish.class).setTreeish(Ref.HEAD).call();
        ObjectId stageTree = index().getTree().getId();
        final boolean nothingElseStaged = headTree.isPresent() && headTree.get().equals(stageTree);
        if (nothingElseStaged) {
            index().updateStageHead(finalWorkTree.getId());
        } else {
            stageDeletes(deleteTrees.values().iterator(), deleteFeatures.iterator());
        }

        getProgressListener().setDescription("Computing result count...");
        List<String> paths = new ArrayList<String>(deleteTrees.keySet());
        paths.addAll(deleteFeatures);
        final DiffObjectCount stageCount = command(DiffCount.class)
                .setOldTree(initialWorkTree.getId())//
                .setNewTree(finalWorkTree.getId())//
                .setFilter(paths)//
                .call();

        return stageCount;
    }

    private void stageDeletes(Iterator<NodeRef> trees, Iterator<String> features) {
        final StagingArea index = index();

        Iterator<DiffEntry> treeDeletes = Iterators.transform(trees,
                (treeRef) -> new DiffEntry(treeRef, null));

        Iterator<DiffEntry> featureDeletes = Iterators.transform(features, (featurePath) -> {
            Node node = Node.create(NodeRef.nodeFromPath(featurePath), ObjectId.NULL, ObjectId.NULL,
                    TYPE.FEATURE, null);
            String parentPath = NodeRef.parentPath(featurePath);
            NodeRef oldFeature = new NodeRef(node, parentPath, ObjectId.NULL);
            return new DiffEntry(oldFeature, null);
        });

        ProgressListener progress = DefaultProgressListener.NULL;
        index.stage(progress, Iterators.concat(treeDeletes, featureDeletes), -1);
    }

    /**
     * @return an iterable with all elements in {@link #pathsToRemove} whose parent path are not in
     *         {@code deleteTrees}
     */
    private List<String> filterFeatures(Set<String> deleteTrees) {
        List<String> filtered = new ArrayList<>(this.pathsToRemove.size());
        for (String pathToRemove : pathsToRemove) {
            if (deleteTrees.contains(pathToRemove)
                    || deleteTrees.contains(NodeRef.parentPath(pathToRemove))) {
                continue;
            }
            NodeRef.checkValidPath(pathToRemove);
            filtered.add(pathToRemove);
        }
        return filtered;
    }

    private Map<String, NodeRef> getDeleteTrees(RevTree workTree) {

        Iterator<NodeRef> childTrees = command(LsTreeOp.class)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_TREES)
                .setReference(workTree.getId().toString()).call();

        ImmutableMap<String, NodeRef> treesByPath = Maps.uniqueIndex(childTrees,
                (ref) -> ref.path());

        Set<String> requestedTrees = Sets.intersection(treesByPath.keySet(),
                new HashSet<>(pathsToRemove));
        Predicate<String> keyPredicate = Predicates.in(requestedTrees);
        Map<String, NodeRef> requestedTreesMap = Maps.filterKeys(treesByPath, keyPredicate);
        return requestedTreesMap;
    }

}
