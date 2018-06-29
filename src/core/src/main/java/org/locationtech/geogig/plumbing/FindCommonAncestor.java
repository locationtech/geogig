/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.GraphDatabase.Direction;
import org.locationtech.geogig.storage.GraphDatabase.GraphEdge;
import org.locationtech.geogig.storage.GraphDatabase.GraphNode;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Finds the common {@link RevCommit commit} ancestor of two commits.
 */
public class FindCommonAncestor extends AbstractGeoGigOp<Optional<ObjectId>> {

    private ObjectId left;

    private ObjectId right;

    private Supplier<GraphDatabase> leftSource = () -> graphDatabase();

    private Supplier<GraphDatabase> rightSource = () -> graphDatabase();

    /**
     * @param left the left {@link ObjectId}
     */
    public FindCommonAncestor setLeftId(ObjectId left) {
        this.left = left;
        return this;
    }

    public FindCommonAncestor setLeftSource(GraphDatabase leftGraph) {
        this.leftSource = leftGraph == null ? () -> graphDatabase() : () -> leftGraph;
        return this;
    }

    public FindCommonAncestor setRightSource(GraphDatabase rightGraph) {
        this.rightSource = rightGraph == null ? () -> graphDatabase() : () -> rightGraph;
        return this;
    }

    /**
     * @param right the right {@link ObjectId}
     */
    public FindCommonAncestor setRightId(ObjectId right) {
        this.right = right;
        return this;
    }

    /**
     * @param left the left {@link RevCommit}
     */
    public FindCommonAncestor setLeft(RevCommit left) {
        this.left = left.getId();
        return this;
    }

    /**
     * @param right the right {@link RevCommit}
     */
    public FindCommonAncestor setRight(RevCommit right) {
        this.right = right.getId();
        return this;
    }

    /**
     * Finds the common {@link RevCommit commit} ancestor of two commits.
     * 
     * @return an {@link Optional} of the ancestor commit, or {@link Optional#absent()} if no common
     *         ancestor was found
     */
    @Override
    protected Optional<ObjectId> _call() {
        Preconditions.checkState(left != null, "Left commit has not been set.");
        Preconditions.checkState(right != null, "Right commit has not been set.");

        if (left.equals(right)) {
            // They are the same commit
            return Optional.of(left);
        }

        getProgressListener().started();

        Optional<ObjectId> ancestor = findLowestCommonAncestor(left, right);

        getProgressListener().complete();

        return ancestor;
    }

    /**
     * Finds the lowest common ancestor of two commits.
     * 
     * @param leftId the commit id of the left commit
     * @param rightId the commit id of the right commit
     * @return An {@link Optional} of the lowest common ancestor of the two commits, or
     *         {@link Optional#absent()} if a common ancestor could not be found.
     */
    public Optional<ObjectId> findLowestCommonAncestor(ObjectId leftId, ObjectId rightId) {
        Set<GraphNode> leftSet = new HashSet<GraphNode>();
        Set<GraphNode> rightSet = new HashSet<GraphNode>();

        Queue<GraphNode> leftQueue = new LinkedList<GraphNode>();
        Queue<GraphNode> rightQueue = new LinkedList<GraphNode>();

        GraphNode leftNode = leftSource.get().getNode(leftId);
        leftQueue.add(leftNode);

        GraphNode rightNode = rightSource.get().getNode(rightId);
        rightQueue.add(rightNode);

        List<GraphNode> potentialCommonAncestors = new LinkedList<GraphNode>();
        while (!leftQueue.isEmpty() || !rightQueue.isEmpty()) {
            if (!leftQueue.isEmpty()) {
                GraphNode commit = leftQueue.poll();
                if (processCommit(commit, leftQueue, leftSet, rightQueue, rightSet)) {
                    potentialCommonAncestors.add(commit);
                }
            }
            if (!rightQueue.isEmpty()) {
                GraphNode commit = rightQueue.poll();
                if (processCommit(commit, rightQueue, rightSet, leftQueue, leftSet)) {
                    potentialCommonAncestors.add(commit);
                }
            }
        }
        verifyAncestors(potentialCommonAncestors, leftSet, rightSet);

        Optional<ObjectId> ancestor = Optional.absent();
        if (potentialCommonAncestors.size() > 0) {
            ancestor = Optional.of(potentialCommonAncestors.get(0).getIdentifier());
        }
        return ancestor;
    }

