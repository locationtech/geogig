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

import static com.google.common.base.Preconditions.checkNotNull;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Finds the subtree of the given tree named after the indicated path, or creates a new one. If a
 * new one is created, it is still not saved.
 * <p>
 * If a child tree of {@code parent} addressed by the given {@code childPath} exists, returns it's
 * mutable copy, otherwise just returns a new mutable tree without any modification to root or any
 * intermediate tree between root and the requested tree path.
 * 
 * @see RevTree
 */
public class FindOrCreateSubtree extends AbstractGeoGigOp<RevTree> {

    private Supplier<Optional<RevTree>> parentSupplier;

    private String childPath;

    private String parentPath;

    /**
     * @param parent a supplier that resolves to the parent tree where to start the search for the
     *        subtree from
     * @return {@code this}
     */
    public FindOrCreateSubtree setParent(Supplier<Optional<RevTree>> parent) {
        this.parentSupplier = parent;
        return this;
    }

    public FindOrCreateSubtree setParent(RevTree parent) {
        this.parentSupplier = Suppliers.ofInstance(Optional.of(parent));
        return this;
    }

    /**
     * @param parentPath the parent's path. If not given parent is assumed to be a root tree.
     * @return {@code this}
     */
    public FindOrCreateSubtree setParentPath(String parentPath) {
        this.parentPath = parentPath;
        return this;
    }

    /**
     * @param subtreePath the full path of the subtree to look for
     * @return {@code this}
     */
    public FindOrCreateSubtree setChildPath(String subtreePath) {
        this.childPath = subtreePath;
        return this;
    }

    /**
     * Executes the command.
     * 
     * @return the subtree if it was found, or a new one if it wasn't
     */
    @Override
    protected RevTree _call() {
        checkNotNull(parentSupplier, "parent");
        checkNotNull(childPath, "childPath");

        ObjectId subtreeId;

        if (parentSupplier.get().isPresent()) {
            RevTree parent = parentSupplier.get().get();

            Optional<NodeRef> treeChildRef = command(FindTreeChild.class).setParentPath(parentPath)
                    .setChildPath(childPath).setParent(Suppliers.ofInstance(parent)).call();

            if (treeChildRef.isPresent()) {
                NodeRef treeRef = treeChildRef.get();
                if (!TYPE.TREE.equals(treeRef.getType())) {
                    throw new IllegalArgumentException("Object exists as child of tree "
                            + parent.getId() + " but is not a tree: " + treeChildRef);
                }
                subtreeId = treeRef.getObjectId();
            } else {
                subtreeId = RevTree.EMPTY_TREE_ID;
            }
        } else {
            subtreeId = RevTree.EMPTY_TREE_ID;
        }
        if (RevTree.EMPTY_TREE_ID.equals(subtreeId)) {
            return RevTree.EMPTY;
        }
        ObjectStore target = objectDatabase();
        RevTree tree = target.getTree(subtreeId);
        return tree;
    }
}
