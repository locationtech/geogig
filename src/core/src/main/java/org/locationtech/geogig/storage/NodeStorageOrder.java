/* Copyright (c) 2012-2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.io.Serializable;

import org.locationtech.geogig.api.Node;

import com.google.common.collect.Ordering;

/**
 * Implements storage order of {@link Node} based on its name using a {@link NodePathStorageOrder}
 * comparator.
 * 
 * @see NodePathStorageOrder
 */
public final class NodeStorageOrder extends Ordering<Node> implements Serializable {

    private static final long serialVersionUID = -2860468212633430368L;

    private final NodePathStorageOrder nameOrder = new NodePathStorageOrder();

    @Override
    public int compare(Node nr1, Node nr2) {
        return nameOrder.compare(nr1.getName(), nr2.getName());
    }

    /**
     * @see NodePathStorageOrder#bucket(String, int)
     */
    public Integer bucket(final Node ref, final int depth) {
        return nameOrder.bucket(ref.getName(), depth);
    }
}