/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

abstract class NodeId implements Comparable<NodeId> {

    protected final String name;

    protected NodeId(final String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    @Override
    public abstract String toString();

    /**
     * 
     * @param depth the tree depth for which to return the bucket index for this node
     * @return a positive integer (in the range of an unsigned byte value) or {@code -1} if this
     *         node can't be added at the specified depth
     */
    public abstract int bucket(int depth);
}