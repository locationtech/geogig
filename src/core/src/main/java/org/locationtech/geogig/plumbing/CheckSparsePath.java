/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.Iterator;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.GraphDatabase.Direction;
import org.locationtech.geogig.storage.GraphDatabase.GraphEdge;
import org.locationtech.geogig.storage.GraphDatabase.GraphNode;

import com.google.common.base.Preconditions;

/**
 * Determines if there are any sparse commits between the start commit and the end commit, not
 * including the end commit.
 */
public class CheckSparsePath extends AbstractGeoGigOp<Boolean> {

    private ObjectId start;

    private ObjectId end;

    /**
     * @param start the start {@link ObjectId}
     */
    public CheckSparsePath setStart(ObjectId start) {
        this.start = start;
        return this;
    }

    /**
     * @param end the end {@link ObjectId}
     */
    public CheckSparsePath setEnd(ObjectId end) {
        this.end = end;
        return this;
    }

    /**
     * Determines if there are any sparse commits between the start commit and the end commit, not
     * including the end commit.
     * 
     * @return true if there are any sparse commits between start and end
     */
    @Override
    protected Boolean _call() {
        Preconditions.checkState(start != null, "start commit has not been set.");
        Preconditions.checkState(end != null, "end commit has not been set.");

        GraphNode node = graphDatabase().getNode(start);
        return isSparsePath(node, end, false);
    }

    /**
     * Recursive brute force function to exhaustively search for all paths between the start and end
     * node. This should be optimized to avoid unnecessary work in the future.
     */
    private boolean isSparsePath(GraphNode node, ObjectId end, boolean sparse) {
        if (node.getIdentifier().equals(end)) {
            return sparse;
        }
        Iterator<GraphEdge> outgoing = node.getEdges(Direction.OUT);
        if (outgoing.hasNext()) {
            boolean node_sparse = node.isSparse();
            boolean combined_sparse = false;
            while (outgoing.hasNext()) {
                GraphEdge parent = outgoing.next();
                GraphNode parentNode = parent.getToNode();
                combined_sparse = combined_sparse
                        || isSparsePath(parentNode, end, sparse || node_sparse);
            }
            return combined_sparse;
        }
        return false;
    }
}
