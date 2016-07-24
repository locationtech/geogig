/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.bdbje;

import static org.locationtech.geogig.storage.GraphDatabase.SPARSE_FLAG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;

import com.google.common.collect.ImmutableList;

class NodeData {
    public ObjectId id;

    public List<ObjectId> outgoing;

    public List<ObjectId> incoming;

    public Map<String, String> properties;

    @Nullable
    public ObjectId mappedTo;

    public NodeData(ObjectId id, List<ObjectId> parents) {
        this(id, ObjectId.NULL, new ArrayList<ObjectId>(parents), new ArrayList<ObjectId>(2),
                new HashMap<String, String>());
    }

    NodeData(ObjectId id, ObjectId mappedTo, List<ObjectId> parents, List<ObjectId> children,
            Map<String, String> properties) {
        this.id = id;
        this.mappedTo = mappedTo;
        this.outgoing = parents;
        this.incoming = children;
        this.properties = properties;
    }

    public NodeData(ObjectId id) {
        this(id, ImmutableList.<ObjectId> of());
    }

    public boolean isSparse() {
        return properties.containsKey(SPARSE_FLAG) ? Boolean.valueOf(properties.get(SPARSE_FLAG))
                : false;
    }
}