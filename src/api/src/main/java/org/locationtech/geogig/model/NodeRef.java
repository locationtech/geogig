/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

/**
 * A reference to a {@link Node} with extra data to fully address it on a revision tree, including
 * the parent tree path and the default metadata id (i.e. the {@link RevFeatureType} the node
 * belongs to.
 * 
 * @since 1.0
 */
public class NodeRef implements Bounded, Comparable<NodeRef> {

    /**
     * String representing the root node ref.
     */
    public static final String ROOT = "";

    /**
     * The character '/' used to separate paths (e.g. {@code path/to/node})
     */
    public static final char PATH_SEPARATOR = '/';

    /**
     * Full path from the root tree to the object this ref points to.
     * <p>
     * Can only be null for a root node (one with {@link NodeRef#ROOT} {@link Node#getName() name})
     */
    @Nullable
    private String parentPath;

    /**
     * The {@code Node} this object points to
     */
    private Node node;

    /**
     * possibly {@link ObjectId#NULL NULL} id for the object describing the object this ref points
     * to
     */
    private ObjectId metadataId;

    /**
     * Constructs a new {@code Node} objects from a {@code Node} object without metadataId. It
     * assumes that the passed {@code Node} does not have a metadataId value, and will not use it,
     * even it it is present.
     * 
     * @param node a Node representing the element this Node points to
     * @param parentPath the path of the parent tree, may be an empty string
     * @param metadataId the metadataId of the element
     */
    public NodeRef(Node node, String parentPath, ObjectId metadataId) {
        Preconditions.checkNotNull(node, "node is null");
        Preconditions.checkNotNull(metadataId, "metadataId is null, did you mean ObjectId.NULL?");

        Preconditions.checkArgument(parentPath != null || NodeRef.ROOT.equals(node.getName()),
                "parentPath is null, did you mean an empty string? null parent path is only allowed for the root node");

        this.node = node;
        this.parentPath = parentPath;
        this.metadataId = metadataId;
    }

    /**
     * Creates a new {@code NodeRef} with the updated {@link ObjectId} and {@link Envelope}.
     * 
     * @param newId the updated {@link ObjectId}
     * @param newBounds the updated bounds
     * @return the newly created {@code NodeRef}
     */
    public NodeRef update(final ObjectId newId, final @Nullable Envelope newBounds) {
        Node newNode = node.update(newId, newBounds);
        return NodeRef.create(parentPath, newNode, metadataId);
    }

    /**
     * Creates a {@code NodeRef} pointing to the provided {@code Node} with a {@code null} parent
     * path, the provided {@code Node} must be a root node.
     * 
     * @param node the {@code Node} to point to
     */
    public static NodeRef createRoot(Node node) {
        Preconditions.checkArgument(NodeRef.ROOT.equals(node.getName()),
                "A root NodeRef can only be created for a root node");
        return new NodeRef(node, null, ObjectId.NULL);
    }

    /**
     * Creates a {@code NodeRef} pointing to the provided {@code node} and parent path.
     * 
     * @param parentPath the parent path of the node
     * @param node the {@code Node} to point to
     * @return the new {@code NodeRef}
     */
    public static NodeRef create(String parentPath, Node node) {
        return new NodeRef(node, parentPath, ObjectId.NULL);
    }

    /**
     * Creates a {@code NodeRef} pointing to the provided {@code node} and parent path and metadata
     * id.
     * 
     * @param parentPath the parent path of the node
     * @param node the {@code Node} to point to
     * @param metadataId the metadata id
     * @return the new {@code NodeRef}
     */
    public static NodeRef create(String parentPath, Node node, ObjectId metadataId) {
        return new NodeRef(node, parentPath, metadataId);
    }

    /**
     * @return the parent path of the object this ref points to
     */
    public String getParentPath() {
        return parentPath;
    }

    /**
     * @return the {@code Node} this object points to
     */
    public Node getNode() {
        return node;
    }

    /**
     * Returns the full path from the root tree to the object this ref points to
     * <p>
     * This is a derived property, shortcut for
     * <code>{@link #getParentPath()} + "/" + getNode().getName() </code>
     */
    public String path() {
        return NodeRef.appendChild(parentPath, node.getName());
    }

    /**
     * @return the simple name of the {@link Node} this object points to
     */
    public String name() {
        return node.getName();
    }

    /**
     * @deprecated use {@link #getObjectId()} instead
     */
    @Deprecated
    public ObjectId objectId() {
        return node.getObjectId();
    }

    /**
     * @return the {@link ObjectId} of the {@code Node} this object points to
     */
    @Override
    public ObjectId getObjectId() {
        return node.getObjectId();
    }

