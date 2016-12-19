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

/**
 * Edge class used by {@link Graph}
 * 
 * @author Justin Deoliveira, Boundless
 */
class Edge {

    final Node src;

    final Node dst;

    /**
     * Creates a new edge between two nodes.
     */
    Edge(Node src, Node dst) {
        this.src = src;
        this.dst = dst;
    }
}
