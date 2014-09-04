/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.repository.SpatialOps;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StagingDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Writes the contents of a given tree as a child of a given ancestor tree, creating any
 * intermediate tree needed, and returns the {@link ObjectId id} of the resulting new ancestor tree.
 * <p>
 * If no {@link #setAncestor(RevTreeBuilder) ancestor} is provided,it is assumed to be the current
 * HEAD tree.
 * <p>
 * If no {@link #setAncestorPath(String) ancestor path} is provided, the ancestor is assumed to be a
 * root tree
 * 
 * @see CreateTree
 * @see RevObjectParse
 * @see FindTreeChild
 */
public class WriteBack extends AbstractGeoGigOp<ObjectId> {

    private boolean indexDb;

    private Supplier<RevTreeBuilder> ancestor;

    private String childPath;

    private Supplier<RevTree> tree;

    private String ancestorPath;

    private Optional<ObjectId> metadataId;

    public WriteBack() {
        this.metadataId = Optional.absent();
    }

    /**
     * @param indexDb if {@code true} the trees will be stored to the {@link StagingDatabase},
     *        otherwise to the repository's {@link ObjectDatabase permanent store}. Defaults to
     *        {@code false}
     * @return {@code this}
     */
    public WriteBack setToIndex(boolean indexDb) {
        this.indexDb = indexDb;
        return this;
    }

    /**
     * @param oldRoot the root tree to which add the {@link #setTree(RevTree) child tree} and any
     *        intermediate tree. If not set defaults to the current HEAD tree
     * @return {@code this}
     */
    public WriteBack setAncestor(RevTreeBuilder oldRoot) {
        return setAncestor(Suppliers.ofInstance(oldRoot));
    }

    /**
     * @param ancestor the root tree to which add the {@link #setTree(RevTree) child tree} and any
     *        intermediate tree. If not set defaults to the current HEAD tree
     * @return {@code this}
     */
    public WriteBack setAncestor(Supplier<RevTreeBuilder> ancestor) {
        this.ancestor = ancestor;
        return this;
    }

    /**
     * @param ancestorPath the path of the {@link #setAncestor(Supplier) ancestor tree}. If set not
     *        the ancestor tree is assumed to be a root tree.
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

        String ancestorPath = resolveAncestorPath();
        checkArgument(NodeRef.isChild(ancestorPath, childPath), String.format(
                "child path '%s' is not a child of ancestor path '%s'", childPath, ancestorPath));

        RevTree tree = this.tree.get();
        checkState(null != tree, "child tree supplier returned null");

        ObjectDatabase targetDb = indexDb ? stagingDatabase() : objectDatabase();
        RevTreeBuilder root = resolveAncestor();

        return writeBack(root, ancestorPath, tree, childPath, targetDb,
                metadataId.or(ObjectId.NULL));
    }

    /**
     * @return the resolved ancestor path
     */
    private String resolveAncestorPath() {
        return ancestorPath == null ? "" : ancestorPath;
    }

    /**
     * @return the resolved ancestor
     */
    private RevTreeBuilder resolveAncestor() {
        RevTreeBuilder ancestor = this.ancestor.get();
        checkState(ancestor != null, "provided ancestor tree supplier returned null");
        return ancestor;
    }

    private ObjectId writeBack(RevTreeBuilder ancestor, final String ancestorPath,
            final RevTree childTree, final String childPath, final ObjectDatabase targetDatabase,
            final ObjectId metadataId) {

        final ObjectId treeId = childTree.getId();
        targetDatabase.put(childTree);

        final boolean isDirectChild = NodeRef.isDirectChild(ancestorPath, childPath);
        if (isDirectChild) {
            Envelope treeBounds = null;
            if (!metadataId.isNull()) {// only include bounds for trees with a default feature type
                treeBounds = SpatialOps.boundsOf(childTree);
            }
            String childName = childPath;
            Node treeNode = Node.create(childName, treeId, metadataId, TYPE.TREE, treeBounds);
            ancestor.put(treeNode);
            RevTree newAncestor = ancestor.build();
            targetDatabase.put(newAncestor);
            return newAncestor.getId();
        }

        final String parentPath = NodeRef.parentPath(childPath);
        Optional<NodeRef> parentRef = getTreeChild(ancestor, parentPath);
        RevTreeBuilder parentBuilder;
        ObjectId parentMetadataId = ObjectId.NULL;
        if (parentRef.isPresent()) {
            ObjectId parentId = parentRef.get().objectId();
            parentMetadataId = parentRef.get().getMetadataId();
            parentBuilder = getTree(parentId, targetDatabase).builder(targetDatabase);
        } else {
            parentBuilder = RevTree.EMPTY.builder(targetDatabase);
        }

        String childName = NodeRef.nodeFromPath(childPath);
        Envelope treeBounds = null;
        if (!metadataId.isNull()) {// only include bounds for trees with a default feature type
            treeBounds = SpatialOps.boundsOf(childTree);
        }
        Node treeNode = Node.create(childName, treeId, metadataId, TYPE.TREE, treeBounds);
        parentBuilder.put(treeNode);
        RevTree parent = parentBuilder.build();

        return writeBack(ancestor, ancestorPath, parent, parentPath, targetDatabase,
                parentMetadataId);
    }

    private RevTree getTree(ObjectId treeId, ObjectDatabase targetDb) {
        RevTree revTree = targetDb.getTree(treeId);
        return revTree;
    }

    private Optional<NodeRef> getTreeChild(RevTreeBuilder parent, String childPath) {
        RevTree realParent = parent.build();
        FindTreeChild cmd = command(FindTreeChild.class).setIndex(true).setParent(realParent)
                .setChildPath(childPath);

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
