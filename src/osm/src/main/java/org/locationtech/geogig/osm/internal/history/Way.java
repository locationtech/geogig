/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.osm.internal.history;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 *
 */
public class Way extends Primitive {

    private List<Long> nodes;

    public Way() {
        super();
        this.nodes = Lists.newLinkedList();
    }

    /**
     * @param nodeRef
     */
    void addNode(long nodeRef) {
        nodes.add(Long.valueOf(nodeRef));
    }

    public ImmutableList<Long> getNodes() {
        return ImmutableList.copyOf(nodes);
    }

    @Override
    public String toString() {
        return new StringBuilder(super.toString()).append(",nodes:").append(nodes).append(']')
                .toString();
    }

}