    /**
     * The node's metadata id, which can be given by the {@link Node#getMetadataId() node itself} or
     * the metadata id given to this {@link NodeRef} constructor if the {@code Node} does not have a
     * metadata id set, so that Nodes can inherit the metadata id from its parent tree.
     * 
     * @return the node's metadata id if provided by {@link Node#getMetadataId()} or this node ref
     *         metadata id otherwise.
     */
    public ObjectId getMetadataId() {
        if (node.getMetadataId().isPresent() && !node.getMetadataId().get().isNull()) {
            return node.getMetadataId().get();
        } else {
            return this.metadataId;
        }
    }

    /**
     * @return the metadata id of this object, not the node it points to
     * 
     * @see NodeRef#getMetadataId()
     */
    public ObjectId getDefaultMetadataId() {
        return this.metadataId;
    }

    /**
     * @return the {@link RevObject.TYPE} of the {@code Node} this object points to
     */
    public RevObject.TYPE getType() {
        return node.getType();
    }

    /**
     * Tests equality over another {@code NodeRef} based on {@link #getParentPath() parent path},
     * {@link #getNode() node} name and id, and {@link #getMetadataId()}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NodeRef)) {
            return false;
        }
        NodeRef r = (NodeRef) o;
        return Objects.equal(parentPath, r.parentPath) && node.equals(r.node)
                && getMetadataId().equals(r.getMetadataId());
    }

    /**
     * Hash code is based on {@link #getParentPath() parent path}, {@link #getNode() node} name and
     * id, and {@link #getMetadataId()}
     */
    @Override
    public int hashCode() {
        return 17 ^ (parentPath != null ? parentPath.hashCode() : 1) * node.getObjectId().hashCode()
                * getMetadataId().hashCode();
    }

    /**
     * Provides for natural ordering of {@code NodeRef}, based on {@link #path()}
     */
    @Override
    public int compareTo(NodeRef o) {
        int c = parentPath.compareTo(o.getParentPath());
        if (c == 0) {
            return node.compareTo(o.getNode());
        }
        return c;
    }

    /**
     * @return the Node represented as a readable string.
     */
    @Override
    public String toString() {
        return new StringBuilder("NodeRef").append('[').append(path()).append(" -> ")
                .append(node.getObjectId()).append(']').toString();
    }

