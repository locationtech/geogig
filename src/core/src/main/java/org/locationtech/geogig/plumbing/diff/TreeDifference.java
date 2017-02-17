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

import static com.google.common.collect.Maps.difference;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Maps.newTreeMap;

import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;

import com.google.common.base.Optional;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedMapDifference;

/**
 * Finds the differences between two trees given by two {@link MutableTree}
 */
public class TreeDifference {

    private MutableTree leftTree;

    private MutableTree rightTree;

    public TreeDifference(MutableTree leftTree, MutableTree rightTree) {
        this.leftTree = leftTree;
        this.rightTree = rightTree;
    }

    public static TreeDifference create(MutableTree leftTree, MutableTree rightTree) {
        return new TreeDifference(leftTree, rightTree);
    }

    public MutableTree getLeftTree() {
        return leftTree;
    }

    public MutableTree getRightTree() {
        return rightTree;
    }

    public TreeDifference inverse() {
        return new TreeDifference(rightTree, leftTree);
    }

    /**
     * Finds node references that represent a renamed tree.
     * <p>
     * A renamed tree is identified when a ref in the right points to the same tree than a ref in
     * the left, with a different name, and no other ref in the right has the same name than the one
     * in the left.
     * 
     * @return
     */
    public SortedMap<NodeRef, NodeRef> findRenames() {

        SortedMap<String, MutableTree> leftEntries = leftTree.getChildrenAsMap();
        SortedMap<String, MutableTree> rightEntries = rightTree.getChildrenAsMap();

        SortedMapDifference<String, MutableTree> difference;
        difference = difference(leftEntries, rightEntries);

        return findRenames(difference);
    }

    private SortedMap<NodeRef, NodeRef> findRenames(
            SortedMapDifference<String, MutableTree> difference) {

        SortedMap<String, MutableTree> entriesOnlyOnLeft = difference.entriesOnlyOnLeft();
        SortedMap<String, MutableTree> entriesOnlyOnRight = difference.entriesOnlyOnRight();

        SortedMap<NodeRef, NodeRef> matches = newTreeMap();

        for (Map.Entry<String, MutableTree> right : entriesOnlyOnRight.entrySet()) {
            for (Map.Entry<String, MutableTree> left : entriesOnlyOnLeft.entrySet()) {

                Node leftNode = left.getValue().getNode();
                Node rightNode = right.getValue().getNode();

                if (rightNode.getObjectId().equals(leftNode.getObjectId())) {
                    String leftParent = NodeRef.parentPath(left.getKey());
                    String rightParent = NodeRef.parentPath(right.getKey());

                    NodeRef leftRef = new NodeRef(leftNode, leftParent, ObjectId.NULL);
                    NodeRef rightRef = new NodeRef(rightNode, rightParent, ObjectId.NULL);
                    matches.put(leftRef, rightRef);
                }
            }
        }
        return matches;
    }

    /**
     * Finds child refs that exist on the right root tree, don't exist on the left root tree, and
     * are not renames.
     * 
     * @return
     */
    public SortedSet<NodeRef> findNewTrees() {

        SortedMap<String, MutableTree> leftEntries = leftTree.getChildrenAsMap();
        SortedMap<String, MutableTree> rightEntries = rightTree.getChildrenAsMap();

        SortedMapDifference<String, MutableTree> difference;
        difference = difference(leftEntries, rightEntries);

        Map<String, MutableTree> entriesOnlyOnRight;
        entriesOnlyOnRight = newHashMap(difference.entriesOnlyOnRight());

        // ignore renames
        Map<NodeRef, NodeRef> pureRenames = findRenames(difference);
        for (NodeRef renamedTo : pureRenames.values()) {
            entriesOnlyOnRight.remove(renamedTo.path());
        }

        SortedSet<NodeRef> newTreeRefs = Sets.newTreeSet();
        for (Map.Entry<String, MutableTree> newTree : entriesOnlyOnRight.entrySet()) {
            Node node = newTree.getValue().getNode();
            String parentPath = NodeRef.parentPath(newTree.getKey());
            // pass NULL to the NodeRef metadataId, to it defers to the one in the Node in case it
            // has one (see NodeRef.getMetadataId())
            ObjectId metadataId = ObjectId.NULL;
            NodeRef ref = new NodeRef(node, parentPath, metadataId);
            newTreeRefs.add(ref);
        }
        return newTreeRefs;
    }

