/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.io.Closeable;
import java.util.Iterator;

import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;

/**
 * Represents a temporary storage of {@link Node} instances to assist {@link RevTreeBuilder2} in
 * creating large {@link RevTree} instances by first building an index of Nodes and then adding all
 * nodes to the {@link RevTreeBuilder} in {@link CanonicalNodeNameOrder}'s order.
 */
interface NodeIndex extends Closeable {

    /**
     * Adds a tree node to the index.
     */
    public abstract void add(Node node);

    /**
     * @return the list of added nodes sorted according to the {@link CanonicalNodeOrder} comparator.
     */
    public abstract Iterator<Node> nodes();

    /**
     * Closes and releases any resource used by this index. This method is idempotent.
     */
    @Override
    public abstract void close();
}