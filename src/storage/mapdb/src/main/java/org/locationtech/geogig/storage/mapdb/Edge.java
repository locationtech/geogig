/* Copyright (c) 2015 SWM Services GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Sebastian Schmidt (SWM Services GmbH) - initial implementation
 */
package org.locationtech.geogig.storage.mapdb;

import java.io.Serializable;

/**
 * Edge class used by {@link Graph}
 * 
 * @author Sebastian Schmidt, SWM Services GmbH
 */
class Edge implements Serializable {
	
	private static final long serialVersionUID = 1L;

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
