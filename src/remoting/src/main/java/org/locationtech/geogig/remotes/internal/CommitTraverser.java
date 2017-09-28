/* Copyright (c) 2013-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remotes.internal;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;

import org.locationtech.geogig.model.ObjectId;

import com.google.common.collect.ImmutableList;

/**
 * Provides a method of traversing the commit graph with overridable functions to determine when to
 * prune the traversal, and when to process a commit node.
 */
public abstract class CommitTraverser {

    private Queue<CommitNode> commitQueue;

    public Stack<ObjectId> commits;

    public List<ObjectId> have;

    private Hashtable<ObjectId, ImmutableList<ObjectId>> commitParents;

    /**
     * Traversal node that stores information about the ObjectId of the commit and it's depth from
     * the starting node.
     */
    protected class CommitNode {
        ObjectId objectId;

        int depth;

        public CommitNode(ObjectId objectId, int depth) {
            this.objectId = objectId;
            this.depth = depth;
        }

        public ObjectId getObjectId() {
            return objectId;
        }

        public int getDepth() {
            return depth;
        }

        /**
         * Use the hash code of the ObjectId.
         */
        @Override
        public int hashCode() {
            return objectId.hashCode();
        }

        /**
         * Ignore depth when comparing two commit nodes.
         * 
         * @return true if the objects are equal
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == null)
                return false;
            if (obj == this)
                return true;
            if (!(obj instanceof CommitNode))
                return false;

            CommitNode rhs = (CommitNode) obj;

            return rhs.objectId.equals(objectId);
        }
    }

    /**
     * Evaluation types to control the traversal.
     * <p>
     * INCLUDE - process the commit (apply)
     * <p>
     * EXCLUDE - do not process the commit
     * <p>
     * CONTINUE - continue traversal past this commit
     * <p>
     * PRUNE - do not traverse past this commit
     */
    protected enum Evaluation {
        INCLUDE_AND_PRUNE, INCLUDE_AND_CONTINUE, EXCLUDE_AND_PRUNE, EXCLUDE_AND_CONTINUE
    };

    /**
     * Constructs a new {@code CommitTraverser}.
     */
    public CommitTraverser() {
        commits = new Stack<ObjectId>();
        have = new LinkedList<ObjectId>();
        commitParents = new Hashtable<ObjectId, ImmutableList<ObjectId>>();
    }

    /**
     * Evaluate the commit node to determine if it should be applied, and if the traversal should
     * continue through this commit's parents.
     * 
     * @param commitNode the commit to evaluate
     * @return the {@link Evaluation} of the node
     */
    protected abstract Evaluation evaluate(CommitNode commitNode);

    /**
     * Process the accepted commit node.
     * 
     * @param commitNode the commit to apply
     */
    protected void apply(CommitNode commitNode, ImmutableList<ObjectId> parents) {
        if (commits.contains(commitNode.getObjectId())) {
            commits.remove(commitNode.getObjectId());
        }

        commits.add(commitNode.getObjectId());
    }

    /**
     * Traverse the commit graph from the given starting point.
     * 
     * @param startPoint the commit to start traversing from.
     */
    public final void traverse(ObjectId startPoint) {
        this.commitQueue = new LinkedList<CommitNode>();
        commitQueue.add(new CommitNode(startPoint, 1));
        while (!commitQueue.isEmpty()) {
            CommitNode node = commitQueue.remove();
            Evaluation evaluation = evaluate(node);
            ImmutableList<ObjectId> parents;
            switch (evaluation) {
            case INCLUDE_AND_PRUNE:
                parents = getParents(node.getObjectId());
                apply(node, parents);
                break;
            case INCLUDE_AND_CONTINUE:
                parents = getParents(node.getObjectId());
                apply(node, parents);
                addParents(node, parents);
                break;
            case EXCLUDE_AND_PRUNE:
                if (existsInDestination(node.getObjectId()) && !have.contains(node.getObjectId())) {
                    have.add(node.getObjectId());
                }
                break;
            case EXCLUDE_AND_CONTINUE:
                parents = getParents(node.getObjectId());
                addParents(node, parents);
                if (existsInDestination(node.getObjectId()) && !have.contains(node.getObjectId())) {
                    have.add(node.getObjectId());
                }
                break;
            }
        }
        commitParents.clear();
    }

    /**
     * Add the given commit's parents to the traversal queue.
     * 
     * @param commitNode the commit whose parents need to be added
     */
    private void addParents(CommitNode commitNode, ImmutableList<ObjectId> parents) {
        for (ObjectId parent : parents) {
            CommitNode parentNode = new CommitNode(parent, commitNode.getDepth() + 1);
            if (commitQueue.contains(parentNode)) {
                commitQueue.remove(parentNode);
            }
            commitQueue.add(parentNode);
        }
    }

    private ImmutableList<ObjectId> getParents(ObjectId commitId) {
        ImmutableList<ObjectId> parents = commitParents.get(commitId);
        if (parents == null) {
            parents = getParentsInternal(commitId);
            commitParents.put(commitId, parents);
        }
        return parents;
    }

    /**
     * Gets the parents of the provided commit.
     * 
     * @param commitId the id of the commit whose parents need to be retrieved
     * @return the list of parents
     */
    protected abstract ImmutableList<ObjectId> getParentsInternal(ObjectId commitId);

    /**
     * Determines if the given commitId exists in the destination.
     * 
     * @param commitId the id of the commit to find
     * @return true if the commit exists in the destination
     */
    protected abstract boolean existsInDestination(ObjectId commitId);

}
