/* Copyright (c) 2012-2014 Boundless and others.
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
import static com.google.common.base.Preconditions.checkState;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.SpatialOps;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Writes the contents of a given tree as a child of a given ancestor tree, creating any
 * intermediate tree needed, and returns the {@link ObjectId id} of the resulting new ancestor tree.
 * <p>
 * If no {@link #setAncestorPath(String) ancestor path} is provided, the ancestor is assumed to be a
 * root tree
 * 
 * @see CreateTree
 * @see FindTreeChild
 */
public class WriteBack extends AbstractGeoGigOp<ObjectId> {

    private RevTree ancestor;

    private String childPath;

    private Supplier<RevTree> tree;

    private String ancestorPath;

    private Optional<ObjectId> metadataId;

    public WriteBack() {
        this.metadataId = Optional.absent();
    }

    /**
     * @param oldRoot the root tree to which add the {@link #setTree(RevTree) child tree} and any
     *        intermediate tree. If not set defaults to the current HEAD tree
     * @return {@code this}
     */
    public WriteBack setAncestor(ObjectId oldRoot) {
        if (RevTreeBuilder.EMPTY_TREE_ID.equals(oldRoot)) {
            this.ancestor = RevTreeBuilder.EMPTY;
        } else {
            this.ancestor = objectDatabase().getTree(oldRoot);
        }
        return this;
    }

    /**
     * @param ancestor the root tree to which add the {@link #setTree(RevTree) child tree} and any
     *        intermediate tree. Defaults to the current HEAD tree if not set.
     * @return {@code this}
     */
    public WriteBack setAncestor(RevTree ancestor) {
        this.ancestor = ancestor;
        return this;
    }

    /**
     * @param ancestorPath the path of the {@link #setAncestor ancestor tree}. If not set the
     *        ancestor tree is assumed to be a root tree.
     * @return {@code this}
     */
    public WriteBack setAncestorPath(String ancestorPath) {
        this.ancestorPath = ancestorPath;
        return this;
    }

    /**
     * @param childPath mandatory, the path to the child tree
     * @return {@code this}
     */
    public WriteBack setChildPath(String childPath) {
        this.childPath = childPath;
        return this;
    }

    /**
     * @param tree the tree to store on the object database and to create any intermediate tree for
     *        the given {@link #setAncestor(Supplier) ancestor tree}
     * @return {@code this}
     */
    public WriteBack setTree(RevTree tree) {
        return setTree(Suppliers.ofInstance(tree));
    }

    /**
     * @param tree the tree to store on the object database and to create any intermediate tree for
     *        the given {@link #setAncestor(Supplier) ancestor tree}
     * @return {@code this}
     */
    public WriteBack setTree(Supplier<RevTree> tree) {
        this.tree = tree;
        return this;
    }

    /**
     * Executes the write back operation.
     * 
     * @return the {@link ObjectId id} of the resulting new ancestor tree.
     */
    @Override
    protected ObjectId _call() {
        checkNotNull(tree, "child tree not set");
        checkNotNull(childPath, "child tree path not set");

        final String ancestorPath = resolveAncestorPath();
        checkArgument(NodeRef.isChild(ancestorPath, childPath), String.format(
                "child path '%s' is not a child of ancestor path '%s'", childPath, ancestorPath));

        RevTree tree = this.tree.get();
        checkState(null != tree, "child tree supplier returned null");

        ObjectDatabase targetDb = objectDatabase();

        return writeBack(ancestor, ancestorPath, tree, childPath, targetDb,
                metadataId.or(ObjectId.NULL));
    }

    /**
     * @return the resolved ancestor path
     */
    private String resolveAncestorPath() {
        return ancestorPath == null ? NodeRef.ROOT : ancestorPath;
    }

    private ObjectId writeBack(final RevTree root, final String ancestorPath,
            final RevTree childTree, final String childPath, final ObjectDatabase targetDatabase,
            final ObjectId metadataId) {

        final ObjectId newRootId;

        final boolean isDirectChild = NodeRef.isDirectChild(ancestorPath, childPath);
        if (isDirectChild) {
            Envelope treeBounds = null;
            if (!metadataId.isNull()) {// only include bounds for trees with a default feature type
                treeBounds = SpatialOps.boundsOf(childTree);
            }
            String childName = childPath;
            Node treeNode = Node.create(childName, childTree.getId(), metadataId, TYPE.TREE,
                    treeBounds);
            RevTree newRoot;
            RevTreeBuilder ancestor = RevTreeBuilder.canonical(targetDatabase, root);
            ancestor.put(treeNode);
            newRoot = ancestor.build();

            newRootId = newRoot.getId();
        } else {

            final String parentPath = NodeRef.parentPath(childPath);
            final String childName = NodeRef.nodeFromPath(childPath);

            Optional<NodeRef> parentRef = getTreeChild(root, parentPath);
            RevTreeBuilder parentBuilder;
            ObjectId parentMetadataId = ObjectId.NULL;
            if (parentRef.isPresent()) {
                ObjectId parentId = parentRef.get().getObjectId();
                parentMetadataId = parentRef.get().getMetadataId();
                parentBuilder = RevTreeBuilder.canonical(targetDatabase,
                        targetDatabase.getTree(parentId));
            } else {
                parentBuilder = RevTreeBuilder.canonical(targetDatabase);
            }

            Envelope treeBounds = null;
            if (!metadataId.isNull()) {// only include bounds for trees with a default feature type
                treeBounds = SpatialOps.boundsOf(childTree);
            }
            Node treeNode = Node.create(childName, childTree.getId(), metadataId, TYPE.TREE,
                    treeBounds);

            parentBuilder.put(treeNode);
            RevTree parent = parentBuilder.build();

            newRootId = writeBack(root, ancestorPath, parent, parentPath, targetDatabase,
                    parentMetadataId);
        }
        return newRootId;
    }

    private Optional<NodeRef> getTreeChild(RevTree parent, String childPath) {

        FindTreeChild cmd = command(FindTreeChild.class).setParent(parent).setChildPath(childPath);

        Optional<NodeRef> nodeRef = cmd.call();
        return nodeRef;
    }

    /**
     * @param metadataId the (optional) metadata id for the resulting tree ref
     * @return
     */
    public WriteBack setMetadataId(@Nullable ObjectId metadataId) {
        this.metadataId = Optional.fromNullable(metadataId);
        return this;
    }

}
