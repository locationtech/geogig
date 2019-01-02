/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import static org.locationtech.geogig.model.NodeRef.ROOT;
import static org.locationtech.geogig.model.NodeRef.depth;
import static org.locationtech.geogig.model.NodeRef.split;
import static org.locationtech.geogig.model.RevTree.EMPTY;
import static org.locationtech.geogig.model.RevTree.EMPTY_TREE_ID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;

/**
 * A mutable data structure representing the state of a tree and its subtrees
 */
public class MutableTree implements Cloneable {

    private Node node;

    private Map<String, MutableTree> childTrees;

    public static final Ordering<NodeRef> DEEPEST_LAST_COMPARATOR = new Ordering<NodeRef>() {
        @Override
        public int compare(NodeRef o1, NodeRef o2) {

            int depth = Integer.valueOf(depth(o1.path()))
                    .compareTo(Integer.valueOf(depth(o2.path())));

            if (depth != 0) {
                return depth;
            }
            return o1.path().compareTo(o2.path());
        }
    };

    public static final Ordering<NodeRef> DEEPEST_FIRST_COMPARATOR = DEEPEST_LAST_COMPARATOR
            .reverse();

    private MutableTree(String name) {
        this(RevObjectFactory.defaultInstance().createNode(name, RevTree.EMPTY_TREE_ID,
                ObjectId.NULL, TYPE.TREE, null, null));
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
        Iterator<NodeRef> refs = Collections.emptyIterator();
        if (treeRefs != null) {
            refs = Lists.newArrayList(treeRefs).iterator();
        }
        return createFromRefs(rootId, refs);
    }

    public static MutableTree createFromRefs(final ObjectId rootId,
            final Iterator<NodeRef> treeRefs) {

        // NodeRef::path, but friendly for Fortify
        Function<NodeRef, String> fn_path = new Function<NodeRef, String>() {
            @Override
            public String apply(NodeRef noderef) {
                return noderef.path();
            }
        };

        ImmutableMap<String, NodeRef> treesByPath = Maps.uniqueIndex(treeRefs, fn_path);

        return createFromPaths(rootId, treesByPath);
    }

    public static MutableTree createFromPaths(final ObjectId rootId,
            final Map<String, NodeRef> entries) {

        List<NodeRef> refsByDepth = Lists.newArrayList(entries.values());
        Collections.sort(refsByDepth, DEEPEST_LAST_COMPARATOR);

        Node rootNode = RevObjectFactory.defaultInstance().createNode(ROOT, rootId, ObjectId.NULL,
                TYPE.TREE, null, null);
        MutableTree root = new MutableTree(rootNode);

        Envelope bounds = new Envelope();

        for (NodeRef entry : refsByDepth) {
            Node node = entry.getNode();
            node.expand(bounds);
            String parentPath = entry.getParentPath();
            root.setChild(parentPath, node);
        }
        // recreate root node with the appropriate bounds
        rootNode = RevObjectFactory.defaultInstance().createNode(ROOT, rootId, ObjectId.NULL,
                TYPE.TREE, bounds, null);
        root.setNode(rootNode);

        return root;
    }

    public Node getNode() {
        return node;
    }

    public void forceChild(final String parentPath, final Node treeNode) {
        List<String> parentSteps = NodeRef.split(parentPath);
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
        NodeRef.checkValidPath(path);

        final List<String> querySteps = NodeRef.split(path);
        List<String> visited = new ArrayList<>(querySteps.size());

        MutableTree tree = this;
        MutableTree removed = null;
        for (Iterator<String> childNames = querySteps.iterator(); childNames.hasNext();) {
            String childName = childNames.next();
            visited.add(childName);

            MutableTree child = tree.childTrees.get(childName);
            if (child == null) {
                break;
            }
            if (querySteps.equals(visited)) {
                removed = tree.childTrees.remove(childName);
                break;
            } else {
                tree = child;
            }
        }
        return removed;
    }

    public void setNode(final Node newNode) {
        this.node = newNode;
    }

    public RevTree build(ObjectStore store) {
        final ObjectId treeId = this.node.getObjectId();
        final RevTree original = EMPTY_TREE_ID.equals(treeId) ? EMPTY : store.getTree(treeId);

        RevTreeBuilder builder = RevTreeBuilder.builder(store, original);
        ImmutableList<Node> currentTrees = original.trees();
        currentTrees.forEach(builder::remove);

        for (MutableTree childTree : this.childTrees.values()) {
            childTree.build(store);
            Node newNode = childTree.node;
            builder.put(newNode);
        }

        final Node oldNode = this.node;
        RevTree newTree = builder.build();
        Envelope newBounds = SpatialOps.boundsOf(newTree);
        Node newNode = oldNode.update(newTree.getId(), newBounds);
        this.node = newNode;
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
