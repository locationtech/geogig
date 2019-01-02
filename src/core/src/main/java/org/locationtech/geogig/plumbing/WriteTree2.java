/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.plumbing.diff.MutableTree;
import org.locationtech.geogig.plumbing.diff.TreeDifference;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.jts.geom.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Creates a new root tree in the {@link ObjectDatabase object database} from the current index,
 * based on the current {@code HEAD} and returns the new root tree id.
 * <p>
 * The index must be in a fully merged state.
 * 
 * <p>
 * This command creates a tree object using the current index. The id of the new root tree object is
 * returned. No {@link Ref ref} is updated as a result of this operation, so the resulting root tree
 * is "orphan". It's up to the calling code to update any needed reference.
 * 
 * Conceptually, write-tree sync()s the current index contents into a set of tree objects on the
 * {@link ObjectDatabase}. In order to have that match what is actually in your directory right now,
 * you need to have done a {@link UpdateIndex} phase before you did the write-tree.
 * 
 * @implNote: this is a performance improvement replacement for {@link WriteTree}, and so far has
 *            been replaced everywhere (i.e. for commit, rebase, revert, cherry-pick), and not by
 *            web api's {@code ApplyChangesResource} and core's
 *            {@code Abstract/LocalMappedRemoteRepo} as it doesn't have a proper replacement for
 *            {@link WriteTree#setDiffSupplier(Supplier)} yet, as used by the remote, REST, and WEB
 *            APIs.
 * @see TreeDifference
 * @see MutableTree
 */
public class WriteTree2 extends AbstractGeoGigOp<ObjectId> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WriteTree2.class);

    private Supplier<RevTree> oldRoot;

    private final Set<String> pathFilters = new TreeSet<>();

    // to be used when implementing a replacement for the current WriteTree2.setDiffSupplier()
    // private Supplier<Iterator<DiffEntry>> diffSupplier = null;

    /**
     * @param oldRoot a supplier for the old root tree
     * @return {@code this}
     */
    public WriteTree2 setOldRoot(Supplier<RevTree> oldRoot) {
        this.oldRoot = oldRoot;
        return this;
    }

    /**
     * Sets the tree paths that will be processed.
     * <p>
     * That is, whether the changes to the current {@link Ref#STAGE_HEAD STAGE_HEAD} vs
     * {@link Ref#HEAD HEAD} should be processed.
     * <p>
     * A path filter applies if:
     * <ul>
     * <li>There are no filters at all
     * <li>A filter and the path are the same
     * <li>A filter is a child of the tree path (e.g. {@code filter = "roads/roads.0" and path =
     * "roads"})
     * <li>A filter is a parent of the tree given by {@code treePath} and addresses a tree instead
     * of a feature (e.g. {@code filter = "roads" and path = "roads/highways"}, but <b>not</b> if
     * {@code filter = "roads/roads.0" and path = "roads/highways"} where {@code roads/roads.0} is
     * not a tree as given by the tree structure in {@code rightTree} and hence may address a
     * feature that's a direct child of {@code roads} instead)
     * </ul>
     * 
     * @param treePath a path to a tree in {@code rightTree}
     * @param rightTree the trees at the right side of the comparison, used to determine if a filter
     *        addresses a parent tree.
     * @return {@code true} if the changes in the tree given by {@code treePath} should be processed
     *         because any of the filters will match the changes on it
     */
    public WriteTree2 setPathFilter(@Nullable Iterable<String> pathFilters) {
        this.pathFilters.clear();
        if (pathFilters != null) {
            this.pathFilters.addAll(Lists.newArrayList(pathFilters));
        }
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

        if (pathFilters.isEmpty()) {
            final ObjectId stageRootId = stagingArea().getTree().getId();
            return stageRootId;
        }

        TreeDifference treeDifference = computeTreeDifference();

        if (treeDifference.areEqual()) {
            MutableTree leftTree = treeDifference.getLeftTree();
            Node leftNode = leftTree.getNode();
            ObjectId leftOid = leftNode.getObjectId();
            return leftOid;
        }

        final MutableTree oldLeftTree = treeDifference.getLeftTree().clone();
        Preconditions.checkState(oldLeftTree.equals(treeDifference.getLeftTree()));

        // handle renames before new and deleted trees for the computation of new and deleted to be
        // accurate, by means of the ignoreList
        Set<String> ignoreList = Sets.newHashSet();
        handleRenames(treeDifference, ignoreList);
        handlePureMetadataChanges(treeDifference, ignoreList);
        handleNewTrees(treeDifference, ignoreList);
        handleDeletedTrees(treeDifference, ignoreList);
        handleRemainingDifferences(treeDifference, ignoreList);

        progress.complete();

        MutableTree newLeftTree = treeDifference.getLeftTree();

        final ObjectDatabase repositoryDatabase = objectDatabase();
        final RevTree newRoot = newLeftTree.build(repositoryDatabase);

        ObjectId newRootId = newRoot.getId();

        return newRootId;
    }

    private void handlePureMetadataChanges(TreeDifference treeDifference, Set<String> ignoreList) {
        Map<NodeRef, NodeRef> pureMetadataChanges = treeDifference.findPureMetadataChanges();
        for (Map.Entry<NodeRef, NodeRef> e : pureMetadataChanges.entrySet()) {
            NodeRef newValue = e.getValue();
            String treePath = newValue.path();
            if (ignoreList.contains(treePath)) {
                continue;
            }
            ignoreList.add(treePath);
            if (!filterMatchesOrIsParent(treePath)) {
                continue;// filter doesn't apply to the changed tree
            }
            MutableTree leftTree = treeDifference.getLeftTree();
            leftTree.setChild(newValue.getParentPath(), newValue.getNode());
        }
    }

    private void handleDeletedTrees(TreeDifference treeDifference, Set<String> ignoreList) {
        SortedSet<NodeRef> deletes = treeDifference.findDeletes();
        for (NodeRef ref : deletes) {
            String path = ref.path();
            if (ignoreList.contains(path)) {
                continue;
            }
            ignoreList.add(path);
            if (!filterMatchesOrIsParent(path)) {
                if (filterApplies(path, treeDifference.getRightTree())) {
                    // can't optimize
                    RevTree newTree = applyChanges(ref, null);
                    Node newNode = RevObjectFactory.defaultInstance().createNode(ref.name(),
                            newTree.getId(), ref.getMetadataId(), TYPE.TREE, null, null);
                    MutableTree leftTree = treeDifference.getLeftTree();
                    leftTree.forceChild(ref.getParentPath(), newNode);
                }
            } else {
                MutableTree leftTree = treeDifference.getLeftTree();
                leftTree.removeChild(path);
            }
        }
    }

    private void handleNewTrees(TreeDifference treeDifference, Set<String> ignoreList) {
        SortedSet<NodeRef> newTrees = treeDifference.findNewTrees();
        for (NodeRef ref : newTrees) {
            final String path = ref.path();
            if (ignoreList.contains(path)) {
                continue;
            }
            ignoreList.add(path);

            if (!filterMatchesOrIsParent(path)) {
                MutableTree rightTree = treeDifference.getRightTree();
                if (filterApplies(path, rightTree)) {
                    // can't optimize
                    RevTree newTree = applyChanges(null, ref);
                    Node newNode = RevObjectFactory.defaultInstance().createNode(ref.name(),
                            newTree.getId(), ref.getMetadataId(), TYPE.TREE, null, null);
                    MutableTree leftTree = treeDifference.getLeftTree();
                    leftTree.forceChild(ref.getParentPath(), newNode);
                }
            } else {
                LOGGER.trace("Creating new tree {}", path);
                MutableTree leftTree = treeDifference.getLeftTree();
                String parentPath = ref.getParentPath();
                Node node = ref.getNode();
                leftTree.setChild(parentPath, node);
            }
        }
    }

    /**
     * A renamed tree is recognized by checking if a tree on the right points to the same object
     * that a tree on the left that doesn't exist anymore on the right.
     * <p>
     * Left entries are the original ones, and right entries are the new ones.
     * </p>
     * 
     * @param treeDifference
     * @param ignoreList
     */
    private void handleRenames(TreeDifference treeDifference, Set<String> ignoreList) {
        final SortedMap<NodeRef, NodeRef> renames = treeDifference.findRenames();

        for (Map.Entry<NodeRef, NodeRef> e : renames.entrySet()) {
            NodeRef oldValue = e.getKey();
            NodeRef newValue = e.getValue();
            String newPath = newValue.path();
            if (ignoreList.contains(newPath)) {
                continue;
            }
            ignoreList.add(newPath);
            if (!filterMatchesOrIsParent(newPath)) {
                continue;// filter doesn't apply to the renamed tree as a whole
            }
            LOGGER.trace("Handling rename of {} as {}", oldValue.path(), newPath);
            MutableTree leftTree = treeDifference.getLeftTree();
            leftTree.removeChild(oldValue.path());
            leftTree.setChild(newValue.getParentPath(), newValue.getNode());
        }
    }

    private void handleRemainingDifferences(TreeDifference treeDifference, Set<String> ignoreList) {

        // old/new refs to trees that have changed and apply to the pathFilters, deepest paths first
        final SortedMap<NodeRef, NodeRef> changedTrees = treeDifference.findChanges();
        final SortedMap<NodeRef, NodeRef> filteredChangedTrees = changedTrees;// filterChanges(changedTrees);

        for (Map.Entry<NodeRef, NodeRef> changedTreeRefs : filteredChangedTrees.entrySet()) {

            NodeRef leftTreeRef = changedTreeRefs.getKey();
            NodeRef rightTreeRef = changedTreeRefs.getValue();
            String newPath = rightTreeRef.path();
            if (ignoreList.contains(newPath)) {
                continue;
            }
            if (!filterApplies(newPath, treeDifference.getRightTree())) {
                continue;
            }
            ignoreList.add(newPath);
            RevTree tree = applyChanges(leftTreeRef, rightTreeRef);

            Envelope bounds = SpatialOps.boundsOf(tree);
            Node newTreeNode = RevObjectFactory.defaultInstance().createNode(rightTreeRef.name(),
                    tree.getId(), rightTreeRef.getMetadataId(), TYPE.TREE, bounds, null);

            MutableTree leftRoot = treeDifference.getLeftTree();
            String parentPath = rightTreeRef.getParentPath();
            leftRoot.setChild(parentPath, newTreeNode);
        }
    }

    private RevTree applyChanges(@Nullable final NodeRef leftTreeRef,
            @Nullable final NodeRef rightTreeRef) {

        Preconditions.checkArgument(leftTreeRef != null || rightTreeRef != null,
                "either left or right tree shall be non null");

        final ObjectDatabase repositoryDatabase = objectDatabase();
        final String treePath = rightTreeRef == null ? leftTreeRef.path() : rightTreeRef.path();

        final Set<String> strippedPathFilters = stripParentAndFiltersThatDontApply(this.pathFilters,
                treePath);

        // find the diffs that apply to the path filters
        final ObjectId leftTreeId = leftTreeRef == null ? RevTree.EMPTY_TREE_ID
                : leftTreeRef.getObjectId();
        final ObjectId rightTreeId = rightTreeRef == null ? RevTree.EMPTY_TREE_ID
                : rightTreeRef.getObjectId();

        final RevTree currentLeftTree = repositoryDatabase.getTree(leftTreeId);

        final RevTreeBuilder builder = RevTreeBuilder.builder(repositoryDatabase, currentLeftTree);

        // create the new trees taking into account all the nodes
        DiffTree diffs = command(DiffTree.class).setRecursive(false).setReportTrees(false)
                .setOldTree(leftTreeId).setNewTree(rightTreeId)
                .setPathFilter(new ArrayList<>(strippedPathFilters)).setCustomFilter(null);

        try (AutoCloseableIterator<DiffEntry> sourceIterator = diffs.get()) {
            Iterator<DiffEntry> updatedIterator = sourceIterator;
            if (!strippedPathFilters.isEmpty()) {
                final Set<String> expected = Sets.newHashSet(strippedPathFilters);
                updatedIterator = Iterators.filter(updatedIterator, new Predicate<DiffEntry>() {
                    @Override
                    public boolean apply(DiffEntry input) {
                        boolean applies;
                        if (input.isDelete()) {
                            applies = expected.contains(input.oldName());
                        } else {
                            applies = expected.contains(input.newName());
                        }
                        return applies;
                    }
                });
            }

            for (; updatedIterator.hasNext();) {
                final DiffEntry diff = updatedIterator.next();
                if (diff.isDelete()) {
                    builder.remove(diff.oldNode());
                } else {
                    NodeRef newObject = diff.getNewObject();
                    Node node = newObject.getNode();
                    builder.put(node);
                }
            }
        }

        final RevTree newTree = builder.build();
        repositoryDatabase.put(newTree);
        return newTree;
    }

    private boolean filterMatchesOrIsParent(final String treePath) {
        if (pathFilters.isEmpty()) {
            return true;
        }

        for (String filter : pathFilters) {
            if (filter.equals(treePath)) {
                return true;
            }
            boolean treeIsChildOfFilter = NodeRef.isChild(filter, treePath);
            if (treeIsChildOfFilter) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if any of the {@link #setPathFilter(List) path filters} apply to the given
     * {@code treePath}.
     * <p>
     * That is, whether the changes to the tree given by {@code treePath} should be processed.
     * <p>
     * A path filter applies to the given tree path if:
     * <ul>
     * <li>There are no filters at all
     * <li>A filter and the path are the same
     * <li>A filter is a child of the tree path (e.g. {@code filter = "roads/roads.0" and path =
     * "roads"})
     * <li>A filter is a parent of the tree given by {@code treePath} and addresses a tree instead
     * of a feature (e.g. {@code filter = "roads" and path = "roads/highways"}, but <b>not</b> if
     * {@code filter = "roads/roads.0" and path = "roads/highways"} where {@code roads/roads.0} is
     * not a tree as given by the tree structure in {@code rightTree} and hence may address a
     * feature that's a direct child of {@code roads} instead)
     * </ul>
     * 
     * @param treePath a path to a tree in {@code rightTree}
     * @param rightTree the trees at the right side of the comparison, used to determine if a filter
     *        addresses a parent tree.
     * @return {@code true} if the changes in the tree given by {@code treePath} should be processed
     *         because any of the filters will match the changes on it
     */
    private boolean filterApplies(final String treePath, MutableTree rightTree) {
        if (pathFilters.isEmpty()) {
            return true;
        }

        final Set<String> childTrees = rightTree.getChildrenAsMap().keySet();

        for (String filter : pathFilters) {
            if (filter.equals(treePath)) {
                return true;
            }
            boolean filterIsChildOfTree = NodeRef.isDirectChild(treePath, filter);
            if (filterIsChildOfTree) {
                return true;
            }
            boolean filterIsParentOfTree = filterMatchesOrIsParent(treePath);
            boolean filterIsTree = childTrees.contains(filter);
            if (filterIsParentOfTree && filterIsTree) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return a new list out of the filters in pathFilters that apply to the given path (are equal
     *         or a parent of), with their own parents stripped to that they apply directly to the
     *         node names in the tree
     */
    private Set<String> stripParentAndFiltersThatDontApply(Set<String> pathFilters,
            final String treePath) {

        Set<String> parentsStripped = new TreeSet<>();
        for (String filter : pathFilters) {
            if (filter.equals(treePath)) {
                continue;// include all diffs in the tree addressed by treePath
            }
            boolean pathIsChildOfFilter = NodeRef.isChild(filter, treePath);
            if (pathIsChildOfFilter) {
                continue;// include all diffs in this path
            }

            boolean filterIsChildOfTree = NodeRef.isChild(treePath, filter);
            if (filterIsChildOfTree) {
                String filterFromPath = NodeRef.removeParent(treePath, filter);
                parentsStripped.add(filterFromPath);
            }
        }
        return parentsStripped;
    }

    private TreeDifference computeTreeDifference() {
        final String rightTreeish = Ref.STAGE_HEAD;

        final ObjectId rootTreeId = resolveRootTreeId();
        final ObjectId stageRootId = stagingArea().getTree().getId();

        final Supplier<Iterator<NodeRef>> leftTreeRefs;
        final Supplier<Iterator<NodeRef>> rightTreeRefs;
        if (rootTreeId.isNull()) {
            Iterator<NodeRef> empty = Collections.emptyIterator();
            leftTreeRefs = Suppliers.ofInstance(empty);
        } else {
            leftTreeRefs = command(LsTreeOp.class).setReference(rootTreeId.toString())
                    .setStrategy(Strategy.DEPTHFIRST_ONLY_TREES);
        }
        rightTreeRefs = command(LsTreeOp.class).setReference(rightTreeish)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_TREES);

        MutableTree leftTree = MutableTree.createFromRefs(rootTreeId, leftTreeRefs);
        MutableTree rightTree = MutableTree.createFromRefs(stageRootId, rightTreeRefs);

        TreeDifference treeDifference = TreeDifference.create(leftTree, rightTree);
        return treeDifference;
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

}