    /**
     * Finds child refs that exist on the left root tree, don't exist in the right root tree, and
     * are not renames.
     * 
     * @return
     */
    public SortedSet<NodeRef> findDeletes() {
        return inverse().findNewTrees();
    }

    /**
     * Finds child refs that are named the same, point to different trees, but are not pure metadata
     * changes
     * 
     * @return a sorted map of old/new references to a trees that have changed, deepest paths first
     */
    public SortedMap<NodeRef, NodeRef> findChanges() {

        SortedMap<String, MutableTree> leftEntries = leftTree.getChildrenAsMap();
        SortedMap<String, MutableTree> rightEntries = rightTree.getChildrenAsMap();

        final Map<NodeRef, NodeRef> pureMetadataChanges = findPureMetadataChanges();

        SortedMapDifference<String, MutableTree> difference;
        difference = difference(leftEntries, rightEntries);

        SortedMap<String, ValueDifference<MutableTree>> entriesDiffering;
        entriesDiffering = difference.entriesDiffering();

        SortedMap<NodeRef, NodeRef> matches = Maps.newTreeMap(MutableTree.DEEPEST_FIRST_COMPARATOR);

        for (Map.Entry<String, ValueDifference<MutableTree>> e : entriesDiffering.entrySet()) {
            String nodePath = e.getKey();
            String parentPath = NodeRef.parentPath(nodePath);
            ValueDifference<MutableTree> vd = e.getValue();
            MutableTree left = vd.leftValue();
            MutableTree right = vd.rightValue();
            NodeRef lref = new NodeRef(left.getNode(), parentPath, ObjectId.NULL);
            NodeRef rref = new NodeRef(right.getNode(), parentPath, ObjectId.NULL);
            if (!pureMetadataChanges.containsKey(lref)) {
                matches.put(lref, rref);
            }
        }
        return matches;
    }

    /**
     * Finds tree pointers that point to the same tree (path and object id) on the left and right
     * sides of the comparison but have different {@link NodeRef#getMetadataId() metadata ids}
     */
    public Map<NodeRef, NodeRef> findPureMetadataChanges() {
        SortedMap<String, MutableTree> leftEntries = leftTree.getChildrenAsMap();
        SortedMap<String, MutableTree> rightEntries = rightTree.getChildrenAsMap();

        Map<NodeRef, NodeRef> matches = Maps.newTreeMap();

        for (Map.Entry<String, MutableTree> e : leftEntries.entrySet()) {
            final String nodePath = e.getKey();

            final MutableTree leftTree = e.getValue();
            final Node leftNode = leftTree.getNode();

            @Nullable
            final MutableTree rightTree = rightEntries.get(nodePath);
            final Node rightNode = rightTree == null ? null : rightTree.getNode();

            if (leftNode.equals(rightNode)) {
                final Optional<ObjectId> leftMetadata = leftNode.getMetadataId();
                final Optional<ObjectId> rightMetadata = rightNode.getMetadataId();
                if (!leftMetadata.equals(rightMetadata)) {
                    String parentPath = NodeRef.parentPath(nodePath);
                    NodeRef leftRef = new NodeRef(leftNode, parentPath, ObjectId.NULL);
                    NodeRef rightRef = new NodeRef(rightNode, parentPath, ObjectId.NULL);
                    matches.put(leftRef, rightRef);
                }
            }
        }
        return matches;
    }

    public boolean areEqual() {
        return leftTree.equals(rightTree);
    }

}