    /**
     * Returns the parent path of {@code fullPath}.
     * <p>
     * Given {@code fullPath == "path/to/node"} returns {@code "path/to"}, given {@code "node"}
     * returns {@code ""}, given {@code null} returns {@code null}
     * 
     * @param fullPath the full path to extract the parent path from
     * @return non null parent path, empty string if {@code fullPath} has no children (i.e. no
     *         {@link #PATH_SEPARATOR}).
     */
    public static @Nullable String parentPath(@Nullable String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return null;
        }
        int idx = fullPath.lastIndexOf(PATH_SEPARATOR);
        if (idx == -1) {
            return ROOT;
        }
        return fullPath.substring(0, idx);
    }

    /**
     * Determines if the input path is valid.
     * 
     * @param path the path to check
     * @throws IllegalArgumentException
     */
    public static void checkValidPath(final String path) {
        if (path == null) {
            throw new IllegalArgumentException("null path");
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("empty path");
        }
        if (path.charAt(path.length() - 1) == PATH_SEPARATOR) {
            throw new IllegalArgumentException("path cannot end with path separator: " + path);
        }
    }

    /**
     * Returns the node of {@code fullPath}.
     * <p>
     * Given {@code fullPath == "path/to/node"} returns {@code "node" }, given {@code "node"}
     * returns {@code "node"}, given {@code null} returns {@code null}
     * 
     * @param fullPath the full path to extract the node from
     * @return non null node, original string if {@code fullPath} has no path (i.e. no
     *         {@link #PATH_SEPARATOR}).
     */
    public static @Nullable String nodeFromPath(@Nullable String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return null;
        }
        int idx = fullPath.lastIndexOf(PATH_SEPARATOR);
        if (idx == -1) {
            return fullPath;
        }
        return fullPath.substring(idx + 1, fullPath.length());
    }

    /**
     * Determines if the given node path is a direct child of the parent path.
     * 
     * @param parentPath the parent path
     * @param nodePath the path of the node
     * @return true if {@code nodePath} is a direct child of {@code parentPath}, {@code false} if
     *         unrelated, sibling, same path, or nested child
     */
    public static boolean isDirectChild(String parentPath, String nodePath) {
        checkNotNull(parentPath, "parentPath");
        checkNotNull(nodePath, "nodePath");
        int idx = nodePath.lastIndexOf(PATH_SEPARATOR);
        if (parentPath.isEmpty()) {
            return !nodePath.isEmpty() && idx == -1;
        }
        return idx == parentPath.length() && nodePath.startsWith(parentPath);
    }

    /**
     * Determines if the given node path is a child of the given parent path.
     * 
     * @param parentPath the parent path
     * @param nodePath the path of the node
     * @return true if {@code nodePath} is a child of {@code parentPath} at any depth level,
     *         {@code false} if unrelated, sibling, or same path
     */
    public static boolean isChild(String parentPath, String nodePath) {
        checkNotNull(parentPath, "parentPath");
        checkNotNull(nodePath, "nodePath");
        return nodePath.length() > parentPath.length()
                && (parentPath.isEmpty() || nodePath.charAt(parentPath.length()) == PATH_SEPARATOR)
                && nodePath.startsWith(parentPath);
    }

    /**
     * Given {@code path == "path/to/node"} returns {@code ["path", "path/to", "path/to/node"]}
     * 
     * @param path the path to analyze
     * @return a sorted list of all paths that lead to the given path
     */
    public static List<String> allPathsTo(final String path) {
        checkNotNull(path);
        checkArgument(!path.isEmpty());

        StringBuilder sb = new StringBuilder();
        List<String> paths = Lists.newArrayList();

        final String[] steps = path.split("" + PATH_SEPARATOR);

        int i = 0;
        do {
            sb.append(steps[i]);
            paths.add(sb.toString());
            sb.append(PATH_SEPARATOR);
            i++;
        } while (i < steps.length);
        return paths;
    }

    /**
     * Splits the given tree {@code path} into its node name components
     * 
     * @param path non null, possibly empty path
     * @return a list of path steps, or an empty list if the path is empty
     */
    public static List<String> split(final String path) {
        checkNotNull(path);
        if (NodeRef.ROOT.equals(path)) {
            return new ArrayList<>();
        }
        List<String> split = Splitter.on(PATH_SEPARATOR).splitToList(path);
        return split;
    }

    /**
     * Constructs a new path by appending a child name to an existing parent path.
     * 
     * @param parentTreePath full parent path
     * @param childName name to append
     * 
     * @return a new full path made by appending {@code childName} to {@code parentTreePath}
     */
    public static String appendChild(String parentTreePath, String childName) {
        checkArgument(parentTreePath != null || ROOT.equalsIgnoreCase(childName));
        checkNotNull(childName);
        return parentTreePath == null || ROOT.equals(parentTreePath) ? childName
                : new StringBuilder(parentTreePath).append(PATH_SEPARATOR).append(childName)
                        .toString();
    }

    /**
     * Determines if the {@code Node} this object points to intersects the given {@link Envelope}
     * 
     * @param env the {@link Envelope} to check against
     * @return {@code true} if the {@code Node} intersects the {@link Envelope}
     */
    @Override
    public boolean intersects(Envelope env) {
        return node.intersects(env);
    }

    /**
     * Expands the provided {@link Envelope} to encompass the {@code Node} this object points to.
     * 
     * @param env the {@link Envelope} to expand
     */
    @Override
    public void expand(Envelope env) {
        node.expand(env);
    }

    /**
     * @return the depth of the given path, being zero if the path is the root path (i.e. the empty
     *         string) or > 0 depending on how many steps compose the path
     */
    public static int depth(String path) {
        return split(path).size();
    }

    /**
     * Remove the parent path from the given child path.
     * 
     * @param parentPath the parent path to remove
     * @param childPath the child path to remove from
     * @return the stripped child path
     */
    public static String removeParent(final String parentPath, final String childPath) {
        checkArgument(isChild(parentPath, childPath));
        List<String> parent = split(parentPath);
        List<String> child = split(childPath);
        child = child.subList(parent.size(), child.size());
        String strippedChildPath = child.get(0);
        for (int i = 1; i < child.size(); i++) {
            strippedChildPath = appendChild(strippedChildPath, child.get(i));
        }
        return strippedChildPath;
    }

    /**
     * @return and {@link Optional} containing the bounds of the {@code Node} this object points to,
     *         or {@link Optional#absent()} if the {@code Node} has no bounds
     */
    @Override
    public Optional<Envelope> bounds() {
        return node.bounds();
    }

    /**
     * Constructs a new {@code NodeRef} that points to the tree with the provided parameters.
     * 
     * @param treePath the path of the tree
     * @param id the {@link ObjectId} of the tree
     * @param metadataId the metadata id of the tree
     * @return the newly constructed {@code NodeRef}
     */
    public static NodeRef tree(String treePath, ObjectId id, ObjectId metadataId) {
        NodeRef.checkValidPath(treePath);
        checkNotNull(id);
        checkNotNull(metadataId);
        String parentPath = NodeRef.parentPath(treePath);
        String treeName = NodeRef.nodeFromPath(treePath);
        Node treeNode = RevObjectFactory.defaultInstance().createNode(treeName, id, metadataId,
                TYPE.TREE, null, null);
        return NodeRef.create(parentPath, treeNode);
    }
}
