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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.google.common.collect.Iterables;

/**
 * Walks a path from the specified node to a root, bifurcating along the way in cases where a node
 * has multiple parents.
 *
 * @author Justin Deoliveira, Boundless
 *
 */
public class PathToRootWalker implements Iterator<List<Node>> {

    /**
     * node queue
     */
    Queue<Node> q;

    /**
     * visited nodes
     */
    Set<Node> seen;

    public PathToRootWalker(Node start) {
        q = new LinkedList<>();
        q.add(start);

        seen = new HashSet<>();
    }

    public @Override boolean hasNext() {
        return !q.isEmpty();
    }

    public @Override List<Node> next() {
        List<Node> curr = new ArrayList<>();
        List<Node> next = new ArrayList<>();

        while (!q.isEmpty()) {
            Node node = q.poll();
            curr.add(node);

            Iterables.addAll(next, node.to());
        }

        seen.addAll(curr);
        q.addAll(next);
        return curr;
    }

    public boolean seen(Node node) {
        return seen.contains(node);
    }

    public @Override void remove() {
        throw new UnsupportedOperationException();
    }
}
