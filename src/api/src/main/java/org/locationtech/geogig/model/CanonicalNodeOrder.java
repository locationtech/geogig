/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

/**
 * Implements storage order of {@link Node} based on its name using a {@link CanonicalNodeNameOrder}
 * comparator.
 * 
 * @see CanonicalNodeNameOrder
 * 
 * @since 1.0
 */
public final class CanonicalNodeOrder extends NodeOrdering {

    private static final long serialVersionUID = -2860468212633430368L;

    public static final CanonicalNodeOrder INSTANCE = new CanonicalNodeOrder();

    @Override
    public int compare(Node nr1, Node nr2) {
        return CanonicalNodeNameOrder.INSTANCE.compare(nr1.getName(), nr2.getName());
    }

    /**
     * @see CanonicalNodeNameOrder#bucket(String, int)
     */
    public int bucket(final Node ref, final int depth) {
        return CanonicalNodeNameOrder.bucket(ref.getName(), depth);
    }
}