    /**
     * Process a commit to see if it has already been seen. If it has, prevent unnecessary work from
     * continuing on the other traversal queue. If it hasn't, add it's parents to the traversal
     * queue.
     * 
     * @param commit commit to process
     * @param myQueue my traversal queue
     * @param mySet my visited nodes
     * @param theirQueue other traversal queue
     * @param theirSet other traversal's visited nodes
     * @return
     */
    private boolean processCommit(GraphNode commit, Queue<GraphNode> myQueue, Set<GraphNode> mySet,
            Queue<GraphNode> theirQueue, Set<GraphNode> theirSet) {
        if (mySet.add(commit)) {
            if (theirSet.contains(commit)) {
                stopAncestryPath(commit, theirQueue, theirSet);
                return true;
            }
            Iterator<GraphEdge> edges = commit.getEdges(Direction.OUT);
            while (edges.hasNext()) {
                GraphEdge parentEdge = edges.next();
                GraphNode parent = parentEdge.getToNode();
                myQueue.add(parent);
            }
        }
        return false;

    }

    /**
     * This function is called when a common ancestor is found and the other traversal queue should
     * stop traversing down the history of that particular commit. Any ancestors caught after this
     * one will be an older ancestor. This function follows the ancestry of the common ancestor
     * until it has been removed from the opposite traversal queue.
     * 
     * @param commit the common ancestor
     * @param theirQueue the opposite traversal queue
     * @param theirSet the opposite visited nodes
     */
    private void stopAncestryPath(GraphNode commit, Queue<GraphNode> theirQueue,
            Set<GraphNode> theirSet) {
        Queue<GraphNode> ancestorQueue = new LinkedList<GraphNode>();
        ancestorQueue.add(commit);
        Set<GraphNode> processed = new HashSet<GraphNode>();
        while (!ancestorQueue.isEmpty()) {
            GraphNode ancestor = ancestorQueue.poll();
            Iterator<GraphEdge> edges = ancestor.getEdges(Direction.OUT);
            while (edges.hasNext()) {
                GraphEdge relationship = edges.next();
                GraphNode parentNode = relationship.getToNode();
                if (theirSet.contains(parentNode)) {
                    if (!processed.contains(parentNode)) {
                        ancestorQueue.add(parentNode);
                        processed.add(parentNode);
                    }
                } else {
                    theirQueue.remove(parentNode);
                }
            }
        }
    }

    /**
     * This function is called at the end of the traversal to make sure none of our results have a
     * more recent ancestor in the result list.
     * 
     * @param potentialCommonAncestors the result list
     * @param leftSet the visited nodes of the left traversal
     * @param rightSet the visited nodes of the right traversal
     */
    private void verifyAncestors(List<GraphNode> potentialCommonAncestors, Set<GraphNode> leftSet,
            Set<GraphNode> rightSet) {
        Queue<GraphNode> ancestorQueue = new LinkedList<GraphNode>();
        List<GraphNode> falseAncestors = new LinkedList<GraphNode>();
        List<GraphNode> processed = new LinkedList<GraphNode>();

        for (GraphNode v : potentialCommonAncestors) {
            if (falseAncestors.contains(v)) {
                continue;
            }
            ancestorQueue.add(v);
            while (!ancestorQueue.isEmpty()) {
                GraphNode ancestor = ancestorQueue.poll();
                Iterator<GraphEdge> edges = ancestor.getEdges(Direction.OUT);
                while (edges.hasNext()) {
                    GraphEdge parent = edges.next();
                    GraphNode parentNode = parent.getToNode();
                    if (parentNode.getIdentifier() != ancestor.getIdentifier()) {
                        if (leftSet.contains(parentNode) || rightSet.contains(parentNode)) {
                            if (!processed.contains(parentNode)) {
                                ancestorQueue.add(parentNode);
                                processed.add(parentNode);
                            }
                            if (potentialCommonAncestors.contains(parentNode)) {
                                falseAncestors.add(parentNode);
                            }
                        }
                    }
                }
            }
        }
        potentialCommonAncestors.removeAll(falseAncestors);
    }
}
