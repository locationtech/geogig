/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.locationtech.geogig.model.RevTree.EMPTY;
import static org.locationtech.geogig.model.RevTree.EMPTY_TREE_ID;

import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.plumbing.diff.MutableTree;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

/**
 * Atomically creates a new tree out of several updates to it's children trees, including nested
 * trees, creating intermediate trees if needed.
 * <p>
 * This command is mainly used to create a new root tree after one or more of it's children feature
 * trees have been updated, in order to do so atomically instead of having to create several new
 * root trees.
 * <p>
 * For example, given root tree {@code A}, create root tree {@code B} by calling
 * {@link #setChild(NodeRef)} as many times as child trees need to be updated, or
 * {@link #removeChildTree(String) removeChild(treePath)}, as needed.
 * <p>
 * The execution of the command will create and return a new tree in the {@link #objectDatabase()}
 * with the provided updates.
 */
public class UpdateTree extends AbstractGeoGigOp<RevTree> {

    private static Ordering<String> REVERSEDEPTH = new Ordering<String>() {
        @Override
        public int compare(String left, String right) {
            int c = Integer.compare(NodeRef.depth(right), NodeRef.depth(left));
            if (c == 0) {
                return left.compareTo(right);
            }
            return c;
        }
    };

    private RevTree root;

    private SortedMap<String, NodeRef> childTreeUpdates = new TreeMap<>(REVERSEDEPTH);

    private Set<String> childTreeRemoves = new TreeSet<>();

    public UpdateTree setRoot(RevTree root) {
        checkNotNull(root);
        this.root = root;
        return this;
    }

    public UpdateTree setRoot(ObjectId root) {
        checkNotNull(root);
        return setRoot(EMPTY_TREE_ID.equals(root) ? EMPTY : objectDatabase().getTree(root));
    }

    public UpdateTree removeChildTree(String childTreePath) {
        NodeRef.checkValidPath(childTreePath);
        childTreeUpdates.remove(childTreePath);
        childTreeRemoves.add(childTreePath);
        return this;
    }

    public UpdateTree setChild(NodeRef childTreeNode) {
        checkNotNull(childTreeNode);
        final String path = childTreeNode.path();
        NodeRef.checkValidPath(path);
        checkArgument(RevObject.TYPE.TREE.equals(childTreeNode.getType()));

        childTreeRemoves.remove(path);
        childTreeUpdates.put(path, childTreeNode);
        return this;
    }

    @Override
    protected RevTree _call() {
        checkArgument(root != null, "root tree not provided");
        if (childTreeRemoves.isEmpty() && childTreeUpdates.isEmpty()) {
            return root;
        }

        // all the current child trees of root
        final MutableTree mutableRoot = load(root);
        MutableTree updatedRoot = mutableRoot.clone();

        this.childTreeRemoves.forEach((path) -> {
            updatedRoot.removeChild(path);
        });

        this.childTreeUpdates.values().forEach((r) -> {
            updatedRoot.forceChild(r.getParentPath(), r.getNode());
        });

        ObjectDatabase db = objectDatabase();
        RevTree newRoot = updatedRoot.build(db);
        return newRoot;
    }

    private MutableTree load(RevTree tree) {
        List<NodeRef> childTrees = Lists
                .newArrayList(command(LsTreeOp.class).setReference(tree.getId().toString())
                        .setStrategy(Strategy.DEPTHFIRST_ONLY_TREES).call());

        MutableTree mutableRoot = MutableTree.createFromRefs(tree.getId(), childTrees.iterator());

        return mutableRoot;
    }

}
