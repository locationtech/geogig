/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing;

import static com.google.common.base.Preconditions.checkNotNull;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.repository.DepthSearch;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StagingDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Finds a {@link Node} by searching the given {@link RevTree} for the given path, returns the
 * {@link NodeRef} that wraps it.
 * 
 * @see DepthSearch
 * @see ResolveTreeish
 * @see RevObjectParse
 */
public class FindTreeChild extends AbstractGeoGigOp<Optional<NodeRef>> {

    private Supplier<RevTree> parent;

    private String childPath;

    private String parentPath;

    private boolean indexDb;

    /**
     * @param indexDb whether to look up in the {@link StagingDatabase index db} ({@code true}) or
     *        on the repository's {@link ObjectDatabase object database} (default)
     * @return {@code this}
     */
    public FindTreeChild setIndex(final boolean indexDb) {
        this.indexDb = indexDb;
        return this;
    }

    /**
     * @param tree a supplier that resolves to the tree where to start the search for the nested
     *        child. If not supplied the current HEAD tree is assumed.
     * @return {@code this}
     */
    public FindTreeChild setParent(Supplier<RevTree> tree) {
        this.parent = tree;
        return this;
    }

    /**
     * @param tree the tree to search for the nested child
     * @return {@code this}
     */
    public FindTreeChild setParent(RevTree tree) {
        this.parent = Suppliers.ofInstance(tree);
        return this;
    }

    /**
     * @param parentPath the parent's path. If not given parent is assumed to be a root tree.
     * @return {@code this}
     */
    public FindTreeChild setParentPath(String parentPath) {
        this.parentPath = parentPath;
        return this;
    }

    /**
     * @param childPath the full path of the subtree to look for
     * @return {@code this}
     */
    public FindTreeChild setChildPath(String childPath) {
        this.childPath = childPath;
        return this;
    }

    /**
     * Executes the command.
     * 
     * @return an {@code Optional} that contains the Node if it was found, or
     *         {@link Optional#absent()} if it wasn't
     */
    @Override
    protected  Optional<NodeRef> _call() {
        checkNotNull(childPath, "childPath");
        final RevTree tree;
        if (parent == null) {
            ObjectId rootTreeId = command(ResolveTreeish.class).setTreeish(Ref.HEAD).call().get();
            if (rootTreeId.isNull()) {
                return Optional.absent();
            }
            tree = command(RevObjectParse.class).setObjectId(rootTreeId).call(RevTree.class).get();
        } else {
            tree = parent.get();
        }
        final String path = childPath;
        final String parentPath = this.parentPath == null ? "" : this.parentPath;
        final ObjectDatabase target = indexDb ? stagingDatabase() : objectDatabase();

        DepthSearch depthSearch = new DepthSearch(target);
        Optional<NodeRef> childRef = depthSearch.find(tree, parentPath, path);
        return childRef;

    }

}
