/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Justin Deoliveira (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;

import com.google.common.collect.Maps;

/**
 * Walks a shortest path between two nodes applying Dijkstra's algorithm.
 * 
 * @author Justin Deoliveira, Boundless
 */
public class ShortestPathWalker implements Iterator<Node> {

    final Node start;

    final Node end;

    final Map<Node, CostNode> nodes;

    final PriorityQueue<CostNode> q;

    ShortestPathWalker(Node start, Node end) {
        this.start = start;
        this.end = end;

        nodes = Maps.newHashMap();
        q = new PriorityQueue<CostNode>(100, new Comparator<CostNode>() {
            @Override
            public int compare(CostNode o1, CostNode o2) {
                return o1.cost.compareTo(o2.cost);
            }
        });
        q.offer(newNode(start, 0d));
    }

    CostNode newNode(Node n, Double cost) {
        CostNode node = new CostNode(n, cost);
        nodes.put(n, node);
        return node;
    }

    @Override
    public boolean hasNext() {
        return !q.isEmpty();
    }

    @Override
    public Node next() {
        // grab next node
        CostNode n = q.poll();

        // update the adjacent nodes
        for (Node adj : n.node.to()) {
            CostNode m = nodes.get(adj);
            Double cost = n.cost + 1;

            if (m == null) {
                m = newNode(adj, cost);
                q.offer(m);
            } else {
                if (cost < m.cost) {
                    // update the node
                    m.cost = cost;
                    q.remove(m);
                    q.offer(m);
                }
            }
        }

        return n.node;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    static class CostNode {
        Node node;

        Double cost = Double.MAX_VALUE;

        CostNode(Node node, Double cost) {
            this.node = node;
            this.cost = cost;
        }
    }
}
