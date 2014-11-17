/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.plumbing.diff;

import static org.locationtech.geogig.api.NodeRef.ROOT;
import static org.locationtech.geogig.api.NodeRef.depth;
import static org.locationtech.geogig.api.NodeRef.split;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.repository.SpatialOps;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.vividsolutions.jts.geom.Envelope;

/**
 * A mutable data structure representing the state of a tree and its subtrees
 */
public class MutableTree implements Cloneable {

    private Node node;

    private Map<String, MutableTree> childTrees;

    public static final Ordering<NodeRef> DEEPEST_LAST_COMPARATOR = new Ordering<NodeRef>() {
        @Override
        public int compare(NodeRef o1, NodeRef o2) {

            int depth = Integer.valueOf(depth(o1.path())).compareTo(
                    Integer.valueOf(depth(o2.path())));

            if (depth != 0) {
                return depth;
            }
            return o1.path().compareTo(o2.path());
        }
    };

    public static final Ordering<NodeRef> DEEPEST_FIRST_COMPARATOR = DEEPEST_LAST_COMPARATOR
            .reverse();

    private MutableTree(String name) {
        this(Node.tree(name, RevTree.EMPTY_TREE_ID, ObjectId.NULL));
    }

    private MutableTree(Node node) {
        this.node = node;
        this.childTrees = Maps.newTreeMap();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        toString(this, sb, 0);
        return sb.toString();
    }

    private void toString(MutableTree tree, StringBuilder sb, int indent) {
        Node node = tree.getNode();
        append(sb, node, indent);

        for (MutableTree c : tree.childTrees.values()) {
            toString(c, sb, indent + 1);
        }

    }

