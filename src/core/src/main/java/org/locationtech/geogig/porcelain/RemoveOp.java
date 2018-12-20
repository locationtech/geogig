/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.DiffCount;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Removes a features and/or tree nodes from the working tree and index
 * 
 */
@CanRunDuringConflict
public class RemoveOp extends AbstractGeoGigOp<DiffObjectCount> {

    private List<String> pathsToRemove;

    /**
     * If true, any path in {@link #addPathToRemove(String)} that points to a tree instead of a
     * feature, is completely removed from the working tree, including the tree node itself.
     * <p>
     * This argument is mutually exclusive with {@link #truncate}
     */
    private boolean recursive;

    /**
     * If true, any path in {@link #addPathToRemove(String)} that points to a tree instead of a
     * feature, is truncated, leaving the tree node empty.
     * <p>
     * This argument is mutually exclusive with {@link #recursive}
     */
    private boolean truncate;

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

    /**
     * Indicates whether to recursively remove tree nodes pointed out to by any
     * {@link #addPathToRemove(String) added path}, including the tree nodes themselves.
     * <p>
     * If true, any path added to {@link #addPathToRemove(String)} that points to a tree instead of
     * a feature, is completely removed from the working tree, including the tree node itself.
     * <p>
     * NOTE this argument is mutually exclusive with {@link #setTruncate(boolean)}, and an
     * {@link IllegalArgumentException} will be thrown when the command is executed if both are set
     * to {@code true}.
     */
    public RemoveOp setRecursive(boolean recursive) {
        this.recursive = recursive;
        return this;
    }

    /**
     * Indicates whether to truncate tree nodes pointed out to by any
     * {@link #addPathToRemove(String) added path}, leaving such trees empty.
     * <p>
     * If true, any path added to {@link #addPathToRemove(String)} that points to a tree instead of
     * a feature, is truncated and the tree node is left empty.
     * <p>
     * NOTE this argument is mutually exclusive with {@link #setRecursive(boolean)}, and an
     * {@link IllegalArgumentException} will be thrown when the command is executed if both are set
     * to {@code true}.
     */
    public RemoveOp setTruncate(boolean truncate) {
        this.truncate = truncate;
        return this;
    }

    /**
     * @see java.util.concurrent.Callable#call()
     */
    protected DiffObjectCount _call() {
        checkArgument(!pathsToRemove.isEmpty(), "No paths to remove were indicated");
        checkArgument(!(recursive && truncate),
                "recursive and truncate arguments are mutually exclusive");

        // Check that all paths are valid and exist
        final WorkingTree workingTree = workingTree();

        final RevTree initialWorkTree = workingTree.getTree();

        final Map<String, NodeRef> deleteTrees = getDeleteTrees(initialWorkTree);
        final List<String> deleteFeatures = filterFeatures(deleteTrees.keySet());

        if (!deleteTrees.isEmpty() && !(recursive || truncate)) {
            String path = deleteTrees.values().iterator().next().path();
            throw new IllegalArgumentException(String.format(
                    "Cannot remove tree %s if recursive or truncate is not specified", path));
        }

        final ProgressListener listener = getProgressListener();

        for (Entry<String, NodeRef> e : deleteTrees.entrySet()) {
            final ObjectId currentWorkHead = workingTree.getTree().getId();
            String treePath = e.getKey();
            checkState(recursive || truncate);

            ObjectId newWorkHead;
            if (recursive) {
                newWorkHead = workingTree.delete(treePath);
            } else {
                newWorkHead = workingTree.truncate(treePath);
            }

            if (currentWorkHead.equals(newWorkHead)) {
                listener.setDescription(String.format("Tree %s not found", treePath));
            } else {
                listener.setDescription(String.format("%s %s tree",
                        (recursive ? "Deleted" : "Truncated"), treePath));
            }
            if (listener.isCanceled()) {
                return null;
            }
        }

        if (listener.isCanceled()) {
            return null;
        }

        if (!deleteFeatures.isEmpty()) {
            listener.setDescription("Deleting features...");
            workingTree.delete(deleteFeatures.iterator(), listener);
        }

        listener.setDescription("Staging changes...");

        final RevTree finalWorkTree = workingTree.getTree();

        Optional<ObjectId> headTree = command(ResolveTreeish.class).setTreeish(Ref.HEAD).call();
        ObjectId stageTree = stagingArea().getTree().getId();
        final boolean nothingElseStaged = headTree.isPresent() && headTree.get().equals(stageTree);
        if (nothingElseStaged) {
            stagingArea().updateStageHead(finalWorkTree.getId());
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
        final StagingArea index = stagingArea();

        //(treeRef) -> new DiffEntry(treeRef, null), but friendly for Fortify
        Function<NodeRef, DiffEntry> fn_DiffEntry_new =  new Function<NodeRef, DiffEntry>() {
            @Override
            public DiffEntry apply(NodeRef treeRef) {
                return new DiffEntry(treeRef, null);
            }};

        Iterator<DiffEntry> treeDeletes = Iterators.transform(trees,
                fn_DiffEntry_new);


//        (featurePath) -> {
//            Node node = RevObjectFactory.defaultInstance().createNode(
//                    NodeRef.nodeFromPath(featurePath), ObjectId.NULL, ObjectId.NULL, TYPE.FEATURE,
//                    null, null);
//            String parentPath = NodeRef.parentPath(featurePath);
//            NodeRef oldFeature = new NodeRef(node, parentPath, ObjectId.NULL);
//            return new DiffEntry(oldFeature, null);
//        }
        Function<String,DiffEntry> f = new Function<String, DiffEntry>() {
            @Override
            public DiffEntry apply(String featurePath) {
                Node node = RevObjectFactory.defaultInstance().createNode(
                        NodeRef.nodeFromPath(featurePath), ObjectId.NULL, ObjectId.NULL, TYPE.FEATURE,
                        null, null);
                String parentPath = NodeRef.parentPath(featurePath);
                NodeRef oldFeature = new NodeRef(node, parentPath, ObjectId.NULL);
                return new DiffEntry(oldFeature, null);
            }};


        Iterator<DiffEntry> featureDeletes = Iterators.transform(features, f);

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

        //NodeRef::path, but friendly for Fortify
        Function<NodeRef, String> fn_path =  new Function<NodeRef, String>() {
            @Override
            public String apply(NodeRef noderef) {
                return noderef.path();
            }};

        ImmutableMap<String, NodeRef> treesByPath = Maps.uniqueIndex(childTrees,
                fn_path);

        Set<String> requestedTrees = Sets.intersection(treesByPath.keySet(),
                new HashSet<>(pathsToRemove));
        Predicate<String> keyPredicate = Predicates.in(requestedTrees);
        Map<String, NodeRef> requestedTreesMap = Maps.filterKeys(treesByPath, keyPredicate);
        return requestedTreesMap;
    }

}
