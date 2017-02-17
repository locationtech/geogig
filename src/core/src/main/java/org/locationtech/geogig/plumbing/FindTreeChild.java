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

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.impl.DepthSearch;
import org.locationtech.geogig.storage.ObjectStore;

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

    private ObjectStore source;

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
     * @param source the object source
     * @return {@code this}
     */
    public FindTreeChild setSource(ObjectStore source) {
        this.source = source;
        return this;
    }

    /**
     * Executes the command.
     * 
     * @return an {@code Optional} that contains the Node if it was found, or
     *         {@link Optional#absent()} if it wasn't
     */
    @Override
    protected Optional<NodeRef> _call() {
        checkNotNull(childPath, "childPath");
        if (source == null) {
            this.source = objectDatabase();
        }
        final RevTree tree;
        if (parent == null) {
            ObjectId rootTreeId = command(ResolveTreeish.class).setSource(source)
                    .setTreeish(Ref.HEAD).call().get();
            if (rootTreeId.isNull()) {
                return Optional.absent();
            }
            tree = command(RevObjectParse.class).setSource(source).setObjectId(rootTreeId)
                    .call(RevTree.class).get();
        } else {
            tree = parent.get();
        }
        final String path = childPath;
        final String parentPath = this.parentPath == null ? "" : this.parentPath;

        DepthSearch depthSearch = new DepthSearch(source);
        Optional<NodeRef> childRef = depthSearch.find(tree, parentPath, path);
        return childRef;

    }

}