    private void append(StringBuilder sb, Node node, int indent) {
        sb.append(Strings.repeat("    ", indent)).append(node.getName()).append("->")
                .append(node.getObjectId()).append(" (").append(node.getMetadataId()).append(")\n");
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MutableTree)) {
            return false;
        }
        MutableTree other = (MutableTree) o;
        return node.equals(other.node) && node.getMetadataId().equals(other.node.getMetadataId())
                && childTrees.equals(other.childTrees);
    }

    public static MutableTree createFromRefs(final ObjectId rootId,
            final Supplier<Iterator<NodeRef>> refs) {
        return createFromRefs(rootId, refs.get());
    }

    public static MutableTree createFromRefs(final ObjectId rootId,
            @Nullable final NodeRef... treeRefs) {
        Iterator<NodeRef> refs = Iterators.emptyIterator();
        if (treeRefs != null) {
            refs = Lists.newArrayList(treeRefs).iterator();
        }
        return createFromRefs(rootId, refs);
    }

    public static MutableTree createFromRefs(final ObjectId rootId, final Iterator<NodeRef> treeRefs) {

        Function<NodeRef, String> keyFunction = new Function<NodeRef, String>() {

            @Override
            public String apply(@Nullable NodeRef input) {
                return input.path();
            }
        };

        ImmutableMap<String, NodeRef> treesByPath = Maps.uniqueIndex(treeRefs, keyFunction);

        return createFromPaths(rootId, treesByPath);
    }

    public static MutableTree createFromPaths(final ObjectId rootId,
            final Map<String, NodeRef> entries) {

        List<NodeRef> refsByDepth = Lists.newArrayList(entries.values());
        Collections.sort(refsByDepth, DEEPEST_LAST_COMPARATOR);

        Node rootNode = Node.create(ROOT, rootId, ObjectId.NULL, TYPE.TREE, null);
        MutableTree root = new MutableTree(rootNode);

        Envelope bounds = new Envelope();

        for (NodeRef entry : refsByDepth) {
            Node node = entry.getNode();
            node.expand(bounds);
            String parentPath = entry.getParentPath();
            root.setChild(parentPath, node);
        }
        // recreate root node with the appropriate bounds
        rootNode = Node.create(ROOT, rootId, ObjectId.NULL, TYPE.TREE, bounds);
        root.setNode(rootNode);

        return root;
    }

    public Node getNode() {
        return node;
    }

    public void forceChild(final String parentPath, final Node treeNode) {
        ImmutableList<String> parentSteps = NodeRef.split(parentPath);
        MutableTree parent = this;
        for (String name : parentSteps) {
            MutableTree child = parent.childTrees.get(name);
            if (child == null) {
                child = new MutableTree(name);
                parent.childTrees.put(name, child);
            }
            parent = child;
        }

        MutableTree tree = parent.childTrees.get(treeNode.getName());
        if (tree == null) {
            tree = new MutableTree(treeNode);
            parent.childTrees.put(treeNode.getName(), tree);
        } else {
            tree.setNode(treeNode);
        }
    }

    public void setChild(String parentPath, Node node) {
        List<String> parentSteps = split(parentPath);
        setChild(parentSteps, node);
    }

    public void setChild(final List<String> parentPath, final Node node) {
        MutableTree parent;
        MutableTree child;
        if (parentPath.isEmpty()) {
            parent = this;
        } else {
            parent = getChild(parentPath);
        }

        child = parent.childTrees.get(node.getName());
        if (child == null) {
            child = new MutableTree(node);
            parent.childTrees.put(node.getName(), child);
        } else {
            child.setNode(node);
        }
    }

    public MutableTree getChild(String path) throws IllegalArgumentException {
        return getChild(NodeRef.split(path));
    }

    public MutableTree getChild(final List<String> path) throws IllegalArgumentException {
        Preconditions.checkArgument(!path.isEmpty());

        String directChildName = path.get(0);
        MutableTree child = childTrees.get(directChildName);
        if (child == null) {
            throw new IllegalArgumentException(String.format("No child named %s exists: %s",
                    directChildName, childTrees.keySet()));
        }
        if (path.size() == 1) {
            return child;
        }
        return child.getChild(path.subList(1, path.size()));
    }

    public SortedMap<String, MutableTree> getChildrenAsMap() {
        TreeMap<String, MutableTree> map = Maps.newTreeMap();
        asMap("", map);
        return map;
    }

    private void asMap(String parentPath, TreeMap<String, MutableTree> target) {
        for (MutableTree childTree : this.childTrees.values()) {
            String childTreePath = NodeRef.appendChild(parentPath, childTree.getNode().getName());
            target.put(childTreePath, childTree);
            childTree.asMap(childTreePath, target);
        }

    }

    @Nullable
    public MutableTree removeChild(String path) {
        ImmutableList<String> steps = NodeRef.split(path);
        MutableTree tree = this;

        for (Iterator<String> childNames = steps.iterator(); childNames.hasNext();) {
            String childName = childNames.next();
            MutableTree child = tree.childTrees.get(childName);
            if (child == null) {
                return null;
            }
            if (!childNames.hasNext()) {
                MutableTree removed = tree.childTrees.remove(childName);
                return removed;
            } else {
                tree = child;
            }
        }
        return null;
    }

    public void setNode(final Node newNode) {
        this.node = newNode;
    }

    public RevTree build(ObjectDatabase origin, ObjectDatabase target) {
        final ObjectId nodeId = node.getObjectId();
        final RevTree tree = origin.getTree(nodeId);

        RevTreeBuilder builder = tree.builder(target).clearSubtrees();

        for (MutableTree childTree : this.childTrees.values()) {
            String name;
            ObjectId newObjectId;
            ObjectId metadataId;
            Envelope bounds;
            {
                RevTree newChild = childTree.build(origin, target);
                target.put(newChild);
                Node oldNode = childTree.getNode();
                name = oldNode.getName();
                newObjectId = newChild.getId();
                metadataId = oldNode.getMetadataId().or(ObjectId.NULL);
                bounds = SpatialOps.boundsOf(newChild);
            }
            Node newNode = Node.create(name, newObjectId, metadataId, TYPE.TREE, bounds);
            builder.put(newNode);
        }
        RevTree newTree = builder.build();
        if (!this.node.getObjectId().equals(newTree.getId())) {
            target.put(newTree);
            Envelope bounds = SpatialOps.boundsOf(newTree);
            this.node = Node.create(node.getName(), newTree.getId(),
                    node.getMetadataId().or(ObjectId.NULL), TYPE.TREE, bounds);
        }

        return newTree;
    }

    @Override
    public MutableTree clone() {
        MutableTree clone = new MutableTree(node);
        for (MutableTree child : this.childTrees.values()) {
            clone.childTrees.put(child.getNode().getName(), child.clone());
        }
        return clone;
    }
}
